package app.ymusic.m3e.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/*
 * Система плагинов.
 *
 * Плагин — один файл `.exm`. Это обычный ZIP-архив, в котором лежат манифест `manifest.json` и Lua-файлы плагина
 * (плюс необязательная иконка). Приложение хранит плагин одним файлом (filesDir/plugins/<id>.exm) и читает из него
 * манифест и скрипты прямо из архива, ничего не распаковывая.
 *
 * Плагин состоит только из Lua-кода: contributes.lua = { "main": "main.lua" }. Скрипт запускается в песочнице LuaJ
 * (см. LuaPlugins.kt и PLUGINS_LUA.md): плеер, уведомления, хранилище, сеть, тема, волна — через API приложения.
 * Старый «декларативный» вид плагинов (только цвет/станции в manifest.json, без кода) убран: то же самое делается
 * Lua-скриптом (theme.set_accent, wave.add_station).
 */

/** Ошибка разбора плагина с текстом, который можно показать пользователю. */
class PluginException(message: String) : Exception(message)

/** Станция волны из плагина: [station] — идентификатор станции Яндекс Музыки (например, `genre:rock`). */
data class PluginStation(val title: String, val station: String, val subtitle: String = "")

/**
 * Поле настроек плагина. Плагин только ОПИСЫВАЕТ поле, а рисует и проверяет его приложение, поэтому код плагина в интерфейс не попадает.
 * Типы со значением: switch (Boolean), slider и number (Double, с min/max/step), text (String), choice (String из options),
 * color (String «#RRGGBB»). Без значения: button (кнопка), section (заголовок группы), info (поясняющий текст).
 * Поле можно показывать только при условии: [showIfKey] — ключ другого поля, [showIfEquals] — значение, при котором поле видно.
 */
data class PluginSetting(
    val key: String,
    val type: String,
    val title: String,
    val summary: String = "",
    val default: Any? = null,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val maxLength: Int = 100,
    /** Для choice: пары «значение → подпись». */
    val options: List<Pair<String, String>> = emptyList(),
    val showIfKey: String? = null,
    val showIfEquals: Any? = null,
) {
    /** У поля есть значение (его хранят и отдают скрипту); у кнопки, заголовка и текста — нет. */
    val hasValue: Boolean get() = type != "button" && type != "section" && type != "info"
}

/** Страница настроек, созданная самим Lua-скриптом (ui.settings_page). [menu] — показывать ли её пунктом в главных Настройках. */
class PluginUiEntry(val id: String, val title: String, val summary: String, val menu: Boolean, val transient: Boolean = false)

/** Кнопка диалога из Lua: [style] — default, primary или danger. */
class PluginDialogButton(val title: String, val style: String)

/**
 * Диалог, который открыл Lua-скрипт (ui.dialog / alert / confirm / prompt). [key] — номер диалога внутри плагина: по нему
 * ответ находит нужный Lua-колбэк. [inputValue] != null — в диалоге есть поле ввода.
 */
class PluginDialog(
    val key: Int, val pluginId: String, val pluginName: String, val title: String, val text: String,
    val buttons: List<PluginDialogButton>, val inputValue: String?, val inputHint: String, val inputMaxLength: Int,
    val cancelable: Boolean,
)

/** Нижняя модалка, открытая из Lua (ui.sheet / ui.open_page). [closing] — Lua попросил закрыть: шторка уедет с анимацией. */
data class PluginSheetReq(val pluginId: String, val entryId: String, val closing: Boolean = false)

/**
 * Один компонент страницы, которую Lua вернул из render(). Описание поля — [st] (тот же вид, что и у настроек из манифеста),
 * [value] — текущее значение (null у section/info/button), [index] — позиция в странице (по ней находится колбэк в Lua).
 */
class PluginUiNode(val index: Int, val st: PluginSetting, val value: Any?)

/** Свой пункт плагина в главных настройках приложения (contributes.settings_entry): открывает настройки плагина. */
data class PluginMenuEntry(val title: String, val summary: String = "")

/** Манифест плагина (manifest.json внутри .exm). */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val api: Int,
    /** Имя файла иконки внутри архива (null — иконки нет). */
    val icon: String?,
    /** Сколько файлов в архиве и сколько они занимают (в распакованном виде). */
    val fileCount: Int,
    val unpackedBytes: Long,
    /** Имя главного Lua-скрипта внутри архива (contributes.lua.main). */
    val lua: String,
    /** Поля настроек плагина (contributes.settings). */
    val settings: List<PluginSetting> = emptyList(),
    /** Пункт плагина в Настройках (contributes.settings_entry); null — пункта нет. */
    val menuEntry: PluginMenuEntry? = null,
    /**
     * Хуки, которые плагин объявил (contributes.hooks). Название и описание задаёт сам плагин, а логику хука (когда он действует)
     * плагин пишет в своём Lua-коде: `hooks.on(id, function() return true end)`. Если хуки объявлены, в настройках плагина всегда
     * есть главный выключатель «Хуки» (по умолчанию выключен), а при установке приложение спрашивает, включать ли их.
     */
    val hooks: List<PluginHook> = emptyList(),
    /**
     * Плагину нужен доступ к Java (contributes.unsafe_java). Тогда у пользователя в настройках плагина (при включённых хуках)
     * появляется выключатель «Отключить песочницу» — после предупреждения. Само по себе поле ничего не включает.
     */
    val unsafeJava: Boolean = false,
)

/** Хук плагина: [id] — имя, которое плагин придумал сам, [title] и [summary] — его подписи в настройках и окне установки. */
data class PluginHook(val id: String, val title: String, val summary: String)

/**
 * Хуки плагина. Приложение НЕ содержит никакой логики хуков: плагин сам объявляет их в манифесте (contributes.hooks) и сам пишет
 * их код в Lua: `hooks.on(id, function(active) ... end)`. Приложение только даёт пользователю выключатели и вызывает обработчик,
 * когда хук включается или выключается. Пока главный выключатель «Включить хуки» в настройках плагина выключен (по умолчанию он
 * выключен, при установке приложение спрашивает), ни один хук не вызывается.
 *
 * Отдельный режим «Отключить песочницу» (только для плагинов с contributes.unsafe_java, только при включённых хуках, только после
 * предупреждения): скрипт получает luajava и доступ к объектам приложения и может выполнять Java-код.
 */
object PluginHooks {
    /** Префикс ключа выключателей хуков в настройках плагина; свои настройки плагина с таким префиксом запрещены. */
    const val KEY_PREFIX = "hook_"

    /** Главный выключатель хуков плагина: по умолчанию выключен, спрашивается при установке. */
    const val MASTER_KEY = KEY_PREFIX + "enabled"

    /** «Отключить песочницу» для этого плагина: по умолчанию выключено, перед включением показывается предупреждение. */
    const val UNSAFE_KEY = KEY_PREFIX + "unsafe"

    fun key(id: String) = KEY_PREFIX + id
}

/** Установленный плагин. Включён он или нет, можно менять; хранится в настройках. */
class InstalledPlugin(val manifest: PluginManifest, val file: File, val iconBytes: ByteArray?, enabled: Boolean = true) {
    var enabled by mutableStateOf(enabled)
        internal set
    val id: String get() = manifest.id

    /** Состояние Lua-скрипта для списка плагинов («Работает», текст ошибки); null — скрипт остановлен. */
    var luaStatus by mutableStateOf<String?>(null)
        internal set

    /** Цвет акцента и AMOLED, заданные из Lua (theme.set_accent / theme.set_amoled), и станции волны из Lua (wave.add_station). */
    var luaAccent by mutableStateOf<Int?>(null)
        internal set
    var luaAmoled by mutableStateOf<Boolean?>(null)
        internal set
    val luaStations = mutableStateListOf<PluginStation>()

    /** Пользователь включил хуки этого плагина (главный выключатель в настройках плагина). */
    val hooksOn: Boolean get() = manifest.hooks.isNotEmpty() && settingValues[PluginHooks.MASTER_KEY] == true

    /** Для этого плагина отключена песочница (ОПАСНО): нужны хуки, объявленный unsafe_java и включённый пользователем выключатель. */
    val unsafeOn: Boolean get() = manifest.unsafeJava && hooksOn && settingValues[PluginHooks.UNSAFE_KEY] == true

    /** Текущие значения настроек (для интерфейса) и их неизменяемая копия для потока Lua. */
    val settingValues = mutableStateMapOf<String, Any>()

    /** Страницы настроек, которые плагин создал из Lua (ui.settings_page), и их последняя отрисовка (id страницы → компоненты). */
    val uiEntries = mutableStateListOf<PluginUiEntry>()
    val uiPages = mutableStateMapOf<String, List<PluginUiNode>>()
    @Volatile var settingsCopy: Map<String, Any> = emptyMap()
        internal set
}

/** Прочитанный, но ещё не установленный плагин: показывается в окне установки. */
class PluginPreview(
    val manifest: PluginManifest,
    val file: File,
    val iconBytes: ByteArray?,
    /** Версия, которая уже установлена (если это обновление или переустановка). */
    val installed: PluginManifest?,
    /** Размер самого .exm файла. */
    val fileBytes: Long,
)

private class ParsedPackage(val manifest: PluginManifest, val iconBytes: ByteArray?)

object PluginSystem {
    /** Версия API плагинов, которую понимает это приложение. */
    const val API_VERSION = 2

    /** Расширение файла плагина. */
    const val EXTENSION = "exm"

    private const val TAG = "Plugins"
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024
    private const val MAX_UNPACKED_BYTES = 40L * 1024 * 1024
    private const val MAX_ENTRIES = 256
    private const val MAX_MANIFEST_BYTES = 128 * 1024
    private const val MAX_DATA_BYTES = 256 * 1024
    private const val MAX_ICON_BYTES = 512 * 1024
    private const val MAX_LUA_BYTES = 512 * 1024
    private const val MAX_SETTINGS = 30
    private const val MAX_HOOKS = 8
    private const val MAX_ICON_SIDE = 1024

    private val ID_RE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$")
    private val SETTING_KEY_RE = Regex("^[a-z][a-z0-9_]{0,31}$")
    private val COLOR_RE = Regex("^#[0-9A-Fa-f]{6}$")
    private val OPTION_VALUE_RE = Regex("^[A-Za-z0-9_.-]{1,32}$")

    private var prefs: SharedPreferences? = null
    private var dir: File? = null
    private var cacheDir: File? = null
    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private var disabled: Set<String> = emptySet()

    /** Главный выключатель: пока он выключен, ни один плагин не действует (но остаётся установленным). По умолчанию включён. */
    var enabled by mutableStateOf(true)
        private set

    val plugins = mutableStateListOf<InstalledPlugin>()

    /** Плагин, который предлагается установить (его показывает окно снизу). */
    var pending by mutableStateOf<PluginPreview?>(null)
        private set

    /** Текст ошибки, если файл не получилось прочитать. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Диалоги из Lua: показывается последний; открыть можно не больше [MAX_DIALOGS_PER_PLUGIN] от одного плагина. */
    val luaDialogs = mutableStateListOf<PluginDialog>()

    /** Нижняя модалка, открытая из Lua; её рисует главный экран. */
    var sheetReq by mutableStateOf<PluginSheetReq?>(null)
        private set

    private const val MAX_DIALOGS_PER_PLUGIN = 3

    fun showDialog(d: PluginDialog) {
        if (luaDialogs.count { it.pluginId == d.pluginId } < MAX_DIALOGS_PER_PLUGIN) luaDialogs.add(d)
    }

    /** Пользователь ответил на диалог: [button] — номер кнопки, -1 — закрыл без выбора; [input] — текст из поля (если оно было). */
    fun answerDialog(d: PluginDialog, button: Int, input: String?) {
        if (!luaDialogs.remove(d)) return
        plugins.firstOrNull { it.id == d.pluginId }?.let { LuaPlugins.dialogEvent(it, d.key, button, input) }
    }

    fun openSheet(pluginId: String, entryId: String) { sheetReq = PluginSheetReq(pluginId, entryId) }

    /** Lua просит закрыть свою шторку (entryId = null — любую свою). */
    fun requestCloseSheet(pluginId: String, entryId: String?) {
        val r = sheetReq ?: return
        if (r.pluginId == pluginId && (entryId == null || r.entryId == entryId)) sheetReq = r.copy(closing = true)
    }

    /** Шторка закрыта (пальцем или по просьбе Lua): сообщаем скрипту (on_close). */
    fun finishSheet() {
        val r = sheetReq ?: return
        sheetReq = null
        plugins.firstOrNull { it.id == r.pluginId }?.let { LuaPlugins.sheetClosed(it, r.entryId) }
    }

    /** Плагин остановлен или выключен: его диалоги и шторка больше не нужны. */
    fun dropUi(pluginId: String) {
        luaDialogs.removeAll { it.pluginId == pluginId }
        if (sheetReq?.pluginId == pluginId) sheetReq = null
    }

    /** Идёт чтение файла плагина. */
    var busy by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        val p = app.getSharedPreferences("plugins", Context.MODE_PRIVATE)
        prefs = p
        appContext = app
        enabled = p.getBoolean("enabled", true)
        disabled = p.getStringSet("disabled", emptySet())?.toSet() ?: emptySet()
        val d = File(app.filesDir, "plugins").apply { mkdirs() }
        dir = d
        cacheDir = app.cacheDir
        // Подчищаем недокачанные файлы прошлых запусков.
        app.cacheDir.listFiles()?.filter { it.name.startsWith("plugin_pending_") }?.forEach { it.delete() }
        plugins.clear()
        d.listFiles { f -> f.isFile && f.extension.equals(EXTENSION, true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { f ->
                runCatching { parsePackage(f) }
                    .onSuccess { pkg ->
                        val ip = InstalledPlugin(pkg.manifest, f, pkg.iconBytes, pkg.manifest.id !in disabled)
                        loadSettings(ip)
                        plugins.add(ip)
                    }
                    .onFailure { AppLog.w(TAG, "Плагин ${f.name} не прочитан: ${it.message}") }
            }
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
        LuaPlugins.sync()
    }

    fun setPluginEnabled(p: InstalledPlugin, on: Boolean) {
        p.enabled = on
        disabled = if (on) disabled - p.id else disabled + p.id
        prefs?.edit()?.putStringSet("disabled", disabled)?.apply()
        LuaPlugins.sync()
    }

    fun uninstall(p: InstalledPlugin) {
        runCatching { p.file.delete() }
        plugins.remove(p)
        disabled = disabled - p.id
        prefs?.edit()?.putStringSet("disabled", disabled)?.apply()
        LuaPlugins.sync()
        LuaPlugins.purge(p.id)
        runCatching { appContext?.deleteSharedPreferences(settingsPrefsName(p.id)) }
    }

    // ---------- Настройки плагинов ----------

    private fun settingsPrefsName(id: String) = "plugin_settings_$id"

    private fun settingsPrefs(id: String): SharedPreferences? = appContext?.getSharedPreferences(settingsPrefsName(id), Context.MODE_PRIVATE)

    /** Значение в допустимых границах: для slider/number — в диапазоне и по шагу. */
    fun snapSetting(s: PluginSetting, v: Double): Double {
        val c = v.coerceIn(s.min, s.max)
        val k = Math.round((c - s.min) / s.step)
        return (s.min + k * s.step).coerceIn(s.min, s.max)
    }

    /** Проверяет значение по описанию поля; null — значение не подходит. */
    fun normalizeSetting(s: PluginSetting, raw: Any?): Any? = when (s.type) {
        "switch" -> raw as? Boolean
        "slider", "number" -> (raw as? Number)?.toDouble()?.takeIf { !it.isNaN() && !it.isInfinite() }?.let { snapSetting(s, it) }
        "text" -> (raw as? String)?.take(s.maxLength)
        "choice" -> (raw as? String)?.takeIf { v -> s.options.any { it.first == v } }
        "color" -> (raw as? String)?.trim()?.takeIf { COLOR_RE.matches(it) }?.uppercase()
        else -> null
    }

    private fun loadSettings(p: InstalledPlugin) {
        if (p.manifest.settings.isEmpty()) return
        val sp = settingsPrefs(p.id)
        val map = HashMap<String, Any>()
        for (s in p.manifest.settings) {
            if (!s.hasValue) continue
            val raw = sp?.getString(s.key, null)
            val parsed: Any? = when (s.type) {
                "switch" -> raw?.toBooleanStrictOrNull()
                "slider", "number" -> raw?.toDoubleOrNull()
                else -> raw
            }
            map[s.key] = normalizeSetting(s, parsed) ?: s.default ?: continue
        }
        p.settingValues.clear()
        p.settingValues.putAll(map)
        p.settingsCopy = map
    }

    /** Пользователь изменил настройку плагина: значение проверяется, сохраняется и сообщается скрипту (событие setting_changed). */
    fun setSetting(p: InstalledPlugin, key: String, raw: Any?, notify: Boolean = true) {
        val s = p.manifest.settings.firstOrNull { it.key == key && it.hasValue } ?: return
        val v = normalizeSetting(s, raw) ?: return
        if (p.settingValues[key] == v) return
        p.settingValues[key] = v
        p.settingsCopy = HashMap(p.settingValues)
        settingsPrefs(p.id)?.edit()?.putString(key, v.toString())?.apply()
        if (notify && enabled && p.enabled) LuaPlugins.settingChanged(p, key, v)
    }

    /**
     * Пользователь тронул компонент на странице, созданной Lua: значение проверяется по описанию поля, на экране обновляется
     * сразу (чтобы переключатель не отставал), а Lua-колбэк вызывается в потоке Lua; после него страница перерисовывается.
     * Для кнопок [raw] = null.
     */
    fun uiEvent(p: InstalledPlugin, entryId: String, index: Int, raw: Any?) {
        if (!enabled || !p.enabled) return
        val nodes = p.uiPages[entryId] ?: return
        val n = nodes.firstOrNull { it.index == index } ?: return
        val v: Any? = if (n.st.type == "button") null else (normalizeSetting(n.st, raw) ?: return)
        if (v != null) p.uiPages[entryId] = nodes.map { if (it.index == index) PluginUiNode(it.index, it.st, v) else it }
        LuaPlugins.uiEvent(p, entryId, index, v)
    }

    /** Окно настроек открыли: просим Lua заново нарисовать страницу [entryId] (null — все страницы плагина). */
    fun requestPage(p: InstalledPlugin, entryId: String?) {
        if (enabled && p.enabled) LuaPlugins.renderPages(p, entryId)
    }

    /** Условие показа поля (show_if): true — поле нужно рисовать. */
    fun isSettingVisible(p: InstalledPlugin, s: PluginSetting): Boolean {
        val k = s.showIfKey ?: return true
        val cur = p.settingValues[k]
        val want = s.showIfEquals
        return when {
            cur == null -> false
            want is Boolean -> cur == want
            want is Number -> (cur as? Number)?.toDouble() == want.toDouble()
            else -> cur.toString() == want.toString()
        }
    }

    /** Нажата кнопка из настроек плагина (событие action). */
    fun pressSettingButton(p: InstalledPlugin, key: String) {
        if (enabled && p.enabled && p.manifest.settings.any { it.key == key && it.type == "button" }) LuaPlugins.action(p, key)
    }

    fun resetSettings(p: InstalledPlugin) {
        for (s in p.manifest.settings) if (s.hasValue) setSetting(p, s.key, s.default)
    }

    // ---------- Что плагины добавляют в приложение ----------

    /** Станции волны из включённых плагинов (пусто, если система выключена). */
    val activeStations: List<Pair<InstalledPlugin, PluginStation>>
        get() = if (!enabled) emptyList() else plugins.filter { it.enabled }.flatMap { p -> p.luaStations.map { p to it } }

    /** Цвет акцента из включённых плагинов (первый по списку); null — плагины цвет не задают. */
    val themeAccent: Int?
        get() = if (!enabled) null else plugins.firstOrNull { it.enabled && it.luaAccent != null }?.luaAccent

    /** Плагин включил AMOLED-фон. */
    val themeAmoled: Boolean
        get() = enabled && plugins.any { it.enabled && it.luaAmoled == true }

    /** Читает файл из архива плагина (например, свои данные); null, если файла нет или он слишком большой. */
    fun readFile(p: InstalledPlugin, name: String, maxBytes: Int = MAX_DATA_BYTES): ByteArray? =
        runCatching { ZipFile(p.file).use { z -> z.getEntry(name)?.let { readEntry(z, it, maxBytes) } } }.getOrNull()

    // ---------- Установка ----------

    /**
     * Читает файл плагина по [uri] (из «Открыть с помощью…» или из выбора файла) и, если он годится, показывает
     * окно установки ([pending]). Вызывать с главного потока.
     */
    fun offerInstall(context: Context, uri: Uri) {
        val app = context.applicationContext
        val cd = cacheDir ?: app.cacheDir
        // Чужое приложение может прислать file:// на закрытые файлы самого ExMusic (токены, настройки): такие адреса не читаем.
        if (uri.scheme.equals("file", true)) {
            val path = runCatching { File(uri.path ?: "").canonicalPath }.getOrNull()
            val own = runCatching { app.filesDir.parentFile?.canonicalPath }.getOrNull()
            if (path == null || (own != null && (path == own || path.startsWith("$own/")))) {
                error = "Не удалось открыть файл плагина"
                return
            }
        }
        busy = true
        Thread {
            val tmp = File(cd, "plugin_pending_${System.nanoTime()}.$EXTENSION")
            val result = runCatching {
                copyLimited(app, uri, tmp)
                val pkg = parsePackage(tmp)
                PluginPreview(
                    manifest = pkg.manifest, file = tmp, iconBytes = pkg.iconBytes,
                    installed = plugins.firstOrNull { it.id == pkg.manifest.id }?.manifest,
                    fileBytes = tmp.length(),
                )
            }
            main.post {
                busy = false
                result.onSuccess {
                    pending?.file?.delete()
                    pending = it
                }.onFailure { e ->
                    tmp.delete()
                    AppLog.w(TAG, "Не удалось прочитать плагин: ${e.message}", e)
                    error = if (e is PluginException) e.message else "Не удалось открыть файл плагина"
                }
            }
        }.start()
    }

    /**
     * «Установить» в окне установки: сохраняет .exm в папку плагинов (заменяя прежнюю версию).
     * [hooksOn] — ответ пользователя на вопрос «Включить хуки?» (только если плагин объявил хуки); по умолчанию выключено.
     */
    fun confirmInstall(hooksOn: Boolean = false) {
        val pv = pending ?: return
        val d = dir
        if (d == null) { error = "Хранилище плагинов недоступно"; return }
        val target = File(d, pv.manifest.id + "." + EXTENSION)
        val ok = runCatching {
            if (target.exists()) target.delete()
            if (!pv.file.renameTo(target)) {
                pv.file.copyTo(target, overwrite = true)
                pv.file.delete()
            }
        }.isSuccess
        pending = null
        if (!ok) { error = "Не удалось сохранить плагин"; return }
        val ip = InstalledPlugin(pv.manifest, target, pv.iconBytes, true)
        loadSettings(ip)
        // Хуки: ответ из окна установки (в окне обновления там стоит прежнее значение). Сохраняем до запуска скрипта.
        if (pv.manifest.hooks.isNotEmpty()) setSetting(ip, PluginHooks.MASTER_KEY, hooksOn, notify = false)
        val i = plugins.indexOfFirst { it.id == pv.manifest.id }
        if (i >= 0) plugins[i] = ip else plugins.add(ip)
        if (pv.manifest.id in disabled) {
            disabled = disabled - pv.manifest.id
            prefs?.edit()?.putStringSet("disabled", disabled)?.apply()
        }
        // Установили плагин — значит, он нужен: если система была выключена, включаем её, чтобы плагин сразу заработал.
        if (!enabled) { enabled = true; prefs?.edit()?.putBoolean("enabled", true)?.apply() }
        LuaPlugins.sync()
    }

    /** «Отмена» или закрытие окна установки. */
    fun dismissPending() {
        pending?.file?.delete()
        pending = null
    }

    fun clearError() { error = null }

    // ---------- Разбор архива ----------

    private fun copyLimited(ctx: Context, uri: Uri, to: File) {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw PluginException("Не удалось открыть файл")
        input.use { ins ->
            to.outputStream().use { out ->
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_FILE_BYTES) throw PluginException("Файл слишком большой (больше ${MAX_FILE_BYTES / 1024 / 1024} МБ)")
                    out.write(buf, 0, n)
                }
            }
        }
    }

    private fun readEntry(z: ZipFile, e: java.util.zip.ZipEntry, max: Int): ByteArray {
        z.getInputStream(e).use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                if (out.size() + n > max) throw PluginException("Файл «${e.name}» слишком большой")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun parsePackage(file: File): ParsedPackage {
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw PluginException("Это не плагин: файл .exm должен быть ZIP-архивом с manifest.json")
        }
        zip.use { z ->
            var count = 0
            var unpacked = 0L
            // Папки тоже считаем: архив из миллионов пустых записей иначе съел бы память ещё до проверки числа файлов.
            if (z.size() > MAX_ENTRIES * 2) throw PluginException("В архиве слишком много файлов (больше $MAX_ENTRIES)")
            val names = HashSet<String>()
            val entries = z.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                val n = e.name
                if (n.startsWith("/") || n.contains("\\") || n.split('/').any { it == ".." } || n.length > 200 || n.any { it.isISOControl() })
                    throw PluginException("В архиве недопустимое имя файла: «${n.filter { !it.isISOControl() }.take(80)}»")
                // Два файла с одним именем: проверяли бы один, а запускался бы другой.
                if (!names.add(n)) throw PluginException("В архиве два файла с одинаковым именем: «${n.take(80)}»")
                if (!e.isDirectory) {
                    count++
                    if (e.size > 0) unpacked += e.size
                }
                if (count > MAX_ENTRIES) throw PluginException("В архиве слишком много файлов (больше $MAX_ENTRIES)")
                if (unpacked > MAX_UNPACKED_BYTES) throw PluginException("Архив слишком большой после распаковки")
            }
            val mEntry = z.getEntry("manifest.json") ?: throw PluginException("В архиве нет manifest.json")
            val json = try {
                JSONObject(String(readEntry(z, mEntry, MAX_MANIFEST_BYTES), Charsets.UTF_8))
            } catch (e: PluginException) {
                throw e
            } catch (e: Exception) {
                throw PluginException("manifest.json не читается: неверный JSON")
            }

            val id = json.optString("id").trim()
            if (!ID_RE.matches(id)) throw PluginException("Неверный id плагина (3–64 символа: латиница, цифры, точка, дефис, подчёркивание)")
            val name = json.optString("name").trim()
            if (name.isEmpty() || name.length > 60) throw PluginException("Название плагина должно быть от 1 до 60 символов")
            val version = json.optString("version").trim()
            if (version.isEmpty() || version.length > 24) throw PluginException("Версия плагина должна быть от 1 до 24 символов")
            val author = json.optString("author").trim().take(60)
            val description = json.optString("description").trim().take(500)
            val api = json.optInt("api", API_VERSION)
            if (api > API_VERSION) throw PluginException("Плагин сделан для более новой версии API ($api), а здесь поддерживается $API_VERSION. Обновите приложение")
            if (api < 1) throw PluginException("Неверная версия API плагина")

            // Иконка необязательна: если с ней что-то не так, плагин всё равно ставится, просто с значком «пазл».
            var iconBytes: ByteArray? = null
            val iconName = json.optString("icon").trim().takeIf { it.isNotEmpty() }
            if (iconName != null) {
                iconBytes = runCatching {
                    val ie = z.getEntry(iconName) ?: error("нет файла")
                    val ext = iconName.substringAfterLast('.', "").lowercase()
                    if (ext !in setOf("png", "jpg", "jpeg", "webp")) error("формат")
                    val ib = readEntry(z, ie, MAX_ICON_BYTES)
                    // Размер картинки проверяем до декодирования: маленький файл может «раскрыться» в огромный битмап.
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(ib, 0, ib.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > MAX_ICON_SIDE || bounds.outHeight > MAX_ICON_SIDE) error("размер")
                    ib
                }.getOrNull()
            }

            val contributes = json.optJSONObject("contributes") ?: JSONObject()

            // ---- Lua-скрипт ----
            if (!contributes.has("lua")) throw PluginException("Плагин должен содержать Lua-скрипт: в manifest.json нужно contributes.lua = {\"main\": \"main.lua\"}")
            val rawLua = contributes.get("lua")
            val luaMain = when (rawLua) {
                is String -> rawLua
                is JSONObject -> rawLua.optString("main")
                else -> ""
            }.trim()
            if (luaMain.isEmpty() || !luaMain.endsWith(".lua")) throw PluginException("lua: укажите файл скрипта, например {\"main\": \"main.lua\"}")
            val le = z.getEntry(luaMain) ?: throw PluginException("lua: файл «$luaMain» не найден в архиве")
            if (le.isDirectory) throw PluginException("lua: «$luaMain» — это папка, а не файл")
            if (le.size > MAX_LUA_BYTES) throw PluginException("lua: скрипт «$luaMain» слишком большой (больше ${MAX_LUA_BYTES / 1024} КБ)")

            // ---- Настройки плагина (только вместе с Lua) ----
            val settings = ArrayList<PluginSetting>()
            if (contributes.has("settings")) {
                val arr = contributes.optJSONArray("settings") ?: throw PluginException("settings: нужен массив полей")
                if (arr.length() > MAX_SETTINGS) throw PluginException("settings: не больше $MAX_SETTINGS полей")
                val seen = HashSet<String>()
                for (i in 0 until arr.length()) {
                    val n = "Настройка №${i + 1}"
                    val o = arr.optJSONObject(i) ?: throw PluginException("$n: ожидался объект")
                    val key = o.optString("key").trim()
                    if (!SETTING_KEY_RE.matches(key)) throw PluginException("$n: key — латиница в нижнем регистре, цифры и _ (до 32 символов, с буквы)")
                    if (key.startsWith(PluginHooks.KEY_PREFIX)) throw PluginException("$n: ключи с префиксом «${PluginHooks.KEY_PREFIX}» зарезервированы за хуками")
                    if (!seen.add(key)) throw PluginException("$n: ключ «$key» повторяется")
                    val type = o.optString("type").trim()
                    val title = o.optString("title").trim()
                    if (title.isEmpty() || title.length > 60) throw PluginException("$n: title от 1 до 60 символов")
                    val summary = o.optString("summary").trim().take(160)
                    settings += when (type) {
                        "switch" -> PluginSetting(key, type, title, summary, default = o.optBoolean("default", false))
                        "slider", "number" -> {
                            val mn = o.optDouble("min", 0.0)
                            val mx = o.optDouble("max", 100.0)
                            val st = o.optDouble("step", 1.0)
                            if (mn.isNaN() || mx.isNaN() || st.isNaN() || mn >= mx || st <= 0.0 || Math.abs(mn) > 1e9 || Math.abs(mx) > 1e9 || (mx - mn) / st > 100000.0)
                                throw PluginException("$n: неверные min/max/step")
                            val def = o.optDouble("default", mn).let { if (it.isNaN()) mn else it }.coerceIn(mn, mx)
                            PluginSetting(key, type, title, summary, default = def, min = mn, max = mx, step = st)
                        }
                        "text" -> {
                            val ml = o.optInt("max_length", 100).coerceIn(1, 500)
                            PluginSetting(key, type, title, summary, default = o.optString("default").take(ml), maxLength = ml)
                        }
                        "choice" -> {
                            val oa = o.optJSONArray("options") ?: throw PluginException("$n: у choice нужен массив options")
                            if (oa.length() < 2 || oa.length() > 12) throw PluginException("$n: options — от 2 до 12 вариантов")
                            val opts = ArrayList<Pair<String, String>>()
                            for (j in 0 until oa.length()) {
                                val oo = oa.optJSONObject(j) ?: throw PluginException("$n: вариант №${j + 1} — ожидался объект")
                                val v = oo.optString("value").trim()
                                val t = oo.optString("title").trim()
                                if (!OPTION_VALUE_RE.matches(v)) throw PluginException("$n: value варианта №${j + 1} — латиница, цифры, . _ - (до 32)")
                                if (t.isEmpty() || t.length > 40) throw PluginException("$n: title варианта №${j + 1} — от 1 до 40 символов")
                                if (opts.any { it.first == v }) throw PluginException("$n: value «$v» повторяется")
                                opts += v to t
                            }
                            val dv = o.optString("default").trim()
                            PluginSetting(key, type, title, summary, default = if (opts.any { it.first == dv }) dv else opts[0].first, options = opts)
                        }
                        "color" -> {
                            val dc = o.optString("default").trim()
                            PluginSetting(key, type, title, summary, default = if (COLOR_RE.matches(dc)) dc.uppercase() else "#6750A4")
                        }
                        "button", "section", "info" -> PluginSetting(key, type, title, summary)
                        else -> throw PluginException("$n: неизвестный тип «$type» (switch, slider, number, text, choice, color, button, section, info)")
                    }
                    // Условие показа: "show_if": {"key": "другое_поле", "equals": значение}.
                    val si = o.optJSONObject("show_if")
                    if (si != null) {
                        val sk = si.optString("key").trim()
                        val ev = si.opt("equals")
                        if (!SETTING_KEY_RE.matches(sk) || sk == key) throw PluginException("$n: show_if.key — ключ другого поля")
                        if (ev !is Boolean && ev !is Number && ev !is String) throw PluginException("$n: show_if.equals — true/false, число или строка")
                        settings[settings.lastIndex] = settings.last().copy(showIfKey = sk, showIfEquals = ev)
                    }
                }
                for (st in settings) {
                    val k = st.showIfKey ?: continue
                    val target = settings.firstOrNull { it.key == k }
                    if (target == null || !target.hasValue) throw PluginException("Поле «${st.key}»: show_if ссылается на «$k», а такого поля со значением нет")
                }
            }

            // ---- Хуки (contributes.hooks): плагин сам объявляет хуки и их подписи, код — в его Lua (hooks.on) ----
            // Элемент — объект {"id", "title", "summary"} или просто строка-id. id — как ключ настройки: a-z, 0-9, _.
            val hooks = ArrayList<PluginHook>()
            if (contributes.has("hooks")) {
                val ha = contributes.optJSONArray("hooks") ?: throw PluginException("hooks: нужен массив хуков")
                if (ha.length() > MAX_HOOKS) throw PluginException("hooks: не больше $MAX_HOOKS хуков")
                for (i in 0 until ha.length()) {
                    val raw = ha.opt(i)
                    val hid: String
                    var htitle = ""
                    var hsummary = ""
                    when (raw) {
                        is String -> hid = raw.trim()
                        is JSONObject -> {
                            hid = raw.optString("id").trim()
                            htitle = raw.optString("title").trim()
                            hsummary = raw.optString("summary").trim().take(160)
                        }
                        else -> throw PluginException("hooks: хук №${i + 1} — строка с id или объект {id, title, summary}")
                    }
                    if (!SETTING_KEY_RE.matches(hid) || hid == "enabled" || hid == "unsafe") {
                        throw PluginException("hooks: id хука №${i + 1} — латиница в нижнем регистре, цифры и _ (до 32 символов, с буквы; «enabled» и «unsafe» заняты)")
                    }
                    if (hooks.any { it.id == hid }) throw PluginException("hooks: хук «$hid» повторяется")
                    if (htitle.length > 60) throw PluginException("hooks: title хука «$hid» — до 60 символов")
                    hooks += PluginHook(hid, htitle.ifEmpty { hid }, hsummary)
                }
            }
            val unsafeJava = contributes.optBoolean("unsafe_java", false)
            if (unsafeJava && hooks.isEmpty()) throw PluginException("unsafe_java: нужен хотя бы один хук (contributes.hooks): песочница отключается только вместе с хуками")
            if (hooks.isNotEmpty()) {
                // Хуки объявлены: настройки у плагина есть всегда. Заголовок, главный выключатель (по умолчанию ВЫКЛЮЧЕН),
                // при unsafe_java — выключатель песочницы (по умолчанию выключен) и по выключателю на каждый хук.
                val hs = ArrayList<PluginSetting>()
                hs += PluginSetting("hooks_header", "section", "Хуки", "Хуки меняют поведение приложения. Код хуков находится в самом плагине")
                hs += PluginSetting(
                    PluginHooks.MASTER_KEY, "switch", "Включить хуки",
                    "По умолчанию выключено. Пока не включено, плагин ничего в приложении не меняет", default = false,
                )
                if (unsafeJava) {
                    hs += PluginSetting(
                        PluginHooks.UNSAFE_KEY, "switch", "Отключить песочницу (ОПАСНО)",
                        "Скрипт плагина сможет выполнять Java-код с правами приложения. Включайте только плагины, которым полностью доверяете",
                        default = false, showIfKey = PluginHooks.MASTER_KEY, showIfEquals = true,
                    )
                }
                for (h in hooks) {
                    hs += PluginSetting(
                        PluginHooks.key(h.id), "switch", h.title, h.summary, default = true,
                        showIfKey = PluginHooks.MASTER_KEY, showIfEquals = true,
                    )
                }
                settings.addAll(0, hs)
            }

            // ---- Свой пункт в главных настройках (contributes.settings_entry) ----
            var menuEntry: PluginMenuEntry? = null
            if (contributes.has("settings_entry")) {
                if (settings.isEmpty()) throw PluginException("settings_entry: у плагина нет настроек, открывать нечего")
                when (val raw = contributes.get("settings_entry")) {
                    is JSONObject -> menuEntry = PluginMenuEntry(
                        raw.optString("title").trim().ifEmpty { name }.take(60),
                        raw.optString("summary").trim().take(100),
                    )
                    true -> menuEntry = PluginMenuEntry(name.take(60))
                    else -> Unit
                }
            }

            return ParsedPackage(
                PluginManifest(
                    id = id, name = name, version = version, author = author, description = description, api = api,
                    icon = iconName, fileCount = count, unpackedBytes = unpacked,
                    lua = luaMain, settings = settings, menuEntry = menuEntry, hooks = hooks, unsafeJava = unsafeJava,
                ),
                iconBytes,
            )
        }
    }
}
