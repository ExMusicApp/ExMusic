package app.ymusic.m3e.data

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

data class LyricLine(val timeMs: Long, val text: String)

data class YandexLyrics(val synced: List<LyricLine>, val plain: String?) {
    val isSynced: Boolean get() = synced.isNotEmpty()
}

data class LyricsSource(
    val id: String,
    val name: String,
    val synced: List<LyricLine> = emptyList(),
    val plain: String? = null,
) {
    val isSynced: Boolean get() = synced.isNotEmpty()
    val hasText: Boolean get() = isSynced || !plain.isNullOrBlank()
}

val LyricsSourceOptions: List<Triple<String, String, String>> = listOf(
    Triple("y", "Яндекс Музыка", "Родной текст трека, часто с таймкодами"),
    Triple("lrclib", "LRCLIB", "Открытая база, чаще всего с таймкодами"),
    Triple("ne", "NetEase", "Китайская база, много текстов с таймкодами"),
    Triple("kg", "Kugou", "Китайская база, тексты с таймкодами"),
    Triple("qq", "QQ Music", "Китайская база, тексты с таймкодами"),
    Triple("g", "Genius", "Только обычный текст, без таймкодов"),
    Triple("ovh", "lyrics.ovh", "Запасной источник, обычный текст"),
)

class LyricsProvider(val id: String, val name: String, val fetch: suspend (Track) -> LyricsSource?)

class LyricsRepository(
    private val ctx: Context,
    private val http: OkHttpClient,
    private val api: YandexMusicApi,
) {
    private val dir: File by lazy { File(ctx.cacheDir, "lyrics").apply { mkdirs() } }
    private val browserUa =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun key(t: Track): String =
        MessageDigest.getInstance("MD5").digest(t.id.toByteArray()).joinToString("") { "%02x".format(it) }

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
                    ?: lrc?.takeIf { !it.contains("[0") }
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

    private fun geniusArtistNames(r: JSONObject): List<String> {
        val out = ArrayList<String>()
        r.optJSONObject("primary_artist")?.optString("name")?.let(out::add)
        r.optString("artist_names").takeIf { it.isNotBlank() }?.split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bи\b)\s*""", RegexOption.IGNORE_CASE))?.let(out::addAll)
        r.optJSONArray("featured_artists")?.let { fa -> for (i in 0 until fa.length()) fa.optJSONObject(i)?.optString("name")?.let(out::add) }
        return out.filter { it.isNotBlank() }
    }

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
                results.filter { title(it) == wantTitle && artistOk(it) }.forEach(::add)
                results.filter { artistOk(it) && title(it).isNotEmpty() && (title(it).contains(wantTitle) || wantTitle.contains(title(it))) }.forEach(::add)
                results.filter { title(it) == wantTitle }.forEach(::add)
                if (urls.isNotEmpty()) break@outer
            }
        }
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
            val m1 = html.indexOf("data-lyrics-container=\"true\"", from)
            val m2 = html.indexOf("class=\"Lyrics__Container", from)
            val m = listOf(m1, m2).filter { it >= 0 }.minOrNull() ?: -1
            if (m < 0) break
            val open = html.lastIndexOf("<div", m)
            val (inner0, end) = balancedDiv(html, open) ?: break
            from = end
            var inner = inner0
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

    val providers: List<LyricsProvider> by lazy {
        listOf(
            LyricsProvider("y", "Яндекс Музыка") { t -> yandex(t)?.let { LyricsSource("y", "Яндекс Музыка", it.synced, it.plain) } },
            LyricsProvider("lrclib", "LRCLIB") { t -> lrclib(t) },
            LyricsProvider("ne", "NetEase") { t -> netease(t) },
            LyricsProvider("kg", "Kugou") { t -> kugou(t) },
            LyricsProvider("qq", "QQ Music") { t -> qqmusic(t) },
            LyricsProvider("g", "Genius") { t -> genius(t)?.let { LyricsSource("g", "Genius", plain = it) } },
            LyricsProvider("ovh", "lyrics.ovh") { t -> ovh(t) },
        )
    }

    private val missTtl = 12L * 3600 * 1000

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

    fun forgetMisses(t: Track) {
        val k = key(t)
        dir.listFiles { f -> f.name.endsWith("_$k.none3") }?.forEach { it.delete() }
    }

    private fun cleanTitle(t: Track): String =
        t.title.replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim().ifBlank { t.title }

    private fun firstArtist(t: Track): String =
        t.artistRefs.firstOrNull()?.name ?: t.artists.substringBefore(',').trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

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

    private fun sameVersion(t: Track, candidateTitle: String): Boolean = versionTags(t) == tagsOf(candidateTitle, null)

    private fun searchTitle(t: Track): String {
        if (versionTags(t).isEmpty()) return cleanTitle(t)
        val v = t.version?.takeIf { it.isNotBlank() && !t.title.contains(it, ignoreCase = true) }
        return if (v != null) "${t.title} ($v)" else t.title
    }

    private fun wantedArtistNames(t: Track): List<String> =
        (t.artistRefs.map { it.name } + t.artists.split(',', '&', '/').map { it.trim() })
            .map { norm(it) }.filter { it.length >= 2 }.distinct()

    private fun artistMatches(t: Track, candidate: String): Boolean {
        val c = norm(candidate)
        if (c.length < 2) return false
        return wantedArtistNames(t).any { it == c || (it.length >= 4 && c.contains(it)) || (c.length >= 4 && it.contains(c)) }
    }

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
        if (sec > 0) {
            val album = t.albumTitle?.let { "&album_name=${enc(it)}" }.orEmpty()
            val body = get("https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(qTitle)}$album&duration=$sec")
            val hit = body?.let { runCatching { JSONObject(it) }.getOrNull() }?.takeIf { sameVersion(t, it.optString("trackName")) }?.let(::parse)
            if (hit != null) return@cached hit
        }
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
        if (lrc.isBlank() || lrc.contains("纯音乐")) return@cached null
        val synced = parseLrc(lrc)
        if (synced.isNotEmpty()) LyricsSource("ne", "NetEase", synced = synced)
        else {
            val plain = lrc.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }.joinToString("\n")
            if (plain.length > 40) LyricsSource("ne", "NetEase", plain = plain) else null
        }
    }

    suspend fun ovh(t: Track): LyricsSource? = cached("ovh", "lyrics.ovh", t) {
        if (versionTags(t).isNotEmpty()) return@cached null
        val artists = (t.artistRefs.map { it.name } + t.artists.split(',').map { it.trim() }).filter { it.isNotBlank() }.distinct().take(3)
        for (a in artists) {
            val body = get("https://api.lyrics.ovh/v1/${enc(a)}/${enc(cleanTitle(t))}") ?: continue
            val text = runCatching { JSONObject(body).optString("lyrics") }.getOrNull().orEmpty().replace("\r\n", "\n").trim()
            if (text.length >= 30) return@cached LyricsSource("ovh", "lyrics.ovh", plain = text.replace(Regex("\n{3,}"), "\n\n"))
        }
        null
    }

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
            val lrc = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
            val synced = parseLrc(lrc)
            if (synced.isNotEmpty()) return@cached LyricsSource("qq", "QQ Music", synced = synced)
        }
        null
    }
}
