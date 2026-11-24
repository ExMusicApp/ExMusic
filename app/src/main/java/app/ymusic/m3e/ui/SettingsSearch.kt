@file:OptIn(ExperimentalMaterial3Api::class)

package app.ymusic.m3e.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R

/**
 * Пункт для поиска по настройкам. [page] — имя страницы (SettingsPage) либо служебное: «main» (пункт на главной странице
 * настроек, просто закрываем поиск) и «links» (системные настройки открытия ссылок). [section] — раздел внутри страницы.
 * [keywords] — слова, по которым пункт тоже находится, но которые на экране не показываются.
 */
internal class SettingsEntry(
    val title: String,
    val desc: String,
    val page: String,
    val section: String = "",
    val keywords: String = "",
)

internal object SettingsSearch {
    private val PAGE_TITLES = mapOf(
        "Accounts" to "Аккаунты", "Appearance" to "Внешний вид", "Haptics" to "Вибрация", "Player" to "Плеер",
        "Equalizer" to "Эквалайзер", "Recognition" to "Распознавание музыки", "ScProxy" to "Обход блокировки SoundCloud (BETA)",
        "Downloads" to "Скачивание песен", "Offline" to "Офлайн-режим", "Plugins" to "Плагины", "Updates" to "Обновления",
        "About" to "О проекте", "Developer" to "Для разработчиков",
    )

    /** Название страницы для «хлебных крошек»; null — пункт лежит на главной странице. */
    fun pageTitle(page: String): String? = PAGE_TITLES[page]

    private fun index(): List<SettingsEntry> {
        val sc = ActiveProvider.isSc
        val vk = ActiveProvider.isVk
        return listOf(
            // ---- Главная страница настроек ----
            SettingsEntry("Аккаунты", "Добавить аккаунт, сменить или выйти", "Accounts", keywords = "яндекс vk вк soundcloud spotify вход логин профиль"),
            SettingsEntry("Язык", "Русский, английский, белорусский", "main", keywords = "язык интерфейса language перевод"),
            SettingsEntry("Внешний вид", "Тема, цвета, шрифт, формы, размер, стекло, свечение, анимации, главная, поиск и док-панель", "Appearance"),
            SettingsEntry("Вибрация", "Виброотклик при нажатиях", "Haptics"),
            SettingsEntry("Плеер", "Качество звука, мини-плеер, полоса прогресса, тексты песен, волна", "Player"),
            SettingsEntry("Эквалайзер", "Настройка частот и усиления звука", "Equalizer", keywords = "басы эквалайзер пресет звук"),
            SettingsEntry("Распознавание музыки", "Ключ сервиса, длина записи, плитка в шторке", "Recognition", keywords = "shazam шазам узнать трек"),
            SettingsEntry("Обход блокировки SoundCloud (BETA)", "Встроенный обход или свой прокси только для SoundCloud", "ScProxy", keywords = "прокси proxy vpn блокировка доступ dpi doh socks5 http"),
            SettingsEntry(
                if (sc) "Ссылки SoundCloud" else if (vk) "Ссылки VK Музыки" else "Ссылки Яндекс Музыки",
                if (sc) "Открывать ссылки soundcloud.com в этом приложении" else if (vk) "Открывать ссылки vk.com и vk.ru на музыку в этом приложении" else "Открывать ссылки music.yandex.ru в этом приложении",
                "links", keywords = "открывать по умолчанию deeplink vk вк",
            ),
            SettingsEntry("Скачивание песен", "Куда сохранять, папка, сеть и список скачанного", "Downloads"),
            SettingsEntry("Кэш", "Размер и очистка кэша: обложки, тексты песен, предзагруженный звук", "Cache", keywords = "кэш кеш очистка память место cache"),
            SettingsEntry("Офлайн-режим", "Файлы с устройства", "Offline"),
            SettingsEntry("Плагины", "Система плагинов и установленные плагины", "Plugins", keywords = "lua расширения"),
            SettingsEntry("Обновления", "Версия, ветка и проверка обновлений", "Updates"),
            SettingsEntry("О проекте", "Версия, разработчики", "About"),
            SettingsEntry("Для разработчиков", "Логи клиента и логи последнего запуска", "Developer", keywords = "журнал отладка ошибки"),

            // ---- Плеер ----
            SettingsEntry("Высокое качество", "Лучший поток звука (нужен Плюс)", "Player", "Звук", "битрейт"),
            SettingsEntry("Микс треков", "Плавный переход между треками, как кроссфейд в Spotify", "Player", "Звук", "кроссфейд crossfade миксовать"),
            SettingsEntry("Предзагрузка треков", "Следующие треки загружаются заранее", "Player", "Звук", "кэш буфер"),
            SettingsEntry("Перематывание треков в мини-плеере", "Ползунок под названием трека", "Player", "Мини-плеер"),
            SettingsEntry("Изменение громкости в мини-плеере", "Ползунок системной громкости музыки", "Player", "Мини-плеер"),
            SettingsEntry("Цвет мини-плеера по обложке", "Подложка подстраивается под цвет обложки", "Player", "Мини-плеер"),
            SettingsEntry("Громкость в полном плеере", "Ползунок системной громкости под кнопками", "Player", "Полный плеер"),
            SettingsEntry("Размытый фон плеера", "Размытая обложка на фоне полного плеера", "Player", "Полный плеер"),
            SettingsEntry("Размытие текста песни в плеере", "", "Player", "Полный плеер"),
            SettingsEntry("Размытие текста на весь экран", "", "Player", "Полный плеер"),
            SettingsEntry("Тексты песен", "Источники текстов песен", "Player", "Тексты песен", "genius караоке"),
            SettingsEntry("Подтверждать выбор текста", "Окно подтверждения при ручном поиске текста", "Player", "Тексты песен", "искать самому поиск текста"),
            SettingsEntry("Привязка текста к треку", "Запоминать выбранный вручную текст для трека", "Player", "Тексты песен", "закрепить привязать текст"),

            SettingsEntry("Полоса прогресса", "Вид ползунка перемотки в плеере и мини-плеере", "Player", "Полоса прогресса", "слайдер перемотка"),
            SettingsEntry("Не повторять треки волны", "Уже звучавший трек не вернётся в волну 24 часа", "Player", "Волна", "повторы сутки моя волна vk микс exwave"),

            // ---- Вибрация ----
            SettingsEntry("Виброотклик", "Лёгкая отдача при нажатиях, перемотке и переключениях", "Haptics", "Виброотклик"),
            SettingsEntry("Сила вибрации", "Слабая, средняя, сильная", "Haptics", "Сила"),
            SettingsEntry("Сила нажатия", "Чем сильнее нажал, тем сильнее вибрация", "Haptics", "Сила"),
            SettingsEntry("Тики при удержании", "Нарастающая вибрация, пока палец держит кнопку", "Haptics", "Тики при удержании"),

            // ---- Распознавание ----
            SettingsEntry("Сервис распознавания", "Shazam, запасной сервис AudD и его ключ", "Recognition", "Сервис", "audd ключ токен"),
            SettingsEntry("Запись", "Длина записи для распознавания", "Recognition", "Запись", "микрофон"),
            SettingsEntry("Когда трек узнан", "Уведомление и доступ «Поверх других приложений»", "Recognition", "Когда трек узнан"),
            SettingsEntry("Плитка в шторке", "Быстрое распознавание из шторки быстрых настроек", "Recognition", "Шторка быстрых настроек", "quick settings"),

            // ---- Внешний вид ----
            SettingsEntry("Тема", "Светлая, тёмная, AMOLED", "Appearance", "Тема", "амолед чёрная ночной режим"),
            SettingsEntry("Цвета", "Цвета системы и свой цвет", "Appearance", "Цвета", "material you динамические акцент палитра"),
            SettingsEntry("Акценты", "Свои цвета частей интерфейса", "Appearance", "Акценты"),
            SettingsEntry("Шрифт", "Свой шрифт .ttf и .otf", "Appearance", "Шрифт"),
            SettingsEntry("Иконка приложения", "Смена значка на рабочем столе", "Appearance", "Иконка приложения", "лаунчер"),
            SettingsEntry("Форма элементов", "Скругления кнопок и карточек", "Appearance", "Формы"),
            SettingsEntry("Форма аватарок исполнителей", "", "Appearance", "Формы"),
            SettingsEntry("Свечение", "Подсветка по цвету обложки и оптимизация", "Appearance", "Свечение", "glow"),
            SettingsEntry("Анимации", "Скорость, направление смены вкладок, motion blur", "Appearance", "Анимации", "скорость переходы"),
            SettingsEntry("Планшеты", "Положение островка, боковая панель", "Appearance", "Планшеты", "tablet"),
            SettingsEntry("Главный экран", "Плиткой или значком в углу", "Appearance", "Главный экран", "главная"),
            SettingsEntry("Клавиатура при входе в поиск", "", "Appearance", "Поиск"),
            SettingsEntry("Информация об артисте", "Карточка «Об артисте» на странице исполнителя", "Appearance", "Страница артиста", "биография описание о исполнителе"),
            SettingsEntry("Док-панель", "Островок, автоскрытие, подписи вкладок", "Appearance", "Док-панель", "навигация снизу"),
            SettingsEntry("Матовое стекло", "Размытая прозрачность панелей", "Appearance", "Матовое стекло (размытая прозрачность)", "blur размытие"),
            SettingsEntry("Размер интерфейса", "Масштаб элементов", "Appearance", "Размер интерфейса", "масштаб крупнее мельче"),
            SettingsEntry("Навигация", "Чувствительность листания, экраны в панели, порядок", "Appearance", "Навигация", "вкладки свайп"),

            // ---- Скачивание ----
            SettingsEntry("Сеть для скачивания", "Wi-Fi или мобильный интернет", "Downloads", "Сеть", "вайфай трафик"),
            SettingsEntry("Спрашивать, куда сохранять", "Окно выбора при скачивании «Любимого», плейлиста или альбома", "Downloads", "Куда сохранять по умолчанию"),
            SettingsEntry("Папка скачивания", "Изменить папку, удалить все скачанные песни", "Downloads", "Папка скачивания", "каталог хранилище"),

            // ---- Прочее ----
            SettingsEntry("Файлы с устройства", "Папка «С устройства» в плейлистах и группа в поиске", "Offline", "", "локальные mp3"),
            SettingsEntry("Система плагинов", "Включить или выключить плагины", "Plugins"),
            SettingsEntry("Проверять при запуске", "Уведомление о новой версии внизу", "Updates", "", "автообновление"),
            SettingsEntry("Проверить обновления", "", "Updates"),
            SettingsEntry("Страница релизов", "github.com", "Updates", "", "скачать apk"),

            // ---- Обход блокировки SoundCloud ----
            SettingsEntry("Режим обхода", "Выключен, встроенный или свой прокси", "ScProxy", "Режим"),
            SettingsEntry("Встроенный обход", "DNS-over-HTTPS и дробление TLS против DPI", "ScProxy", "Встроенный обход", "doh"),
            SettingsEntry("Свой прокси", "HTTP или SOCKS5, логин и пароль, вставить ссылкой", "ScProxy", "Свой прокси", "socks5 telegram"),
            SettingsEntry("Подобрать стратегии ByeByeDPI", "Перебор стратегий ByeByeDPI на живых запросах к SoundCloud и вставка найденной в него", "ScProxy", "ByeByeDPI", "подбор стратегия подобрать"),
            SettingsEntry("ByeByeDPI", "SoundCloud через приложение ByeByeDPI (SOCKS5 127.0.0.1:1080)", "ScProxy", "ByeByeDPI", "byedpi dpi"),
            SettingsEntry("Проверить соединение", "Проверка доступа к SoundCloud", "ScProxy", "Проверка"),
        )
    }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е')

    /** Пункты, у которых каждое слово запроса встречается в названии, описании, разделе или скрытых словах (на русском или в текущем языке). */
    fun search(query: String): List<SettingsEntry> {
        val tokens = norm(query).split(' ', ',', ';').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return index().mapNotNull { e ->
            val title = norm(e.title) + " " + norm(AppLang.tr(e.title))
            val rest = listOf(e.desc, e.section).joinToString(" ") { norm(it) + " " + norm(AppLang.tr(it)) } + " " + norm(e.keywords) +
                " " + (pageTitle(e.page)?.let { norm(it) + " " + norm(AppLang.tr(it)) } ?: "")
            val hay = "$title $rest"
            if (!tokens.all { hay.contains(it) }) null
            else e to (if (tokens.all { title.contains(it) }) 0 else if (tokens.any { title.contains(it) }) 1 else 2)
        }.sortedBy { it.second }.map { it.first }
    }
}

/**
 * Что подсветить после перехода из поиска: раздел (он откроется сам) и заголовок пункта (мигнёт и прокрутится в видимую область).
 * Гаснет через несколько секунд ([clear] вызывает экран настроек).
 */
internal object SettingsHighlight {
    var section by mutableStateOf<String?>(null)
        private set
    var item by mutableStateOf<String?>(null)
        private set
    var stamp by mutableIntStateOf(0)
        private set

    fun set(e: SettingsEntry) {
        section = e.section.takeIf { it.isNotBlank() }
        // Заголовок страницы целиком подсвечивать незачем: на неё мы и так перешли.
        item = e.title.takeIf { SettingsSearch.pageTitle(e.page) != it }
        stamp++
    }

    fun clear() { section = null; item = null }
}

/** Мигающая подложка у пункта, найденного поиском, и прокрутка к нему. */
@Composable
internal fun Modifier.searchFlash(): Modifier {
    val alpha = remember { Animatable(1f) }
    val req = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        // Раздел раскрывается с анимацией: ждём, пока пункт встанет на место.
        delay(380)
        runCatching { req.bringIntoView() }
        alpha.animateTo(0f, tween(2600))
    }
    val c = MaterialTheme.colorScheme.primary
    return this.bringIntoViewRequester(req).drawBehind {
        drawRoundRect(c.copy(alpha = 0.30f * alpha.value), cornerRadius = CornerRadius(12.dp.toPx()))
    }
}

/** Недавние запросы поиска по настройкам (SharedPreferences «settings_search»). */
internal object SettingsSearchHistory {
    private const val MAX = 8
    val items = mutableStateListOf<String>()
    private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("settings_search", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        prefs(ctx).getString("h", "").orEmpty().split('\n').filter { it.isNotBlank() }.take(MAX).let { items.addAll(it) }
    }

    fun add(ctx: Context, query: String) {
        load(ctx)
        val q = query.trim()
        if (q.length < 2) return
        items.removeAll { it.equals(q, ignoreCase = true) }
        items.add(0, q)
        while (items.size > MAX) items.removeAt(items.size - 1)
        save(ctx)
    }

    fun clear(ctx: Context) { items.clear(); save(ctx) }

    private fun save(ctx: Context) { prefs(ctx).edit().putString("h", items.joinToString("\n")).apply() }
}

/** Ряд недавних запросов под полем поиска: нажатие подставляет запрос. */
@Composable
internal fun SettingsSearchHistoryRow(ctx: Context, onPick: (String) -> Unit) {
    LaunchedEffect(Unit) { SettingsSearchHistory.load(ctx) }
    if (SettingsSearchHistory.items.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ms_history), null)
        SettingsSearchHistory.items.toList().forEach { q ->
            AssistChip(onClick = { onPick(q) }, label = { androidx.compose.material3.Text(q, maxLines = 1) })
        }
        TextButton(onClick = { SettingsSearchHistory.clear(ctx) }) { Text("Очистить") }
    }
}

/** Поле поиска над списком настроек. Результаты обновляются по мере ввода; [onSearch] — нажатие «поиск» на клавиатуре. */
@Composable
internal fun SettingsSearchField(query: String, onSearch: () -> Unit = {}, onChange: (String) -> Unit) {
    val kb = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Поиск по настройкам") },
        leadingIcon = { Icon(painterResource(R.drawable.ms_search), null) },
        trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { onChange("") }) { Icon(painterResource(R.drawable.ms_close), contentDescription = "Очистить") }
        }) else null,
        singleLine = true,
        shape = containerShape(28),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(); kb?.hide() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Результаты поиска: название, описание и путь до пункта; нажатие открывает нужную страницу настроек. */
@Composable
internal fun SettingsSearchResults(query: String, onOpen: (SettingsEntry) -> Unit) {
    val hits = remember(query, AppLang.code, ActiveProvider.value) { SettingsSearch.search(query) }
    if (hits.isEmpty()) {
        Text(
            "Ничего не найдено",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        return
    }
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(containerShape(28)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        hits.forEach { e ->
            val crumbs = listOfNotNull(SettingsSearch.pageTitle(e.page), e.section.takeIf { it.isNotBlank() && it != SettingsSearch.pageTitle(e.page) })
                .joinToString(" › ") { AppLang.tr(it) }
            ListItem(
                overlineContent = if (crumbs.isNotEmpty()) ({ Text(crumbs, maxLines = 1) }) else null,
                headlineContent = { Text(marked(AppLang.tr(e.title), query)) },
                supportingContent = if (e.desc.isNotBlank()) ({ Text(marked(AppLang.tr(e.desc), query)) }) else null,
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = colors,
                modifier = Modifier.clickable { onOpen(e) },
            )
        }
    }
}

/** Строка, в которой найденные слова запроса выделены. */
@Composable
private fun marked(text: String, query: String): AnnotatedString {
    val color = MaterialTheme.colorScheme.primaryContainer
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    val low = text.lowercase().replace('ё', 'е')
    val tokens = query.lowercase().replace('ё', 'е').split(' ', ',', ';').filter { it.isNotBlank() }
    // Выделяем только если длина не изменилась при приведении к нижнему регистру: иначе позиции съедут.
    if (low.length != text.length || tokens.isEmpty()) return AnnotatedString(text)
    val hit = BooleanArray(text.length)
    for (t in tokens) {
        var from = low.indexOf(t)
        while (from >= 0) {
            for (i in from until from + t.length) hit[i] = true
            from = low.indexOf(t, from + t.length)
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && hit[j] == hit[i]) j++
            if (hit[i]) withStyle(SpanStyle(background = color, color = onColor, fontWeight = FontWeight.SemiBold)) { append(text.substring(i, j)) }
            else append(text.substring(i, j))
            i = j
        }
    }
}
