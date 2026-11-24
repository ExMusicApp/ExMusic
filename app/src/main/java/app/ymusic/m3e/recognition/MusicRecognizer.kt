package app.ymusic.m3e.recognition

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Откуда слушаем: микрофон или звук, который играет на самом устройстве (другие приложения). */
enum class RecSource { Mic, Device }

/** Найденный трек. */
data class RecResult(
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: String? = null,
) {
    /** Запрос для поиска в Яндекс Музыке. */
    val query: String get() = "$artist $title".trim()
}

/** Состояние распознавания: его показывает и шторка в приложении, и окно из плитки в шторке уведомлений. */
sealed interface RecState {
    data object Idle : RecState
    /** [matching] — пока идёт запись, фрагмент уже отправлен на проверку (трек могут узнать в любую секунду). */
    data class Listening(val source: RecSource, val secondsLeft: Int, val matching: Boolean = false) : RecState
    data class Searching(val source: RecSource) : RecState
    data class Found(val result: RecResult) : RecState
    data object NotFound : RecState
    data class Error(val message: String) : RecState
}

/**
 * Что делать, когда трек узнан, а окно распознавания не на экране (например, слушали звук другого приложения).
 * Если окно распознавания открыто и видно, результат показывается прямо в нём.
 */
enum class FoundAction(val title: String, val hint: String) {
    Notify("Уведомление", "Всплывающее уведомление с названием и исполнителем. Нажатие открывает поиск трека в приложении"),
    OpenApp("Открыть приложение", "Сразу открыть приложение на поиске найденного трека. Если система не даёт открыть его из фона, придёт уведомление"),
    Both("Оба способа", "И открыть приложение, и оставить уведомление с результатом"),
}

/** Настройки распознавания (SharedPreferences). */
object RecognitionSettings {
    private var prefs: SharedPreferences? = null

    /** Действие при найденном треке (Настройки → Распознавание музыки → «Когда трек узнан»). */
    var onFound by mutableStateOf(FoundAction.OpenApp)
        private set

    fun updateOnFound(a: FoundAction) {
        onFound = a
        prefs?.edit()?.putString("on_found", a.name)?.apply()
    }

    /** Необязательный ключ api_token с https://dashboard.audd.io: запасной вариант, если Shazam не узнал трек. */
    var auddToken by mutableStateOf("")
        private set

    /** Длина записываемого фрагмента, секунд. */
    var clipSeconds by mutableIntStateOf(12)
        private set

    val clipOptions = listOf(8, 12, 16)

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("recognition", Context.MODE_PRIVATE)
        prefs = p
        auddToken = p.getString("audd_token", "").orEmpty()
        clipSeconds = p.getInt("clip_seconds", 12).takeIf { it in clipOptions } ?: 12
        onFound = FoundAction.entries.firstOrNull { it.name == p.getString("on_found", null) } ?: FoundAction.OpenApp
    }

    fun updateToken(t: String) {
        auddToken = t.trim()
        prefs?.edit()?.putString("audd_token", auddToken)?.apply()
    }

    fun updateClipSeconds(s: Int) {
        clipSeconds = s
        prefs?.edit()?.putInt("clip_seconds", s)?.apply()
    }
}

/**
 * Распознавание музыки: записываем фрагмент (микрофон или внутренний звук устройства через
 * AudioPlaybackCapture), считаем отпечаток прямо на телефоне и отправляем его в Shazam: ключи и настройка
 * не нужны. Найденный трек потом ищется в Яндекс Музыке по «исполнитель + название».
 * Если Shazam ничего не нашёл, а в настройках указан ключ AudD, пробуем ещё и его.
 */
object Recognizer {
    private const val RATE = 44100

    private val _state = MutableStateFlow<RecState>(RecState.Idle)
    val state: StateFlow<RecState> = _state

    fun set(s: RecState) {
        // Узнанный трек сразу попадает в историю распознавания.
        if (s is RecState.Found) RecognitionHistory.add(s.result)
        _state.value = s
    }
    fun reset() { _state.value = RecState.Idle }

    /** Сколько окон распознавания сейчас на экране (RESUMED): если есть, результат виден и без уведомления. */
    @Volatile var uiShown: Int = 0

    /** Главное окно приложения на экране (между onStart и onStop). */
    @Volatile var appForeground: Boolean = false

    /** Идёт запись или поиск — повторно запускать нельзя. */
    val busy: Boolean get() = _state.value.let { it is RecState.Listening || it is RecState.Searching }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Записывает [seconds] секунд звука и возвращает WAV (PCM 16 бит, моно). null — если звука нет совсем. */
    @SuppressLint("MissingPermission")
    suspend fun record(source: RecSource, projection: MediaProjection?, seconds: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val builder = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minBuf, RATE) * 2)
            if (source == RecSource.Device) {
                require(Build.VERSION.SDK_INT >= 29 && projection != null) { "Запись звука устройства нужна Android 10 или новее" }
                val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    // Своё приложение не слушаем: иначе «распознаем» собственный плеер.
                    .excludeUid(Process.myUid())
                    .build()
                builder.setAudioPlaybackCaptureConfig(cfg)
            } else {
                builder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            val rec = builder.build()
            check(rec.state == AudioRecord.STATE_INITIALIZED) { "Не удалось начать запись звука" }
            val total = RATE * seconds
            val pcm = ShortArray(total)
            var filled = 0
            var peak = 0
            try {
                rec.startRecording()
                var lastSecond = -1
                while (filled < total && coroutineContext.isActive) {
                    val n = rec.read(pcm, filled, minOf(4096, total - filled))
                    if (n <= 0) { if (n < 0) error("Ошибка записи звука ($n)"); continue }
                    for (i in filled until filled + n) { val a = kotlin.math.abs(pcm[i].toInt()); if (a > peak) peak = a }
                    filled += n
                    val left = seconds - filled / RATE
                    if (left != lastSecond) {
                        lastSecond = left
                        _state.value = RecState.Listening(source, left)
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
            if (!coroutineContext.isActive) return@withContext null
            // Почти тишина — отправлять нечего.
            if (peak < 150) null else toWav(pcm, filled)
        }

    private fun toWav(pcm: ShortArray, samples: Int): ByteArray {
        val dataLen = samples * 2
        val out = ByteArrayOutputStream(44 + dataLen)
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + dataLen); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(RATE); h.putInt(RATE * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(dataLen)
        out.write(h.array())
        val body = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) body.putShort(pcm[i])
        out.write(body.array())
        return out.toByteArray()
    }

    /** Определяет трек по записи: сначала Shazam (без ключа), затем AudD, если указан ключ. */
    suspend fun identify(wav: ByteArray): RecState = withContext(Dispatchers.IO) {
        val shazam = identifyShazam(wav)
        if (shazam is RecState.Found) return@withContext shazam
        if (RecognitionSettings.auddToken.isNotBlank()) {
            val audd = identifyAudd(wav)
            if (audd is RecState.Found) return@withContext audd
        }
        shazam
    }

    private fun wavToPcm(wav: ByteArray): ShortArray {
        val n = (wav.size - 44) / 2
        val bb = ByteBuffer.wrap(wav, 44, n * 2).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(n) { bb.short }
    }

    /** Отпечаток считается на устройстве, на сервер уходит только он (несколько килобайт), а не сама запись. */
    private fun identifyShazam(wav: ByteArray): RecState {
        return try {
            val pcm = ShazamSignature.resampleTo16k(wavToPcm(wav), RATE)
            // Пробуем отпечаток с начала записи, а если не узнали — с середины.
            val offsets = listOf(0, pcm.size / 3)
            var hadResponse = false
            for (off in offsets) {
                val sig = ShazamSignature.generate(pcm, off) ?: continue
                val found = askShazam(sig)
                hadResponse = true
                if (found != null) return RecState.Found(found)
            }
            if (hadResponse) RecState.NotFound else RecState.Error("Не удалось разобрать звук: слишком тихо или нет музыки")
        } catch (e: java.io.IOException) {
            RecState.Error("Нет связи с сервером распознавания")
        } catch (e: Exception) {
            RecState.Error(e.message ?: "Ошибка распознавания")
        }
    }

    private fun askShazam(sig: ShazamSignature.Result): RecResult? =
        http.newCall(shazamRequest(sig)).execute().use { resp -> parseShazam(resp) }

    /** То же, но запрос отменяется вместе с корутиной (кнопка «Отмена» не ждёт ответа сервера). */
    private suspend fun askShazamLive(sig: ShazamSignature.Result): RecResult? =
        http.newCall(shazamRequest(sig)).await().use { resp -> parseShazam(resp) }

    private fun shazamRequest(sig: ShazamSignature.Result): Request {
        val uri = "data:audio/vnd.shazam.sig;base64," +
            android.util.Base64.encodeToString(ShazamSignature.encode(sig), android.util.Base64.NO_WRAP)
        val json = JSONObject()
            .put("timezone", java.util.TimeZone.getDefault().id)
            .put("signature", JSONObject().put("uri", uri).put("samplems", sig.sampleMs))
            .put("timestamp", System.currentTimeMillis())
            .put("context", JSONObject())
            .put("geolocation", JSONObject().put("altitude", 300).put("latitude", 45).put("longitude", 2))
        val id1 = java.util.UUID.randomUUID().toString().uppercase()
        val id2 = java.util.UUID.randomUUID().toString().uppercase()
        val url = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$id1/$id2" +
            "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true" +
            "&hubv5minorversion=v5.1&hidelb=true&video=v3"
        return Request.Builder().url(url)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 13; Pixel 7 Build/TQ3A.230901.001)")
            .header("Content-Language", "en_US")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseShazam(resp: Response): RecResult? {
        if (resp.code == 429) error("Shazam временно ограничил запросы, попробуйте чуть позже")
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful || text.isBlank()) error("Сервер Shazam ответил ошибкой (${resp.code})")
        val root = JSONObject(text)
        if ((root.optJSONArray("matches")?.length() ?: 0) == 0) return null
        val track = root.optJSONObject("track") ?: return null
        val title = track.optString("title")
        if (title.isBlank()) return null
        val images = track.optJSONObject("images")
        var album: String? = null
        val sections = track.optJSONArray("sections")
        if (sections != null) {
            loop@ for (i in 0 until sections.length()) {
                val meta = sections.optJSONObject(i)?.optJSONArray("metadata") ?: continue
                for (j in 0 until meta.length()) {
                    val m = meta.optJSONObject(j) ?: continue
                    if (m.optString("title") == "Album") { album = m.optString("text").takeIf { it.isNotBlank() }; break@loop }
                }
            }
        }
        return RecResult(
            title = title,
            artist = track.optString("subtitle"),
            album = album,
            cover = images?.optString("coverarthq")?.takeIf { it.isNotBlank() }
                ?: images?.optString("coverart")?.takeIf { it.isNotBlank() },
        )
    }

    /** Ждёт ответа, не блокируя поток; при отмене корутины отменяет и сам запрос. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    // ---------- Мгновенное распознавание ----------

    /** Итог одной попытки: узнали, не узнали или не получилось спросить сервер. */
    private sealed interface Attempt {
        data class Hit(val result: RecResult) : Attempt
        data object Miss : Attempt
        data class Fail(val message: String) : Attempt
    }

    /** До первой попытки накапливаем столько звука, секунд. */
    private const val FIRST_TRY_SEC = 3.5

    /** Новая попытка не раньше, чем набралось столько нового звука, секунд. */
    private const val STEP_SEC = 2.0

    /** Попытка смотрит на последние столько секунд записи. */
    private const val WINDOW_SEC = 5.0

    /** Тише этого пика звука считаем тишиной. */
    private const val MIN_PEAK = 150

    /** Одна проверка: отпечаток по свежему куску записи и запрос в Shazam. */
    private suspend fun attempt(window: ShortArray): Attempt = try {
        val pcm = ShazamSignature.resampleTo16k(window, RATE)
        val sig = ShazamSignature.generate(pcm, 0)
        if (sig == null) Attempt.Miss
        else askShazamLive(sig)?.let { Attempt.Hit(it) } ?: Attempt.Miss
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        Attempt.Fail("Нет связи с сервером распознавания")
    } catch (e: Exception) {
        Attempt.Fail(e.message ?: "Ошибка распознавания")
    }

    /**
     * Слушает до [seconds] секунд и отдаёт результат сразу, как только трек узнан: пока идёт запись, каждые пару секунд
     * свежий фрагмент уходит на проверку (параллельно, запись не прерывается). Если за всё время ничего не узнали,
     * то только по окончании таймера возвращает [RecState.NotFound].
     */
    @SuppressLint("MissingPermission")
    suspend fun listen(source: RecSource, projection: MediaProjection?, seconds: Int): RecState =
        withContext(Dispatchers.IO) {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val builder = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minBuf, RATE) * 2)
            if (source == RecSource.Device) {
                require(Build.VERSION.SDK_INT >= 29 && projection != null) { "Запись звука устройства нужна Android 10 или новее" }
                val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    // Своё приложение не слушаем: иначе «распознаем» собственный плеер.
                    .excludeUid(Process.myUid())
                    .build()
                builder.setAudioPlaybackCaptureConfig(cfg)
            } else {
                builder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            val rec = builder.build()
            check(rec.state == AudioRecord.STATE_INITIALIZED) { "Не удалось начать запись звука" }

            val total = RATE * seconds
            val pcm = ShortArray(total)
            var filled = 0
            var peak = 0
            var hit: RecResult? = null
            var tries = 0
            var fails = 0
            var lastFail = ""
            try {
                rec.startRecording()
                coroutineScope<Unit> {
                    var pending: Deferred<Attempt>? = null
                    var lastLaunch = -RATE * 100
                    var left = seconds

                    fun collect(a: Attempt) {
                        when (a) {
                            is Attempt.Hit -> hit = a.result
                            is Attempt.Miss -> {}
                            is Attempt.Fail -> { fails++; lastFail = a.message }
                        }
                    }

                    fun launchTry(upTo: Int): Deferred<Attempt> {
                        val from = maxOf(0, upTo - (WINDOW_SEC * RATE).toInt())
                        val window = pcm.copyOfRange(from, upTo)
                        lastLaunch = upTo
                        tries++
                        val d = async { attempt(window) }
                        pending = d
                        _state.value = RecState.Listening(source, left, matching = true)
                        return d
                    }

                    while (filled < total && isActive && hit == null) {
                        val n = rec.read(pcm, filled, minOf(4096, total - filled))
                        if (n <= 0) { if (n < 0) error("Ошибка записи звука ($n)"); continue }
                        for (i in filled until filled + n) { val a = kotlin.math.abs(pcm[i].toInt()); if (a > peak) peak = a }
                        filled += n
                        val nowLeft = seconds - filled / RATE
                        // Проверка закончилась: узнали — выходим сразу, не дожидаясь конца таймера.
                        val p = pending
                        if (p != null && p.isCompleted) {
                            pending = null
                            collect(p.await())
                            _state.value = RecState.Listening(source, nowLeft, matching = false)
                        }
                        if (hit != null) break
                        if (nowLeft != left) {
                            left = nowLeft
                            _state.value = RecState.Listening(source, left, matching = pending != null)
                        }
                        if (pending == null && peak >= MIN_PEAK &&
                            filled >= (FIRST_TRY_SEC * RATE).toInt() && filled - lastLaunch >= (STEP_SEC * RATE).toInt()
                        ) launchTry(filled)
                    }

                    // Таймер вышел, а трек ещё не узнан: дожидаемся начатой проверки и делаем последнюю по самому свежему звуку.
                    if (hit == null && isActive) {
                        pending?.let { d ->
                            _state.value = RecState.Searching(source)
                            collect(d.await())
                            pending = null
                        }
                        if (hit == null && isActive && peak >= MIN_PEAK && filled - lastLaunch >= RATE) {
                            _state.value = RecState.Searching(source)
                            collect(launchTry(filled).await())
                            pending = null
                        }
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
            ensureActive()
            val found = hit
            when {
                found != null -> RecState.Found(found)
                peak < MIN_PEAK -> RecState.Error(
                    if (source == RecSource.Device) "Не слышно звука. Включите музыку в другом приложении (некоторые приложения запрещают запись звука)"
                    else "Не слышно звука. Поднесите телефон ближе к источнику",
                )
                else -> {
                    // Запасной вариант: AudD по всей записи (если указан ключ).
                    var audd: RecState? = null
                    if (RecognitionSettings.auddToken.isNotBlank()) {
                        _state.value = RecState.Searching(source)
                        audd = identifyAudd(toWav(pcm, filled))
                    }
                    when {
                        audd is RecState.Found -> audd
                        tries > 0 && fails == tries -> RecState.Error(lastFail)
                        else -> RecState.NotFound
                    }
                }
            }
        }

    /** Обложка для уведомления: с коротким таймаутом; не получилось — уведомление придёт без неё. */
    suspend fun fetchCover(url: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val quick = http.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
            quick.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.byteStream()?.let { android.graphics.BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    /** Запасной вариант: AudD, если пользователь указал свой ключ. */
    private fun identifyAudd(wav: ByteArray): RecState {
        val token = RecognitionSettings.auddToken
        return try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("api_token", token)
                .addFormDataPart("return", "apple_music,spotify")
                .addFormDataPart("file", "clip.wav", wav.toRequestBody("audio/wav".toMediaType()))
                .build()
            val req = Request.Builder().url("https://api.audd.io/").post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) RecState.Error("Пустой ответ сервера (${resp.code})") else parse(text)
            }
        } catch (e: java.io.IOException) {
            RecState.Error("Нет связи с сервером распознавания")
        } catch (e: Exception) {
            RecState.Error(e.message ?: "Ошибка распознавания")
        }
    }

    private fun parse(text: String): RecState {
        val json = JSONObject(text)
        if (json.optString("status") != "success") {
            val err = json.optJSONObject("error")
            val code = err?.optInt("error_code") ?: 0
            return RecState.Error(
                when (code) {
                    900, 901 -> "Ключ AudD не подошёл или закончился лимит запросов"
                    else -> err?.optString("error_message")?.takeIf { it.isNotBlank() } ?: "Ошибка распознавания"
                },
            )
        }
        val r = json.optJSONObject("result") ?: return RecState.NotFound
        val title = r.optString("title")
        if (title.isBlank()) return RecState.NotFound
        val cover = r.optJSONObject("apple_music")?.optJSONObject("artwork")?.optString("url")
            ?.replace("{w}", "600")?.replace("{h}", "600")?.takeIf { it.isNotBlank() }
            ?: r.optJSONObject("spotify")?.optJSONObject("album")?.optJSONArray("images")
                ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
        return RecState.Found(
            RecResult(
                title = title,
                artist = r.optString("artist"),
                album = r.optString("album").takeIf { it.isNotBlank() },
                cover = cover,
            ),
        )
    }
}
