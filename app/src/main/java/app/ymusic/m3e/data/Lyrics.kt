package app.ymusic.m3e.data

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/** Строка текста с временем начала (мс от начала трека). */
data class LyricLine(val timeMs: Long, val text: String)

/** Текст из Яндекс Музыки: если [synced] не пуст — текст с таймкодами, иначе только [plain]. */
data class YandexLyrics(val synced: List<LyricLine>, val plain: String?) {
    val isSynced: Boolean get() = synced.isNotEmpty()
}

/** Текст из одного источника: с таймкодами ([synced]) и/или обычный ([plain]). */
data class LyricsSource(
    val id: String,
    val name: String,
    val synced: List<LyricLine> = emptyList(),
    val plain: String? = null,
) {
    val isSynced: Boolean get() = synced.isNotEmpty()
    val hasText: Boolean get() = isSynced || !plain.isNullOrBlank()
}

/** Источники текстов для настроек: id, название, пояснение. Порядок — порядок предпочтения. */
val LyricsSourceOptions: List<Triple<String, String, String>> = listOf(
    Triple("y", "Яндекс / VK Музыка", "Родной текст трека из сервиса аккаунта, часто с таймкодами"),
    Triple("lrclib", "LRCLIB", "Открытая база, чаще всего с таймкодами"),
    Triple("ne", "NetEase", "Китайская база, много текстов с таймкодами"),
    Triple("kg", "Kugou", "Китайская база, тексты с таймкодами"),
    Triple("qq", "QQ Music", "Китайская база, тексты с таймкодами"),
    Triple("g", "Genius", "Только обычный текст, без таймкодов"),
    Triple("ovh", "lyrics.ovh", "Запасной источник, обычный текст"),
)

/**
 * Вариант из ручного поиска текста: исполнитель, название, источник и есть ли таймкоды ([synced]).
 * Сам текст подгружается через [load] (для большинства источников он уже получен при поиске и лежит в кэше запроса).
 */
class LyricsCandidate(
    val sourceId: String,
    val sourceName: String,
    val artist: String,
    val title: String,
    val durationMs: Long,
    val synced: Boolean,
    val preview: String?,
    val load: suspend () -> LyricsSource?,
) {
    val key: String get() = "$sourceId|$artist|$title|$durationMs"
}

/** Поиск вариантов текста в одном источнике по строке запроса. */
class LyricsSearcher(val id: String, val name: String, val search: suspend (String) -> List<LyricsCandidate>)

/** Один источник текстов: Яндекс, LRCLIB, NetEase, Kugou, QQ Music, Genius, lyrics.ovh. */
class LyricsProvider(val id: String, val name: String, val fetch: suspend (Track) -> LyricsSource?)

/**
 * Тексты песен.
 *  - Genius: официального API текстов нет, поэтому ищем песню через поиск сайта и берём текст со страницы.
 *    Таймкодов у Genius нет — это только обычный текст.
 *  - Яндекс Музыка: текст с таймкодами (LRC), по нему строки подсвечиваются в такт песне.
 *  - LRCLIB, NetEase, Kugou и QQ Music: открытые базы текстов, у большинства песен есть таймкоды; нужны, когда в Яндексе и на Genius текста нет.
 *  - lyrics.ovh: запасной источник с обычным текстом.
 * Найденное кэшируется на устройстве, чтобы тексты открывались и без сети.
 */
class LyricsRepository(
    private val ctx: Context,
    private val http: OkHttpClient,
    private val api: MusicApi,
) {
    private val dir: File by lazy { File(ctx.cacheDir, "lyrics").apply { mkdirs() } }
    private val browserUa =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun key(t: Track): String =
        MessageDigest.getInstance("MD5").digest(t.id.toByteArray()).joinToString("") { "%02x".format(it) }

    // ---------- Яндекс ----------

    suspend fun yandex(t: Track): YandexLyrics? = withContext(Dispatchers.IO) {
        val f = File(dir, "y_${key(t)}.json")
        readYandexCache(f)?.let { return@withContext it }
        if (t.isLocal) return@withContext null
        val id = t.id.substringBefore(':')
        val lrc = runCatching { api.trackLyrics(id, synced = true) }.getOrNull()
        val synced = lrc?.let { parseLrc(it) }.orEmpty()
        val result = when {
            synced.isNotEmpty() -> YandexLyrics(synced, null)
            else -> {
                val plain = runCatching { api.trackLyrics(id, synced = false) }.getOrNull()
                    ?: lrc?.takeIf { !it.contains("[0") }   // ответ мог прийти обычным текстом
                plain?.trim()?.takeIf { it.isNotBlank() }?.let { YandexLyrics(emptyList(), it) }
            }
        }
        if (result != null) runCatching { writeYandexCache(f, result) }
        result
    }

    private fun readYandexCache(f: File): YandexLyrics? = if (!f.exists()) null else runCatching {
        val o = JSONObject(f.readText())
        val arr = o.optJSONArray("lines") ?: JSONArray()
        YandexLyrics(
            synced = List(arr.length()) { i -> arr.getJSONArray(i).let { LyricLine(it.getLong(0), it.getString(1)) } },
            plain = o.optString("plain").ifBlank { null },
        )
    }.getOrNull()

    private fun writeYandexCache(f: File, l: YandexLyrics) {
        val arr = JSONArray().also { a -> l.synced.forEach { a.put(JSONArray().put(it.timeMs).put(it.text)) } }
        f.writeText(JSONObject().put("lines", arr).put("plain", l.plain ?: "").toString())
    }

    /** Разбор LRC: «[мм:сс.хх] текст», допускаются несколько меток на строке; служебные теги ([ar:], [ti:]) пропускаются. */
    private fun parseLrc(text: String): List<LyricLine> {
        val tag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val out = ArrayList<LyricLine>()
        for (raw in text.lineSequence()) {
            val marks = tag.findAll(raw).toList()
            if (marks.isEmpty()) continue
            val body = raw.substring(marks.last().range.last + 1).trim()
            for (m in marks) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LyricLine((min * 60 + sec) * 1000 + ms, body)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    // ---------- Genius ----------

    suspend fun genius(t: Track): String? = withContext(Dispatchers.IO) {
        val f = File(dir, "g2_${key(t)}.txt")
        if (f.exists()) return@withContext runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        for (url in geniusUrls(t)) {
            val html = runCatching { get(url, geniusHeaders) }.getOrNull() ?: continue
            val text = extractGeniusLyrics(html) ?: continue
            runCatching { f.writeText(text) }
            return@withContext text
        }
        null
    }

    private val geniusHeaders = mapOf(
        "Accept" to "application/json, text/html;q=0.9, */*;q=0.8",
        "Referer" to "https://genius.com/",
        "X-Requested-With" to "XMLHttpRequest",
    )

    private val fast: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val b = Request.Builder().url(url)
            .header("User-Agent", browserUa)
            .header("Accept-Language", "ru,en;q=0.8")
        headers.forEach { (k, v) -> b.header(k, v) }
        fast.newCall(b.build()).execute().use { r -> return if (r.isSuccessful) r.body?.string() else null }
    }

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("""[(\[].*?[)\]]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), "")

    /** Результаты поиска Genius из любого из ответов (search/multi, search/song, search). */
    private fun geniusResults(body: String): List<JSONObject> {
        val resp = JSONObject(body).optJSONObject("response") ?: return emptyList()
        val out = ArrayList<JSONObject>()
        fun addHits(hits: JSONArray?) {
            if (hits == null) return
            for (h in 0 until hits.length()) {
                val hit = hits.optJSONObject(h) ?: continue
                val type = hit.optString("type")
                if (type.isNotEmpty() && type != "song") continue
                hit.optJSONObject("result")?.takeIf { it.optString("url").startsWith("http") }?.let(out::add)
            }
        }
        resp.optJSONArray("sections")?.let { secs ->
            for (i in 0 until secs.length()) addHits(secs.optJSONObject(i)?.optJSONArray("hits"))
        }
        addHits(resp.optJSONArray("hits"))
        return out
    }

    /** Имена исполнителей записи Genius: основной, из artist_names и «feat.». */
    private fun geniusArtistNames(r: JSONObject): List<String> {
        val out = ArrayList<String>()
        r.optJSONObject("primary_artist")?.optString("name")?.let(out::add)
        r.optString("artist_names").takeIf { it.isNotBlank() }?.split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bи\b)\s*""", RegexOption.IGNORE_CASE))?.let(out::addAll)
        r.optJSONArray("featured_artists")?.let { fa -> for (i in 0 until fa.length()) fa.optJSONObject(i)?.optString("name")?.let(out::add) }
        return out.filter { it.isNotBlank() }
    }

    /** Адреса страниц Genius по убыванию уверенности: из поиска (несколько способов), затем прямой адрес по шаблону. */
    private fun geniusUrls(t: Track): List<String> {
        val urls = LinkedHashSet<String>()
        val wantTitle = norm(cleanTitle(t))
        val artist = firstArtist(t)
        val queries = listOf("$artist ${searchTitle(t)}", searchTitle(t)).distinct()
        outer@ for (q in queries) {
            val encQ = enc(q)
            for (endpoint in listOf("search/multi?per_page=10&q=", "search/song?per_page=10&q=", "search?q=")) {
                val body = runCatching { get("https://genius.com/api/$endpoint$encQ", geniusHeaders) }.getOrNull() ?: continue
                val results = runCatching { geniusResults(body) }.getOrNull().orEmpty()
                    .filter { sameVersion(t, it.optString("title")) }
                if (results.isEmpty()) continue
                fun title(r: JSONObject) = norm(r.optString("title"))
                fun artistOk(r: JSONObject) = geniusArtistNames(r).any { artistMatches(t, it) }
                fun add(r: JSONObject) { urls += r.optString("url") }
                // Сначала точное название и исполнитель, затем «содержит» и исполнитель, затем точное название без сверки исполнителя.
                results.filter { title(it) == wantTitle && artistOk(it) }.forEach(::add)
                results.filter { artistOk(it) && title(it).isNotEmpty() && (title(it).contains(wantTitle) || wantTitle.contains(title(it))) }.forEach(::add)
                results.filter { title(it) == wantTitle }.forEach(::add)
                if (urls.isNotEmpty()) break@outer
            }
        }
        // Запасной путь, если поиск Genius недоступен: адрес вида genius.com/Artist-title-lyrics (только для латиницы и оригиналов).
        if (urls.isEmpty() && versionTags(t).isEmpty()) {
            fun slug(s: String) = s.replace("&", " and ").replace("'", "").replace("’", "")
                .replace(Regex("""[^A-Za-z0-9]+"""), "-").trim('-')
            val a = slug(artist); val ti = slug(cleanTitle(t))
            if (a.isNotEmpty() && ti.isNotEmpty() && artist.all { it.code < 128 } && cleanTitle(t).all { it.code < 128 }) {
                urls += "https://genius.com/${a.replaceFirstChar { it.uppercase() }}-$ti-lyrics"
            }
        }
        return urls.take(3)
    }

    /** Внутренность `<div>`, открывающийся в позиции [open], и позиция сразу после его закрытия. */
    private fun balancedDiv(html: String, open: Int): Pair<String, Int>? {
        val tagEnd = html.indexOf('>', open)
        if (tagEnd < 0) return null
        var depth = 1
        val re = Regex("""<div\b|</div>""")
        var pos = tagEnd + 1
        while (depth > 0) {
            val m = re.find(html, pos) ?: return null
            if (m.value == "</div>") depth-- else depth++
            if (depth == 0) return html.substring(tagEnd + 1, m.range.first) to (m.range.last + 1)
            pos = m.range.last + 1
        }
        return null
    }

    private fun extractGeniusLyrics(html: String): String? {
        val parts = ArrayList<String>()
        var from = 0
        while (true) {
            // Контейнер текста помечен атрибутом, а в старой вёрстке — классом Lyrics__Container.
            val m1 = html.indexOf("data-lyrics-container=\"true\"", from)
            val m2 = html.indexOf("class=\"Lyrics__Container", from)
            val m = listOf(m1, m2).filter { it >= 0 }.minOrNull() ?: -1
            if (m < 0) break
            val open = html.lastIndexOf("<div", m)
            val (inner0, end) = balancedDiv(html, open) ?: break
            from = end
            var inner = inner0
            // Блоки «контрибьюторы», «описание» помечены как не-текст — вырезаем.
            while (true) {
                val ex = inner.indexOf("data-exclude-from-selection=\"true\"")
                if (ex < 0) break
                val exOpen = inner.lastIndexOf("<div", ex)
                val cut = if (exOpen >= 0) balancedDiv(inner, exOpen) else null
                inner = if (cut != null) inner.removeRange(exOpen, cut.second) else inner.removeRange(ex, ex + 10)
            }
            val br = "@@BR@@"
            var text = inner.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), br)
                .replace(Regex("""<[^>]+>"""), "")
            text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replace(br, "\n")
            parts += text.trim()
        }
        val joined = parts.joinToString("\n\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return joined.takeIf { it.length > 20 }
    }

    // ---------- Список источников ----------

    /** Все источники по порядку предпочтения; интерфейс запускает их параллельно и показывает каждый по мере прихода. */
    val providers: List<LyricsProvider> by lazy {
        listOf(
            LyricsProvider("y", "Яндекс / VK Музыка") { t -> yandex(t)?.let { LyricsSource("y", if (VkIds.isVk(t.id)) "VK Музыка" else "Яндекс Музыка", it.synced, it.plain) } },
            LyricsProvider("lrclib", "LRCLIB") { t -> lrclib(t) },
            LyricsProvider("ne", "NetEase") { t -> netease(t) },
            LyricsProvider("kg", "Kugou") { t -> kugou(t) },
            LyricsProvider("qq", "QQ Music") { t -> qqmusic(t) },
            LyricsProvider("g", "Genius") { t -> genius(t)?.let { LyricsSource("g", "Genius", plain = it) } },
            LyricsProvider("ovh", "lyrics.ovh") { t -> ovh(t) },
        )
    }

    // ---------- Общий кэш для новых источников ----------

    private val missTtl = 12L * 3600 * 1000

    /**
     * Кэш найденного текста; «не найдено» запоминается на 12 часов. Ошибка сети промахом не считается —
     * в следующий раз источник спросят заново. Суффикс .none2 сбрасывает промахи, записанные прежней, более строгой версией.
     */
    private suspend fun cached(prefix: String, name: String, t: Track, fetch: suspend () -> LyricsSource?): LyricsSource? = withContext(Dispatchers.IO) {
        val f = File(dir, "${prefix}_${key(t)}.v2.json")
        val miss = File(dir, "${prefix}_${key(t)}.none3")
        readYandexCache(f)?.let { return@withContext LyricsSource(prefix, name, it.synced, it.plain) }
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < missTtl) return@withContext null
        val attempt = runCatching { fetch() }
        val r = attempt.getOrNull()?.takeIf { it.hasText }
        if (r != null) runCatching { writeYandexCache(f, YandexLyrics(r.synced, r.plain)) }
        else if (attempt.isSuccess) runCatching { miss.writeText("") }
        r
    }

    /** Забывает запомненные «не найдено» для трека — следующий поиск опросит все источники заново. */
    fun forgetMisses(t: Track) {
        val k = key(t)
        dir.listFiles { f -> f.name.endsWith("_$k.none3") }?.forEach { it.delete() }
    }

    private fun cleanTitle(t: Track): String =
        t.title.replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim().ifBlank { t.title }

    private fun firstArtist(t: Track): String =
        t.artistRefs.firstOrNull()?.name ?: t.artists.substringBefore(',').trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- Защита от путаницы с ремиксами и другими версиями ----------

    /** Пометки версии: (группа, шаблон). Ищутся только в «приписках» — скобках, после « - » и в поле version. */
    private val versionRules: List<Pair<String, Regex>> = listOf(
        "remix" to Regex("""re-?mix|ремикс|рэмикс|bootleg|бутлег|rework|(?<!\p{L})flip(?!\p{L})|(?<!\p{L})vip(?!\p{L})"""),
        "live" to Regex("""(?<!\p{L})live(?!\p{L})|лайв|концертн|живое исполнение"""),
        "acoustic" to Regex("""acoustic|акустик"""),
        "instrumental" to Regex("""instrumental|инструментал|минус|karaoke|караоке|без слов|(?<!\p{L})inst(?!\p{L})"""),
        "speed" to Regex("""sped|speed|slowed|reverb|nightcore|найткор|(?<!\p{L})8d(?!\p{L})|bass boost"""),
        "cover" to Regex("""cover|кавер|tribute"""),
        "demo" to Regex("""(?<!\p{L})demo(?!\p{L})|демо"""),
        "mashup" to Regex("""mash-?up|мэшап|машап"""),
    )

    /** Приписки к названию: поле version, всё в скобках и хвост после « - ». */
    private fun annotations(title: String, version: String?): String {
        val sb = StringBuilder(version.orEmpty()).append(' ')
        Regex("""[(\[{](.*?)[)\]}]""").findAll(title).forEach { sb.append(it.groupValues[1]).append(' ') }
        Regex("""\s[-–—]\s(.+)$""").find(title)?.let { sb.append(it.groupValues[1]) }
        return sb.toString().lowercase()
    }

    private fun tagsOf(title: String, version: String?): Set<String> {
        val a = annotations(title, version)
        return versionRules.filter { (_, re) -> re.containsMatchIn(a) }.mapTo(HashSet()) { it.first }
    }

    private fun versionTags(t: Track): Set<String> = tagsOf(t.title, t.version)

    /** true — найденная запись той же версии, что и наш трек (оригинал не путается с ремиксом, live, акустикой и т. д.). */
    private fun sameVersion(t: Track, candidateTitle: String): Boolean = versionTags(t) == tagsOf(candidateTitle, null)

    /** Название для запроса: у оригинала — без скобок, у ремикса и прочих версий — полное, чтобы искалась именно эта версия. */
    private fun searchTitle(t: Track): String {
        if (versionTags(t).isEmpty()) return cleanTitle(t)
        val v = t.version?.takeIf { it.isNotBlank() && !t.title.contains(it, ignoreCase = true) }
        return if (v != null) "${t.title} ($v)" else t.title
    }

    /** Исполнители трека в нормализованном виде (для сверки с названиями у источников). */
    private fun wantedArtistNames(t: Track): List<String> =
        (t.artistRefs.map { it.name } + t.artists.split(',', '&', '/').map { it.trim() })
            .map { norm(it) }.filter { it.length >= 2 }.distinct()

    private fun artistMatches(t: Track, candidate: String): Boolean {
        val c = norm(candidate)
        if (c.length < 2) return false
        return wantedArtistNames(t).any { it == c || (it.length >= 4 && c.contains(it)) || (c.length >= 4 && it.contains(c)) }
    }

    // ---------- LRCLIB ----------

    /** lrclib.net: открытая база с синхронизированными текстами. Сначала точное совпадение, потом поиск с проверкой длительности. */
    suspend fun lrclib(t: Track): LyricsSource? = cached("lrclib", "LRCLIB", t) {
        val title = cleanTitle(t)
        val artist = firstArtist(t)
        val qTitle = searchTitle(t)
        val sec = (t.durationMs / 1000).toInt()
        fun parse(o: JSONObject): LyricsSource? {
            if (o.optBoolean("instrumental")) return null
            val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { parseLrc(it) }.orEmpty()
            val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }?.trim()
            return LyricsSource("lrclib", "LRCLIB", synced, plain).takeIf { it.hasText }
        }
        // 1) точное совпадение (нужна длительность)
        if (sec > 0) {
            val album = t.albumTitle?.let { "&album_name=${enc(it)}" }.orEmpty()
            val body = get("https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(qTitle)}$album&duration=$sec")
            val hit = body?.let { runCatching { JSONObject(it) }.getOrNull() }?.takeIf { sameVersion(t, it.optString("trackName")) }?.let(::parse)
            if (hit != null) return@cached hit
        }
        // 2) поиск; берём результат с подходящими названием, исполнителем и длительностью (±4 с), текст с таймкодами предпочтительнее
        val body = get("https://lrclib.net/api/search?track_name=${enc(qTitle)}&artist_name=${enc(artist)}") ?: return@cached null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return@cached null
        val wantTitle = norm(title)
        val wantArtists = norm(t.artists) + t.artistRefs.joinToString("") { norm(it.name) }
        var best: LyricsSource? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ti = norm(o.optString("trackName"))
            val ar = norm(o.optString("artistName"))
            if (ti.isEmpty() || ar.isEmpty()) continue
            if (!(ti.contains(wantTitle) || wantTitle.contains(ti))) continue
            if (!sameVersion(t, o.optString("trackName"))) continue
            if (!wantArtists.contains(ar) && !ar.contains(norm(artist))) continue
            val d = o.optDouble("duration", 0.0)
            if (sec > 0 && d > 0 && kotlin.math.abs(d - sec) > 4) continue
            val r = parse(o) ?: continue
            val b = best
            if (b == null || (r.isSynced && !b.isSynced)) best = r
            if (best?.isSynced == true) break
        }
        if (best != null) return@cached best
        // 3) мягкий поиск одной строкой «исполнитель название»: исполнителя не сверяем (у Яндекса он может быть записан иначе),
        // но название должно совпасть полностью, а длительность — отличаться не больше чем на 3 с
        val body2 = get("https://lrclib.net/api/search?q=${enc("$artist $qTitle")}") ?: return@cached null
        val arr2 = runCatching { JSONArray(body2) }.getOrNull() ?: return@cached null
        for (i in 0 until arr2.length()) {
            val o = arr2.optJSONObject(i) ?: continue
            if (norm(o.optString("trackName")) != wantTitle) continue
            if (!sameVersion(t, o.optString("trackName"))) continue
            val d = o.optDouble("duration", 0.0)
            if (sec > 0 && d > 0 && kotlin.math.abs(d - sec) > 3) continue
            val r = parse(o) ?: continue
            val b = best
            if (b == null || (r.isSynced && !b.isSynced)) best = r
            if (best?.isSynced == true) break
        }
        best
    }

    // ---------- NetEase Cloud Music ----------

    /** music.163.com: поиск песни и её LRC. Принимаем только совпадение по названию, исполнителю и длительности (±5 с). */
    suspend fun netease(t: Track): LyricsSource? = cached("ne", "NetEase", t) {
        val title = cleanTitle(t)
        val artist = firstArtist(t)
        val qTitle = searchTitle(t)
        val sec = (t.durationMs / 1000).toInt()
        val hdr = mapOf("Referer" to "https://music.163.com/", "Cookie" to "appver=2.0.2")
        val q = enc("$artist $qTitle")
        val sBody = get("https://music.163.com/api/search/get/web?csrf_token=&s=$q&type=1&offset=0&limit=8", hdr) ?: return@cached null
        val songs = JSONObject(sBody).optJSONObject("result")?.optJSONArray("songs") ?: return@cached null
        val wantTitle = norm(title)
        val wantArtists = norm(t.artists) + t.artistRefs.joinToString("") { norm(it.name) }
        var id: Long? = null
        // Первый проход строгий (название + исполнитель + ±5 с), второй мягкий (название целиком + ±2 с).
        for (strict in listOf(true, false)) {
            if (id != null) break
            for (i in 0 until songs.length()) {
                val o = songs.optJSONObject(i) ?: continue
                val ti = norm(o.optString("name"))
                if (ti.isEmpty()) continue
                val alias = o.optJSONArray("alias")?.let { al -> (0 until al.length()).joinToString(" ") { al.optString(it) } }.orEmpty()
                if (!sameVersion(t, o.optString("name") + " (" + alias + ")")) continue
                val d = o.optLong("duration", 0L) / 1000
                if (strict) {
                    if (!(ti.contains(wantTitle) || wantTitle.contains(ti))) continue
                    val artists = o.optJSONArray("artists")
                    val okArtist = artists != null && (0 until artists.length()).any {
                        val a = norm(artists.optJSONObject(it)?.optString("name").orEmpty())
                        a.isNotEmpty() && (wantArtists.contains(a) || a.contains(norm(artist)))
                    }
                    if (!okArtist) continue
                    if (sec > 0 && d > 0 && kotlin.math.abs(d - sec) > 5) continue
                } else {
                    if (ti != wantTitle) continue
                    if (sec <= 0 || d <= 0 || kotlin.math.abs(d - sec) > 2) continue
                }
                id = o.optLong("id"); break
            }
        }
        val songId = id ?: return@cached null
        val lBody = get("https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1", hdr) ?: return@cached null
        val lrc = JSONObject(lBody).optJSONObject("lrc")?.optString("lyric").orEmpty()
        // «纯音乐» — «чисто инструментальная музыка»
        if (lrc.isBlank() || lrc.contains("纯音乐")) return@cached null
        val synced = parseLrc(lrc)
        if (synced.isNotEmpty()) LyricsSource("ne", "NetEase", synced = synced)
        else {
            // У части песен текст есть, но без таймкодов — показываем как обычный.
            val plain = lrc.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }.joinToString("\n")
            if (plain.length > 40) LyricsSource("ne", "NetEase", plain = plain) else null
        }
    }

    // ---------- lyrics.ovh ----------

    suspend fun ovh(t: Track): LyricsSource? = cached("ovh", "lyrics.ovh", t) {
        // lyrics.ovh ищет только по чистому названию и не говорит, какая это версия: для ремиксов и live не используем.
        if (versionTags(t).isNotEmpty()) return@cached null
        val artists = (t.artistRefs.map { it.name } + t.artists.split(',').map { it.trim() }).filter { it.isNotBlank() }.distinct().take(3)
        for (a in artists) {
            val body = get("https://api.lyrics.ovh/v1/${enc(a)}/${enc(cleanTitle(t))}") ?: continue
            val text = runCatching { JSONObject(body).optString("lyrics") }.getOrNull().orEmpty().replace("\r\n", "\n").trim()
            if (text.length >= 30) return@cached LyricsSource("ovh", "lyrics.ovh", plain = text.replace(Regex("\n{3,}"), "\n\n"))
        }
        null
    }

    // ---------- Kugou ----------

    /** kugou.com: поиск песни по названию и исполнителю, затем LRC по hash. Совпадение — по названию, исполнителю и длительности (±5 с). */
    suspend fun kugou(t: Track): LyricsSource? = cached("kg", "Kugou", t) {
        val title = cleanTitle(t)
        val artist = firstArtist(t)
        val qTitle = searchTitle(t)
        val sec = (t.durationMs / 1000).toInt()
        val sBody = get("https://mobileservice.kugou.com/api/v3/search/song?format=json&keyword=${enc("$artist $qTitle")}&page=1&pagesize=8&showtype=1")
            ?: return@cached null
        val list = JSONObject(sBody).optJSONObject("data")?.optJSONArray("info") ?: return@cached null
        val wantTitle = norm(title)
        val wantArtists = norm(t.artists) + t.artistRefs.joinToString("") { norm(it.name) }
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val ti = norm(o.optString("songname"))
            val ar = norm(o.optString("singername"))
            if (ti.isEmpty() || !(ti.contains(wantTitle) || wantTitle.contains(ti))) continue
            if (!sameVersion(t, o.optString("songname"))) continue
            if (ar.isEmpty() || !(wantArtists.contains(ar) || ar.contains(norm(artist)))) continue
            val d = o.optInt("duration", 0)
            if (sec > 0 && d > 0 && kotlin.math.abs(d - sec) > 5) continue
            val hash = o.optString("hash").takeIf { it.isNotBlank() } ?: continue
            val found = get("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${enc("$artist - $title")}&duration=${sec * 1000}&hash=$hash")
                ?: continue
            val cand = JSONObject(found).optJSONArray("candidates")?.optJSONObject(0) ?: continue
            val id = cand.optString("id")
            val key = cand.optString("accesskey")
            if (id.isBlank() || key.isBlank()) continue
            val dl = get("https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$key&fmt=lrc&charset=utf8") ?: continue
            val b64 = JSONObject(dl).optString("content")
            if (b64.isBlank()) continue
            val lrc = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8).removePrefix("\uFEFF")
            val synced = parseLrc(lrc)
            if (synced.isNotEmpty()) return@cached LyricsSource("kg", "Kugou", synced = synced)
        }
        null
    }

    // ---------- QQ Music ----------

    /** y.qq.com: поиск и LRC. Совпадение — по названию, исполнителю и длительности (±5 с). */
    suspend fun qqmusic(t: Track): LyricsSource? = cached("qq", "QQ Music", t) {
        val title = cleanTitle(t)
        val artist = firstArtist(t)
        val qTitle = searchTitle(t)
        val sec = (t.durationMs / 1000).toInt()
        val hdr = mapOf("Referer" to "https://y.qq.com/", "Origin" to "https://y.qq.com")
        val sBody = get("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&new_json=1&cr=1&n=8&p=1&w=${enc("$artist $qTitle")}", hdr)
            ?: return@cached null
        val list = JSONObject(sBody).optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return@cached null
        val wantTitle = norm(title)
        val wantArtists = norm(t.artists) + t.artistRefs.joinToString("") { norm(it.name) }
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val ti = norm(o.optString("name"))
            if (ti.isEmpty() || !(ti.contains(wantTitle) || wantTitle.contains(ti))) continue
            if (!sameVersion(t, o.optString("name") + " " + o.optString("title") + " (" + o.optString("subtitle") + ")")) continue
            val singers = o.optJSONArray("singer")
            val okArtist = singers != null && (0 until singers.length()).any {
                val a = norm(singers.optJSONObject(it)?.optString("name").orEmpty())
                a.isNotEmpty() && (wantArtists.contains(a) || a.contains(norm(artist)))
            }
            if (!okArtist) continue
            val d = o.optInt("interval", 0)
            if (sec > 0 && d > 0 && kotlin.math.abs(d - sec) > 5) continue
            val mid = o.optString("mid").takeIf { it.isNotBlank() } ?: continue
            val lBody = get("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$mid&format=json&nobase64=1&g_tk=5381", hdr) ?: continue
            val raw = JSONObject(lBody).optString("lyric")
            if (raw.isBlank()) continue
            // В ответе бывают HTML-сущности («&#58;» вместо «:»).
            val lrc = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
            val synced = parseLrc(lrc)
            if (synced.isNotEmpty()) return@cached LyricsSource("qq", "QQ Music", synced = synced)
        }
        null
    }

    // ---------- Ручной поиск ----------

    /** Поисковики по всем источникам, в том же порядке, что и [providers]. */
    val searchers: List<LyricsSearcher> by lazy {
        listOf(
            LyricsSearcher("y", "Яндекс / VK Музыка") { q -> searchApi(q) },
            LyricsSearcher("lrclib", "LRCLIB") { q -> searchLrclib(q) },
            LyricsSearcher("ne", "NetEase") { q -> searchNetease(q) },
            LyricsSearcher("kg", "Kugou") { q -> searchKugou(q) },
            LyricsSearcher("qq", "QQ Music") { q -> searchQq(q) },
            LyricsSearcher("g", "Genius") { q -> searchGenius(q) },
            LyricsSearcher("ovh", "lyrics.ovh") { q -> searchOvh(q) },
        )
    }

    private val searchLimit = 6

    private suspend fun <T, R : Any> List<T>.pmap(f: suspend (T) -> R?): List<R> = coroutineScope {
        map { item -> async(Dispatchers.IO) { runCatching { f(item) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    private fun previewOf(s: LyricsSource): String? {
        val lines = if (s.isSynced) s.synced.map { it.text } else s.plain.orEmpty().lines()
        return lines.map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** Кандидат с уже полученным текстом. */
    private fun ready(srcId: String, srcName: String, artist: String, title: String, durMs: Long, s: LyricsSource) =
        LyricsCandidate(srcId, srcName, artist, title, durMs, s.isSynced, previewOf(s)) { s }

    /** Яндекс / VK: обычный поиск треков аккаунта, у каждого найденного проверяется, есть ли текст. */
    private suspend fun searchApi(q: String): List<LyricsCandidate> {
        val tracks = withContext(Dispatchers.IO) { api.search(q) }.filter { !it.isLocal }.take(searchLimit)
        return tracks.pmap { t ->
            val y = yandex(t) ?: return@pmap null
            val name = if (VkIds.isVk(t.id)) "VK Музыка" else "Яндекс Музыка"
            ready("y", name, t.artists, listOfNotNull(t.title, t.version?.takeIf { it.isNotBlank() && !t.title.contains(it, true) }?.let { "($it)" }).joinToString(" "),
                t.durationMs, LyricsSource("y", name, y.synced, y.plain))
        }
    }

    private suspend fun searchLrclib(q: String): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val body = get("https://lrclib.net/api/search?q=${enc(q)}") ?: return@withContext emptyList()
        val arr = JSONArray(body)
        val out = ArrayList<LyricsCandidate>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("instrumental")) continue
            val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { parseLrc(it) }.orEmpty()
            val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }?.trim()
            val src = LyricsSource("lrclib", "LRCLIB", synced, plain)
            if (!src.hasText) continue
            out += ready("lrclib", "LRCLIB", o.optString("artistName"), o.optString("trackName"), (o.optDouble("duration", 0.0) * 1000).toLong(), src)
            if (out.size >= 8) break
        }
        out
    }

    private val neHeaders = mapOf("Referer" to "https://music.163.com/", "Cookie" to "appver=2.0.2")

    private suspend fun searchNetease(q: String): List<LyricsCandidate> {
        val body = withContext(Dispatchers.IO) {
            get("https://music.163.com/api/search/get/web?csrf_token=&s=${enc(q)}&type=1&offset=0&limit=$searchLimit", neHeaders)
        } ?: return emptyList()
        val songs = JSONObject(body).optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        val list = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }
        return list.pmap { o ->
            val lBody = get("https://music.163.com/api/song/lyric?id=${o.optLong("id")}&lv=1&kv=1&tv=-1", neHeaders) ?: return@pmap null
            val lrc = JSONObject(lBody).optJSONObject("lrc")?.optString("lyric").orEmpty()
            if (lrc.isBlank() || lrc.contains("纯音乐")) return@pmap null
            val synced = parseLrc(lrc)
            val src = if (synced.isNotEmpty()) LyricsSource("ne", "NetEase", synced = synced) else {
                val plain = lrc.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }.joinToString("\n")
                if (plain.length > 40) LyricsSource("ne", "NetEase", plain = plain) else return@pmap null
            }
            val artists = o.optJSONArray("artists")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") } }.orEmpty()
            ready("ne", "NetEase", artists.joinToString(", "), o.optString("name"), o.optLong("duration", 0L), src)
        }
    }

    private suspend fun searchKugou(q: String): List<LyricsCandidate> {
        val body = withContext(Dispatchers.IO) {
            get("https://mobileservice.kugou.com/api/v3/search/song?format=json&keyword=${enc(q)}&page=1&pagesize=$searchLimit&showtype=1")
        } ?: return emptyList()
        val list = JSONObject(body).optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.pmap { o ->
            val hash = o.optString("hash").takeIf { it.isNotBlank() } ?: return@pmap null
            val artist = o.optString("singername")
            val title = o.optString("songname")
            val sec = o.optInt("duration", 0)
            val found = get("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${enc("$artist - $title")}&duration=${sec * 1000}&hash=$hash") ?: return@pmap null
            val cand = JSONObject(found).optJSONArray("candidates")?.optJSONObject(0) ?: return@pmap null
            val id = cand.optString("id"); val key = cand.optString("accesskey")
            if (id.isBlank() || key.isBlank()) return@pmap null
            val dl = get("https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$key&fmt=lrc&charset=utf8") ?: return@pmap null
            val b64 = JSONObject(dl).optString("content")
            if (b64.isBlank()) return@pmap null
            val lrc = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8).removePrefix("﻿")
            val synced = parseLrc(lrc).takeIf { it.isNotEmpty() } ?: return@pmap null
            ready("kg", "Kugou", artist, title, sec * 1000L, LyricsSource("kg", "Kugou", synced = synced))
        }
    }

    private suspend fun searchQq(q: String): List<LyricsCandidate> {
        val hdr = mapOf("Referer" to "https://y.qq.com/", "Origin" to "https://y.qq.com")
        val body = withContext(Dispatchers.IO) {
            get("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&new_json=1&cr=1&n=$searchLimit&p=1&w=${enc(q)}", hdr)
        } ?: return emptyList()
        val list = JSONObject(body).optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.pmap { o ->
            val mid = o.optString("mid").takeIf { it.isNotBlank() } ?: return@pmap null
            val lBody = get("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$mid&format=json&nobase64=1&g_tk=5381", hdr) ?: return@pmap null
            val raw = JSONObject(lBody).optString("lyric")
            if (raw.isBlank()) return@pmap null
            val synced = parseLrc(Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()).takeIf { it.isNotEmpty() } ?: return@pmap null
            val singers = o.optJSONArray("singer")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") } }.orEmpty()
            ready("qq", "QQ Music", singers.joinToString(", "), o.optString("name"), o.optInt("interval", 0) * 1000L, LyricsSource("qq", "QQ Music", synced = synced))
        }
    }

    /** Genius: текст только обычный, поэтому «без тактов»; страница с текстом загружается, когда вариант выбран. */
    private suspend fun searchGenius(q: String): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        var results: List<JSONObject> = emptyList()
        for (endpoint in listOf("search/multi?per_page=8&q=", "search/song?per_page=8&q=", "search?q=")) {
            val body = runCatching { get("https://genius.com/api/$endpoint${enc(q)}", geniusHeaders) }.getOrNull() ?: continue
            results = runCatching { geniusResults(body) }.getOrNull().orEmpty()
            if (results.isNotEmpty()) break
        }
        results.take(8).map { r ->
            val url = r.optString("url")
            LyricsCandidate("g", "Genius", r.optJSONObject("primary_artist")?.optString("name").orEmpty().ifBlank { r.optString("artist_names") },
                r.optString("title"), 0L, false, null) {
                withContext(Dispatchers.IO) {
                    val html = get(url, geniusHeaders) ?: return@withContext null
                    extractGeniusLyrics(html)?.let { LyricsSource("g", "Genius", plain = it) }
                }
            }
        }
    }

    /** lyrics.ovh: подсказки по названию (api.lyrics.ovh/suggest), текст — по паре «исполнитель + название». */
    private suspend fun searchOvh(q: String): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val body = get("https://api.lyrics.ovh/suggest/${enc(q)}") ?: return@withContext emptyList()
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<LyricsCandidate>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val artist = o.optJSONObject("artist")?.optString("name").orEmpty()
            val title = o.optString("title")
            if (artist.isBlank() || title.isBlank() || !seen.add(norm(artist) + "|" + norm(title))) continue
            out += LyricsCandidate("ovh", "lyrics.ovh", artist, title, o.optLong("duration", 0L) * 1000, false, null) {
                withContext(Dispatchers.IO) {
                    val b = get("https://api.lyrics.ovh/v1/${enc(artist)}/${enc(title)}") ?: return@withContext null
                    val text = runCatching { JSONObject(b).optString("lyrics") }.getOrNull().orEmpty().replace("\r\n", "\n").trim()
                    if (text.length >= 30) LyricsSource("ovh", "lyrics.ovh", plain = text.replace(Regex("\n{3,}"), "\n\n")) else null
                }
            }
            if (out.size >= 8) break
        }
        out
    }

    // ---------- Привязка текста к треку ----------

    private val pinDir: File by lazy { File(ctx.filesDir, "lyrics_pins").apply { mkdirs() } }

    /** Текст, привязанный к треку вручную (id источника — «pin»), или null. */
    suspend fun pinned(t: Track): LyricsSource? = withContext(Dispatchers.IO) {
        val f = File(pinDir, "${key(t)}.json")
        if (!f.exists()) return@withContext null
        runCatching {
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("lines") ?: JSONArray()
            LyricsSource(
                "pin", o.optString("name"),
                List(arr.length()) { i -> arr.getJSONArray(i).let { LyricLine(it.getLong(0), it.getString(1)) } },
                o.optString("plain").ifBlank { null },
            ).takeIf { it.hasText }
        }.getOrNull()
    }

    suspend fun pin(t: Track, s: LyricsSource) = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray().also { a -> s.synced.forEach { a.put(JSONArray().put(it.timeMs).put(it.text)) } }
            File(pinDir, "${key(t)}.json").writeText(JSONObject().put("name", s.name).put("lines", arr).put("plain", s.plain ?: "").toString())
        }
        Unit
    }

    suspend fun unpin(t: Track) = withContext(Dispatchers.IO) { File(pinDir, "${key(t)}.json").delete(); Unit }
}
