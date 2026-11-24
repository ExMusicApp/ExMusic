package app.ymusic.m3e.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Устройство из списка «Подключить» Яндекс Музыки: Яндекс Станция, телевизор, другой телефон и т. п. */
data class ConnectDevice(
    val id: String,
    val title: String,
    /** SMART_SPEAKER, ANDROID, IOS, WEB, WEB_TV, ANDROID_TV, APPLE_TV … */
    val type: String,
    val appName: String,
    /** Устройство умеет проигрывать музыку (колонки, ТВ, приложения). */
    val canPlay: Boolean,
    val offline: Boolean,
    /** Громкость 0..1 (−1 — неизвестна). */
    val volume: Double,
    /** Число делений громкости (0 — громкость удалённо менять нельзя). */
    val volumeSteps: Int,
) {
    val isSpeaker: Boolean get() = type == "SMART_SPEAKER"
    val isTv: Boolean get() = type == "WEB_TV" || type == "ANDROID_TV" || type == "APPLE_TV"

    /** Человекочитаемое описание типа устройства. */
    val kindLabel: String
        get() = when (type) {
            "SMART_SPEAKER" -> "Умная колонка"
            "WEB_TV", "ANDROID_TV", "APPLE_TV" -> "Телевизор"
            "ANDROID", "IOS" -> "Телефон"
            "WEB" -> "Браузер"
            else -> "Устройство"
        }
}

data class ConnectState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val devices: List<ConnectDevice> = emptyList(),
    /** Устройство, которое сейчас активно в аккаунте (на любом клиенте). */
    val activeId: String? = null,
    /** Устройство, на котором музыку запустили мы. */
    val remoteId: String? = null,
    val error: String? = null,
    val paused: Boolean = true,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    /** id трека, который сейчас играет на удалённом устройстве. */
    val currentTrackId: String? = null,
    val currentIndex: Int = -1,
    val receivedAtMs: Long = 0,
    /** Для управления колонкой по Wi-Fi (Glagol) нужен вход в Яндекс через WebView. */
    val stationLoginRequired: Boolean = false,
)

/**
 * Управление колонками и другими устройствами Яндекса через Ynison — тот же протокол синхронизации, которым
 * пользуется официальное приложение Яндекс Музыки (кнопка «Подключить»). Телефон здесь — пульт: очередь и
 * позиция отправляются на выбранное устройство, оно само берёт поток с серверов Яндекса и играет.
 *
 * Протокол неофициальный (JSON поверх WebSocket); если Яндекс его изменит, поломается только этот файл.
 */
class YandexConnect(
    ctx: Context,
    base: OkHttpClient,
    private val tokenProvider: () -> String?,
) {
    private val http = base.newBuilder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _state = MutableStateFlow(ConnectState())
    val state: StateFlow<ConnectState> = _state

    // ---- Колонки по Wi-Fi (Glagol, порт из PulseSync): работает рядом с Ynison, а не вместо него ----
    private val stationPrefix = "glagol:"
    private var ynisonDevices: List<ConnectDevice> = emptyList()
    private var stationDevices: List<ConnectDevice> = emptyList()
    /** platform найденных по Wi-Fi колонок (id без префикса → platform): по нему подбирается название модели, если настоящего имени нет. */
    private var stationPlatforms: Map<String, String?> = emptyMap()

    /** Колонка по Wi-Fi не ответила / пропала: слушатель должен забрать музыку обратно на телефон (AppViewModel.releaseConnect). */
    @Volatile var onRemoteLost: ((String) -> Unit)? = null

    private val station = YandexStation(ctx, base, tokenProvider) { msg -> stationLost(msg) }

    init {
        scope.launch { station.state.collect { onStation(it) } }
    }

    /** Название модели колонки по platform из Glagol (если известно). */
    private fun speakerModelName(platform: String?): String? = when (platform?.trim()?.lowercase()) {
        "yandexstation" -> "Яндекс Станция"
        "yandexstation_2" -> "Яндекс Станция Макс"
        "yandexmini" -> "Яндекс Станция Мини"
        "yandexmini_2" -> "Яндекс Станция Мини 2"
        "yandexmicro" -> "Яндекс Станция Лайт"
        "yandexmidi" -> "Яндекс Станция 2"
        "yandexmodule" -> "Яндекс Модуль"
        "yandexmodule_2" -> "Яндекс Модуль 2"
        else -> null
    }

    private fun isStationId(id: String?): Boolean = id != null && id.startsWith(stationPrefix)

    private fun stationRemote(): Boolean = isStationId(_state.value.remoteId)

    private fun normTitle(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Колонка из Ynison, которая уже найдена по Wi-Fi, показывается один раз (локальной записью). */
    private fun mergeDevices(): List<ConnectDevice> {
        val st = stationDevices
        if (st.isEmpty()) return ynisonDevices
        val ids = st.map { it.id.removePrefix(stationPrefix).lowercase() }.toSet()
        val names = st.map { normTitle(it.title) }.toSet()
        val rest = ynisonDevices.filterNot { it.isSpeaker && (it.id.lowercase() in ids || normTitle(it.title) in names) }
        // Если имя колонки по Wi-Fi служебное (YandexIOReceiver…), берём настоящее из списка устройств Ynison (тот же device_id).
        val usedYn = HashSet<String>()
        val genericCount = st.count { isGenericSpeakerName(it.title) }
        val named = st.map { d ->
            if (!isGenericSpeakerName(d.title)) return@map d
            val rawId = d.id.removePrefix(stationPrefix)
            val byId = ynisonDevices.firstOrNull {
                it.isSpeaker && it.id.equals(rawId, ignoreCase = true) && !isGenericSpeakerName(it.title)
            }
            // Один безымянный колонка и ровно одна колонка с именем в Ynison — это она (id в Ynison мог записаться иначе).
            val y = byId ?: ynisonDevices.filter { it.isSpeaker && !isGenericSpeakerName(it.title) && it.id !in usedYn }
                .takeIf { genericCount == 1 && it.size == 1 }?.first()
            if (y != null) { usedYn += y.id; return@map d.copy(title = y.title) }
            // Настоящего имени нет нигде — хотя бы модель по platform («Яндекс Станция Мини»), а не безликое «Яндекс Станция».
            val model = speakerModelName(stationPlatforms[rawId])
            if (model != null) d.copy(title = model) else d
        }
        return named + rest
    }

    private fun onStation(ss: StationState) {
        synchronized(lock) {
            stationPlatforms = ss.speakers.associate { it.id to it.platform }
            stationDevices = ss.speakers.map {
                ConnectDevice(
                    id = stationPrefix + it.id,
                    title = it.name,
                    type = "SMART_SPEAKER",
                    appName = "Яндекс Станция",
                    canPlay = true,
                    offline = false,
                    volume = if (ss.activeId == it.id) ss.volume else -1.0,
                    volumeSteps = 20,
                )
            }
            _state.update { s ->
                var n = s.copy(devices = mergeDevices(), stationLoginRequired = ss.loginRequired)
                val activeId = ss.activeId?.let { stationPrefix + it }
                if (activeId != null && s.remoteId == activeId) {
                    n = n.copy(
                        activeId = activeId,
                        paused = !ss.playing,
                        progressMs = ss.progressMs,
                        durationMs = ss.durationMs,
                        currentTrackId = ss.trackId ?: n.currentTrackId,
                        currentIndex = ss.index,
                        receivedAtMs = ss.receivedAtMs,
                    )
                }
                n
            }
        }
    }

    private fun stationLost(msg: String) {
        _state.update { it.copy(error = msg) }
        val cb = onRemoteLost
        if (cb != null) cb(msg) else _state.update { it.copy(remoteId = null, paused = true) }
    }

    /** Вход в Яндекс через WebView завершён — пересчитать токены для колонок. */
    fun stationLoginDone() = station.afterLogin()

    /** Повторный поиск колонок в Wi-Fi. */
    fun stationRescan() = station.rescan()

    /** Постоянный id этого приложения как «устройства» в аккаунте. */
    private val deviceId: String = ctx.getSharedPreferences("yandex_connect", Context.MODE_PRIVATE).let { p ->
        p.getString("device_id", null) ?: run {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val id = (1..16).map { chars.random() }.joinToString("")
            p.edit().putString("device_id", id).apply()
            id
        }
    }

    @Volatile private var socket: WebSocket? = null
    private var session: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    /** Последнее полное состояние плеера с сервера — из него собираются команды «следующий/предыдущий». */
    private var lastPlayerState: JSONObject? = null
    private var verQueue = 0L
    private var verStatus = 0L
    private var startedAtMs = 0L

    // ---------- Подключение ----------

    /** Открывает сессию, если она ещё не открыта. Список устройств приходит сам и потом обновляется на лету. */
    fun ensureConnected() {
        station.start()
        val s = _state.value
        if (s.connected || s.connecting) return
        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            _state.update { it.copy(error = "Нужен вход в аккаунт Яндекса") }
            return
        }
        _state.update { it.copy(connecting = true, error = null) }
        session?.cancel()
        session = scope.launch {
            try {
                open(token)
                reconnectAttempts = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message?.takeIf { it.isNotBlank() } ?: "Не удалось подключиться к устройствам Яндекса")
            }
        }
    }

    /** Закрывает соединение (если мы никем не управляем) — чтобы не держать сокет и не тратить батарею. */
    fun disconnect() {
        reconnectJob?.cancel()
        session?.cancel()
        socket?.close(1000, null)
        socket = null
        station.shutdown()
        synchronized(lock) {
            lastPlayerState = null
            ynisonDevices = emptyList()
            stationDevices = emptyList()
            _state.update { ConnectState() }
        }
    }

    private suspend fun open(token: String) {
        var last: Exception? = null
        // Сначала так, как это делает веб-клиент; если сервер отклонит — повторяем без «теневого» флага.
        for (shadow in listOf(true, false)) {
            try {
                val (host, ticket) = redirect(token)
                openState(token, host, ticket, shadow)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Не удалось подключиться к устройствам Яндекса")
    }

    private fun protoJson(ticket: String?): String {
        val info = JSONObject().put("app_name", "Chrome").put("type", 1)
        val o = JSONObject().put("Ynison-Device-Id", deviceId).put("Ynison-Device-Info", info.toString())
        if (ticket != null) o.put("Ynison-Redirect-Ticket", ticket)
        return o.toString()
    }

    private fun wsRequest(url: String, token: String, ticket: String?): Request =
        Request.Builder().url(url)
            .header("Sec-WebSocket-Protocol", "Bearer, v2, ${protoJson(ticket)}")
            .header("Origin", "http://music.yandex.ru")
            .header("Authorization", "OAuth $token")
            .build()

    /** Шаг 1: редиректор говорит, к какому серверу Ynison подключаться, и выдаёт одноразовый билет. */
    private suspend fun redirect(token: String): Pair<String, String> {
        val got = CompletableDeferred<JSONObject>()
        val ws = http.newWebSocket(
            wsRequest("wss://ynison.music.yandex.ru/redirector.YnisonRedirectService/GetRedirectToYnison", token, null),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { JSONObject(text) }
                        .onSuccess { got.complete(it) }
                        .onFailure { got.completeExceptionally(it) }
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    got.completeExceptionally(IOException("Сервер Яндекса закрыл соединение ($code)"))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code
                    got.completeExceptionally(
                        if (code == 401 || code == 403) IOException("Яндекс не принял токен (HTTP $code). Войдите в аккаунт заново")
                        else IOException(t.message ?: "Нет связи с сервером Яндекса"),
                    )
                }
            },
        )
        try {
            val j = withTimeout(12_000) { got.await() }
            val host = j.optString("host")
            val ticket = j.optString("redirect_ticket")
            if (host.isBlank() || ticket.isBlank()) throw IOException("Неожиданный ответ сервера Яндекса")
            return host to ticket
        } catch (e: Exception) {
            ws.cancel()
            throw e
        }
    }

    /** Шаг 2: основное соединение. Сервер сразу присылает полное состояние: очередь, устройства, активное устройство. */
    private suspend fun openState(token: String, host: String, ticket: String, shadow: Boolean) {
        val first = CompletableDeferred<Unit>()
        val ws = http.newWebSocket(
            wsRequest("wss://$host/ynison_state.YnisonStateService/PutYnisonState", token, ticket),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(fullState(shadow).toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handle(text)
                    first.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    first.completeExceptionally(IOException("Соединение закрыто ($code)"))
                    dropped(webSocket, "Соединение с устройствами Яндекса закрыто")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    first.completeExceptionally(IOException(t.message ?: "Обрыв связи"))
                    dropped(webSocket, "Соединение с устройствами Яндекса прервалось")
                }
            },
        )
        socket = ws
        try {
            withTimeout(12_000) { first.await() }
        } catch (e: Exception) {
            ws.cancel()
            if (socket === ws) socket = null
            throw e
        }
        _state.update { it.copy(connecting = false, connected = true, error = null) }
    }

    /** Сокет упал уже после успешного подключения — помечаем и, если играем на колонке, пробуем переподключиться. */
    private fun dropped(ws: WebSocket, msg: String) {
        if (socket !== ws) return
        socket = null
        if (!_state.value.connected) return // обрыв на этапе подключения обрабатывает open()
        ynisonDevices = emptyList()
        _state.update { it.copy(connected = false, connecting = false, error = msg, devices = mergeDevices()) }
        if (_state.value.remoteId != null && !stationRemote() && reconnectAttempts < 5) {
            reconnectAttempts++
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(2_000L * reconnectAttempts)
                ensureConnected()
            }
        }
    }

    private fun fail(msg: String) {
        socket?.cancel()
        socket = null
        _state.update { it.copy(connecting = false, connected = false, error = msg) }
    }

    // ---------- Сообщения ----------

    private fun ver(v: Long): JSONObject =
        JSONObject().put("device_id", deviceId).put("version", v.toString()).put("timestamp_ms", "0")

    private fun nextQueueVer(): Long {
        verQueue = if (verQueue <= 0L) 9021243204784341000L else verQueue
        verQueue += 1
        return verQueue
    }

    private fun nextStatusVer(): Long {
        verStatus = if (verStatus <= 0L) 9021243204784341000L else verStatus
        verStatus += 1
        return verStatus
    }

    private fun fullState(shadow: Boolean): JSONObject {
        val v = 9021243204784341000L
        val queue = JSONObject()
            .put("current_playable_index", -1)
            .put("entity_id", "")
            .put("entity_type", "VARIOUS")
            .put("playable_list", JSONArray())
            .put("options", JSONObject().put("repeat_mode", "NONE"))
            .put("entity_context", "BASED_ON_ENTITY_BY_DEFAULT")
            .put("version", ver(v))
            .put("from_optional", "")
        val status = JSONObject()
            .put("duration_ms", 0)
            .put("paused", true)
            .put("playback_speed", 1)
            .put("progress_ms", 0)
            .put("version", ver(v))
        val device = JSONObject()
            .put(
                "capabilities",
                JSONObject().put("can_be_player", false).put("can_be_remote_controller", true).put("volume_granularity", 0),
            )
            .put(
                "info",
                JSONObject().put("device_id", deviceId).put("title", "ExMusic").put("type", "WEB").put("app_name", "Chrome"),
            )
            .put("volume_info", JSONObject().put("volume", 0))
        if (shadow) device.put("is_shadow", true)
        return JSONObject()
            .put(
                "update_full_state",
                JSONObject()
                    .put("player_state", JSONObject().put("player_queue", queue).put("status", status))
                    .put("device", device)
                    .put("is_currently_active", false),
            )
            .put("rid", UUID.randomUUID().toString())
            .put("player_action_timestamp_ms", 0)
            .put("activity_interception_type", "DO_NOT_INTERCEPT_BY_DEFAULT")
    }

    /** int64 в JSON приходит то числом, то строкой. */
    private fun lng(o: JSONObject?, key: String): Long {
        val v = o?.opt(key) ?: return 0L
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun typeName(v: Any?): String = when (v) {
        is Number -> when (v.toInt()) {
            1 -> "WEB"
            2 -> "ANDROID"
            3 -> "IOS"
            4 -> "SMART_SPEAKER"
            5 -> "WEB_TV"
            6 -> "ANDROID_TV"
            7 -> "APPLE_TV"
            else -> "UNSPECIFIED"
        }
        is String -> v
        else -> "UNSPECIFIED"
    }

    private fun handle(text: String) {
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            var devices: List<ConnectDevice>? = null
            j.optJSONArray("devices")?.let { arr ->
                val list = ArrayList<ConnectDevice>()
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    val info = d.optJSONObject("info") ?: continue
                    val id = info.optString("device_id")
                    if (id.isBlank() || id == deviceId) continue
                    val caps = d.optJSONObject("capabilities")
                    list += ConnectDevice(
                        id = id,
                        title = info.optString("title").let { t -> if (t.isBlank()) "Устройство" else if (isGenericSpeakerName(t) && typeName(info.opt("type")) == "SMART_SPEAKER") "Яндекс Станция" else t },
                        type = typeName(info.opt("type")),
                        appName = info.optString("app_name"),
                        canPlay = caps?.optBoolean("can_be_player", false) ?: false,
                        offline = d.optBoolean("is_offline", false),
                        volume = d.optJSONObject("volume_info")?.optDouble("volume", -1.0) ?: -1.0,
                        volumeSteps = caps?.optInt("volume_granularity", 0) ?: 0,
                    )
                }
                ynisonDevices = list
                devices = mergeDevices()
            }
            val activeId = if (j.has("active_device_id_optional")) j.optString("active_device_id_optional").ifBlank { null } else null
            val hasActive = j.has("active_device_id_optional")

            var paused: Boolean? = null
            var progress: Long? = null
            var duration: Long? = null
            var trackId: String? = null
            var index: Int? = null
            j.optJSONObject("player_state")?.let { ps ->
                lastPlayerState = ps
                val q = ps.optJSONObject("player_queue")
                val st = ps.optJSONObject("status")
                if (q != null) {
                    verQueue = maxOf(verQueue, lng(q.optJSONObject("version"), "version"))
                    val idx = q.optInt("current_playable_index", -1)
                    index = idx
                    val list = q.optJSONArray("playable_list")
                    if (list != null && idx in 0 until list.length()) {
                        trackId = list.optJSONObject(idx)?.optString("playable_id")?.ifBlank { null }
                    }
                }
                if (st != null) {
                    verStatus = maxOf(verStatus, lng(st.optJSONObject("version"), "version"))
                    paused = st.optBoolean("paused", true)
                    progress = lng(st, "progress_ms")
                    duration = lng(st, "duration_ms")
                }
            }

            _state.update { s ->
                s.copy(
                    devices = devices ?: s.devices,
                    activeId = if (hasActive) activeId else s.activeId,
                    paused = paused ?: s.paused,
                    progressMs = progress ?: s.progressMs,
                    durationMs = duration ?: s.durationMs,
                    currentTrackId = trackId ?: s.currentTrackId,
                    currentIndex = index ?: s.currentIndex,
                    receivedAtMs = if (progress != null || paused != null) now else s.receivedAtMs,
                )
            }
        }
    }

    private fun send(body: JSONObject): Boolean {
        val ws = socket ?: return false
        body.put("rid", UUID.randomUUID().toString())
        body.put("player_action_timestamp_ms", System.currentTimeMillis().toString())
        body.put("activity_interception_type", "DO_NOT_INTERCEPT_BY_DEFAULT")
        return ws.send(body.toString())
    }

    // ---------- Управление ----------

    /** Музыка играет на удалённом устройстве, которое мы выбрали (и его никто не перехватил). */
    fun isRemoteActive(): Boolean {
        val s = _state.value
        val id = s.remoteId ?: return false
        if (isStationId(id)) return true
        if (s.activeId == id) return true
        // Сразу после выбора сервер ещё не подтвердил смену активного устройства — даём ему несколько секунд.
        return System.currentTimeMillis() - startedAtMs < 8_000
    }

    /** Позиция на удалённом устройстве с поправкой на прошедшее время (сервер шлёт её только при изменениях). */
    fun estimatedProgress(): Long {
        val s = _state.value
        if (s.paused) return s.progressMs
        val p = s.progressMs + (System.currentTimeMillis() - s.receivedAtMs)
        return if (s.durationMs > 0) p.coerceAtMost(s.durationMs) else p
    }

    private fun playable(t: Track): JSONObject {
        val o = JSONObject()
            .put("playable_id", t.id)
            .put("playable_type", "TRACK")
            .put("title", t.title)
            .put("from", "mobile-ex-music")
        if (!t.albumId.isNullOrBlank()) o.put("album_id_optional", t.albumId)
        t.coverUri?.takeIf { it.isNotBlank() }?.let { o.put("cover_url_optional", it) }
        return o
    }

    /**
     * Запускает [tracks] с позиции [index] / [positionMs] на устройстве [device].
     * Файлы с телефона колонка проиграть не может — они пропускаются. Возвращает false, если играть нечего или нет связи.
     */
    fun playOn(device: ConnectDevice, tracks: List<Track>, index: Int, positionMs: Long, repeat: String = "NONE"): Boolean {
        val cur = tracks.getOrNull(index) ?: return false
        if (cur.localUri != null) {
            _state.update { it.copy(error = "Файлы с телефона нельзя включить на колонке — выберите трек из Яндекс Музыки") }
            return false
        }
        if (isStationId(device.id)) return playOnStation(device, tracks, index, positionMs, repeat)
        // Переключаемся с колонки по Wi-Fi на устройство Ynison: колонку ставим на паузу.
        if (stationRemote()) {
            station.release()
            _state.update { it.copy(remoteId = null) }
        }
        // Окно вокруг текущего трека: JSON не должен разрастаться на тысячи элементов.
        val from = (index - 20).coerceAtLeast(0)
        val to = minOf(tracks.size, from + 250)
        val arr = JSONArray()
        var newIndex = 0
        for (i in from until to) {
            val t = tracks[i]
            if (t.localUri != null) continue
            if (i == index) newIndex = arr.length()
            arr.put(playable(t))
        }
        if (socket == null) {
            ensureConnected()
            _state.update { it.copy(error = "Нет соединения с устройствами Яндекса, попробуйте ещё раз") }
            return false
        }
        synchronized(lock) {
            val queue = JSONObject()
                .put("current_playable_index", newIndex)
                .put("entity_id", "")
                .put("entity_type", "VARIOUS")
                .put("playable_list", arr)
                .put("options", JSONObject().put("repeat_mode", repeat))
                .put("entity_context", "BASED_ON_ENTITY_BY_DEFAULT")
                .put("version", ver(nextQueueVer()))
                .put("from_optional", "mobile-ex-music")
            val status = JSONObject()
                .put("duration_ms", cur.durationMs)
                .put("paused", false)
                .put("playback_speed", 1)
                .put("progress_ms", positionMs.coerceAtLeast(0))
                .put("version", ver(nextStatusVer()))
            val ps = JSONObject().put("player_queue", queue).put("status", status)
            startedAtMs = System.currentTimeMillis()
            _state.update {
                it.copy(
                    remoteId = device.id,
                    paused = false,
                    progressMs = positionMs.coerceAtLeast(0),
                    durationMs = cur.durationMs,
                    currentTrackId = cur.id,
                    currentIndex = newIndex,
                    receivedAtMs = startedAtMs,
                    error = null,
                )
            }
            lastPlayerState = JSONObject().put("player_queue", JSONObject(queue.toString())).put("status", JSONObject(status.toString()))
            val a = send(JSONObject().put("update_player_state", JSONObject().put("player_state", ps)))
            val b = send(JSONObject().put("update_active_device", JSONObject().put("device_id_optional", device.id)))
            if (!(a && b)) {
                _state.update { it.copy(remoteId = null, error = "Не удалось отправить команду устройству") }
                return false
            }
        }
        return true
    }

    /** Запуск на колонке по Wi-Fi: очередь ведёт телефон, колонка играет по одному треку (playMusic). */
    private fun playOnStation(device: ConnectDevice, tracks: List<Track>, index: Int, positionMs: Long, repeat: String): Boolean {
        val sp = station.speaker(device.id.removePrefix(stationPrefix))
        if (sp == null) {
            _state.update { it.copy(error = "Колонка «${device.title}» пропала из Wi-Fi сети, обновите список") }
            return false
        }
        val cur = tracks[index]
        val from = (index - 20).coerceAtLeast(0)
        val to = minOf(tracks.size, from + 250)
        val list = ArrayList<Track>()
        var newIndex = 0
        for (i in from until to) {
            val t = tracks[i]
            if (t.localUri != null) continue
            if (i == index) newIndex = list.size
            list += t
        }
        synchronized(lock) {
            // Если до этого играло устройство Ynison — ставим его на паузу и снимаем активность.
            val prev = _state.value
            if (prev.remoteId != null && !isStationId(prev.remoteId)) {
                runCatching {
                    send(statusUpdate(true, estimatedProgress(), prev.durationMs))
                    send(JSONObject().put("update_active_device", JSONObject()))
                }
            }
            startedAtMs = System.currentTimeMillis()
            _state.update {
                it.copy(
                    remoteId = device.id,
                    activeId = device.id,
                    paused = false,
                    progressMs = positionMs.coerceAtLeast(0),
                    durationMs = cur.durationMs,
                    currentTrackId = cur.id,
                    currentIndex = newIndex,
                    receivedAtMs = startedAtMs,
                    error = null,
                )
            }
        }
        station.play(sp, list, newIndex, positionMs, repeat)
        return true
    }

    private fun statusUpdate(paused: Boolean, progress: Long, duration: Long): JSONObject =
        JSONObject().put(
            "update_playing_status",
            JSONObject().put(
                "playing_status",
                JSONObject()
                    .put("duration_ms", duration)
                    .put("paused", paused)
                    .put("playback_speed", 1)
                    .put("progress_ms", progress.coerceAtLeast(0))
                    .put("version", ver(nextStatusVer())),
            ),
        )

    fun setPaused(paused: Boolean) {
        synchronized(lock) {
            val s = _state.value
            val p = estimatedProgress()
            if (isStationId(s.remoteId)) {
                if (paused) station.pause() else station.resume()
                _state.update { it.copy(paused = paused, progressMs = p, receivedAtMs = System.currentTimeMillis()) }
                return
            }
            if (!send(statusUpdate(paused, p, s.durationMs))) return
            _state.update { it.copy(paused = paused, progressMs = p, receivedAtMs = System.currentTimeMillis()) }
        }
    }

    fun togglePause() = setPaused(!_state.value.paused)

    fun seekTo(ms: Long) {
        synchronized(lock) {
            val s = _state.value
            if (isStationId(s.remoteId)) {
                station.seek(ms)
                _state.update { it.copy(progressMs = ms.coerceAtLeast(0), receivedAtMs = System.currentTimeMillis()) }
                return
            }
            if (!send(statusUpdate(s.paused, ms, s.durationMs))) return
            _state.update { it.copy(progressMs = ms.coerceAtLeast(0), receivedAtMs = System.currentTimeMillis()) }
        }
    }

    /** На [delta] треков вперёд (1) или назад (−1) в очереди колонки. */
    fun skip(delta: Int): Boolean = synchronized(lock) {
        if (stationRemote()) return@synchronized station.skip(delta)
        val ps = lastPlayerState?.let { JSONObject(it.toString()) } ?: return@synchronized false
        val q = ps.optJSONObject("player_queue") ?: return@synchronized false
        val list = q.optJSONArray("playable_list") ?: return@synchronized false
        val idx = q.optInt("current_playable_index", -1) + delta
        if (idx !in 0 until list.length()) return@synchronized false
        jumpTo(ps, q, idx)
    }

    /** Перейти к элементу очереди колонки по id трека (смена трека на телефоне — например, из шторки уведомлений). */
    fun skipToTrack(trackId: String): Boolean = synchronized(lock) {
        if (stationRemote()) return@synchronized station.skipToTrack(trackId)
        val ps = lastPlayerState?.let { JSONObject(it.toString()) } ?: return@synchronized false
        val q = ps.optJSONObject("player_queue") ?: return@synchronized false
        val list = q.optJSONArray("playable_list") ?: return@synchronized false
        val cur = q.optInt("current_playable_index", 0)
        var best = -1
        for (i in 0 until list.length()) {
            if (list.optJSONObject(i)?.optString("playable_id") == trackId) {
                if (best < 0 || kotlin.math.abs(i - cur) < kotlin.math.abs(best - cur)) best = i
            }
        }
        if (best < 0 || best == cur) return@synchronized false
        jumpTo(ps, q, best)
    }

    private fun jumpTo(ps: JSONObject, q: JSONObject, idx: Int): Boolean {
        q.put("current_playable_index", idx).put("version", ver(nextQueueVer()))
        val st = (ps.optJSONObject("status") ?: JSONObject())
            .put("progress_ms", 0)
            .put("duration_ms", 0)
            .put("paused", false)
            .put("version", ver(nextStatusVer()))
        ps.put("status", st)
        if (!send(JSONObject().put("update_player_state", JSONObject().put("player_state", ps)))) return false
        lastPlayerState = ps
        val id = q.optJSONArray("playable_list")?.optJSONObject(idx)?.optString("playable_id")
        _state.update {
            it.copy(
                paused = false, progressMs = 0, durationMs = 0,
                currentTrackId = id ?: it.currentTrackId, currentIndex = idx,
                receivedAtMs = System.currentTimeMillis(),
            )
        }
        return true
    }

    /** Громкость устройства 0..1 (округляется до делений, которые оно поддерживает). */
    fun setVolume(device: ConnectDevice, v: Double) {
        if (isStationId(device.id)) {
            station.setVolume(v)
            synchronized(lock) {
                _state.update { s -> s.copy(devices = s.devices.map { if (it.id == device.id) it.copy(volume = v.coerceIn(0.0, 1.0)) else it }) }
            }
            return
        }
        val steps = device.volumeSteps
        if (steps <= 0) return
        val q = (Math.round(v.coerceIn(0.0, 1.0) * steps).toDouble() / steps)
        synchronized(lock) {
            send(
                JSONObject().put(
                    "update_volume_info",
                    JSONObject()
                        .put("device_id", device.id)
                        .put("volume_info", JSONObject().put("volume", q).put("version", ver(nextStatusVer()))),
                ),
            )
            _state.update { s -> s.copy(devices = s.devices.map { if (it.id == device.id) it.copy(volume = q) else it }) }
        }
    }

    /**
     * Вернуть музыку на телефон: колонка ставится на паузу и перестаёт быть активной.
     * Возвращает (id трека, позиция, играло ли) на момент возврата, чтобы телефон продолжил с того же места.
     */
    fun release(): Triple<String?, Long, Boolean> = synchronized(lock) {
        val s = _state.value
        val pos = estimatedProgress()
        if (isStationId(s.remoteId)) {
            val wasPlaying = !s.paused
            val id = s.currentTrackId
            station.release()
            _state.update { it.copy(remoteId = null, activeId = null, paused = true) }
            return@synchronized Triple(id, pos, wasPlaying)
        }
        val wasPlaying = !s.paused
        val id = s.currentTrackId
        if (s.remoteId != null) {
            send(statusUpdate(true, pos, s.durationMs))
            send(JSONObject().put("update_active_device", JSONObject()))
        }
        _state.update { it.copy(remoteId = null, paused = true) }
        Triple(id, pos, wasPlaying)
    }

    /** Забыть ошибку после показа. */
    fun clearError() = _state.update { it.copy(error = null) }
}
