@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ymusic.m3e.AppViewModel
import app.ymusic.m3e.R
import app.ymusic.m3e.data.ScProfile
import coil3.compose.AsyncImage

/** Ключи сервисов в шторке выбора (передаются в [AppViewModel.startAddAccount]). */
internal const val PICK_YANDEX = "yandex"
internal const val PICK_VK = "vk"
internal const val PICK_SOUNDCLOUD = "soundcloud"
internal const val PICK_SPOTIFY = "spotify"

/**
 * Нижняя шторка «С чем войти»: Яндекс.Музыка, VK Музыка, SoundCloud, Spotify.
 * Открывается кнопкой «Дальше» на первой странице и кнопкой «Добавить аккаунт» в настройках.
 */
@Composable
internal fun ProviderPickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(
                "Выберите платформу",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 12.dp),
            )
            ProviderRow("Яндекс.Музыка", R.drawable.logo_yandex, Modifier.size(28.dp)) { onPick(PICK_YANDEX) }
            ProviderRow("VK Музыка", R.drawable.logo_vk, Modifier.size(28.dp)) { onPick(PICK_VK) }
            ProviderRow("SoundCloud", R.drawable.logo_soundcloud, Modifier.width(36.dp).height(16.dp)) { onPick(PICK_SOUNDCLOUD) }
            // Вход в Spotify временно закрыт: строка неактивна и подписана «В разработке».
            ProviderRow("Spotify", R.drawable.logo_spotify, Modifier.size(28.dp), enabled = false, note = "В разработке") { onPick(PICK_SPOTIFY) }
        }
    }
}

@Composable
private fun ProviderRow(title: String, logo: Int, logoModifier: Modifier, enabled: Boolean = true, note: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = note?.let { n -> { Text(n, style = MaterialTheme.typography.bodyMedium) } },
        // Логотип без подложки: однотонный, цвета темы (как значки Material 3 Expressive).
        leadingContent = {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(logo), contentDescription = null, modifier = logoModifier, tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        trailingContent = if (enabled) ({ Icon(painterResource(R.drawable.ms_chevron_right), null) }) else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.padding(vertical = 3.dp).clip(rounded(20)).alpha(if (enabled) 1f else 0.5f).clickable(enabled = enabled, onClick = onClick),
    )
}

private fun openUrl(ctx: android.content.Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(ctx, android.net.Uri.parse(url))
}

/**
 * Вход в SoundCloud: открывается страница SoundCloud в браузере, после подтверждения он возвращает в приложение
 * (ymusicm3e://soundcloud), и вход завершается сам.
 */
@Composable
internal fun SoundCloudLoginPage(vm: AppViewModel, onBack: () -> Unit) {
    val ui by vm.scLogin.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var opened by rememberSaveable { mutableStateOf(false) }

    fun start() {
        openUrl(ctx, vm.scBeginLogin())
    }
    fun back() { vm.scResetLogin(); onBack() }
    BackHandler { back() }
    // Страница входа открывается сразу при выборе SoundCloud (один раз, повторно — кнопкой).
    LaunchedEffect(Unit) {
        if (!opened) { opened = true; start() }
    }

    Box(Modifier.fillMaxSize()) {
        IconButton(
            onClick = { back() },
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
        ) { Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = "Назад") }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 72.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Вход через SoundCloud", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    ui.busy -> "Завершаем вход…"
                    else -> "Откроется страница SoundCloud. Войдите и разрешите доступ — приложение вернётся сюда само. Пароль в приложение не передаётся"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { start() },
                enabled = !ui.busy,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(56.dp),
                shape = rounded(28),
            ) { Text(if (ui.busy) "Входим…" else if (ui.waiting) "Открыть SoundCloud снова" else "Войти через SoundCloud") }
            ui.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 1234 → «1,2K», 2 500 000 → «2,5M». */
internal fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M").replace('.', ',')
    n >= 10_000 -> (n / 1000).toString() + "K"
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000f).replace(".0K", "K").replace('.', ',')
    else -> n.toString()
}

/** Карточка профиля SoundCloud: аватар, ник, имя, город, тариф, счётчики и описание. */
@Composable
internal fun SoundCloudProfileCard(p: ScProfile, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Surface(
        shape = containerShape(28),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ms_person), null, Modifier.size(36.dp))
                        p.avatarUrl?.let {
                            AsyncImage(
                                model = it, contentDescription = "Аватар",
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (p.permalink.isNotBlank()) {
                        Text(
                            "@" + p.permalink, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val sub = listOf(p.fullName.takeIf { it.isNotBlank() && it != p.username }, listOf(p.city, p.country).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null })
                        .filterNotNull().joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(
                            sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            ScStatsRow(p)
            if (p.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    p.description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis,
                )
            }
            if (p.profileUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { openUrl(ctx, p.profileUrl) }, shape = rounded(24)) {
                    Icon(painterResource(R.drawable.ms_open_in_new), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть в SoundCloud")
                }
            }
        }
    }
}

/** Счётчики профиля SoundCloud: подписки, треки и подписчики по центру. */
@Composable
internal fun SoundCloudStats(p: ScProfile, modifier: Modifier = Modifier) {
    ScStatsRow(p, modifier)
}

/** Три счётчика по центру в одну строку: подписки, треки, подписчики. */
@Composable
private fun ScStatsRow(p: ScProfile, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        ScStat(compactCount(p.followings), "подписок", Modifier.weight(1f))
        ScStat(compactCount(p.tracks), "треков", Modifier.weight(1f))
        ScStat(compactCount(p.followers), "подписчиков", Modifier.weight(1f))
    }
}

@Composable
private fun ScStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = rounded(16), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        }
    }
}


// ---------- Загрузка своего трека на SoundCloud ----------

/** Имя выбранного файла (для подстановки в название трека). */
private fun pickedFileName(ctx: android.content.Context, uri: android.net.Uri): String {
    var name = ""
    runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = c.getString(i).orEmpty()
            }
        }
    }
    return name.ifBlank { uri.lastPathSegment.orEmpty() }.substringBeforeLast('.')
}

/**
 * Кнопка «Загрузить трек на SoundCloud»: выбор аудиофайла, затем окно с названием и настройками.
 * Пока идёт загрузка, показывается полоса прогресса.
 */
@Composable
internal fun ScUploadSection(vm: AppViewModel, uploading: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var picked by remember { mutableStateOf<android.net.Uri?>(null) }
    var pickedName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { picked = uri; pickedName = pickedFileName(ctx, uri) }
    }
    Column(modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = { picker.launch(arrayOf("audio/*")) },
            enabled = !uploading,
            shape = rounded(24),
        ) {
            Icon(painterResource(R.drawable.ms_add), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (uploading) "Загрузка на SoundCloud…" else "Загрузить трек на SoundCloud")
        }
        if (uploading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                if (progress >= 1f) "Файл отправлен, SoundCloud его обрабатывает…" else "${(progress * 100).toInt()}% · не закрывайте приложение",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    picked?.let { uri ->
        ScUploadDialog(
            initialTitle = pickedName,
            onDismiss = { picked = null },
            onConfirm = { title, description, genre, tags, isPublic, cover ->
                picked = null
                vm.uploadScTrack(uri, title, description, genre, tags, isPublic, cover)
            },
        )
    }
}

@Composable
private fun ScUploadDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, genre: String, tags: String, isPublic: Boolean, cover: android.net.Uri?) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var cover by remember { mutableStateOf<android.net.Uri?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) cover = uri }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Загрузить на SoundCloud") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Обложка: нажмите на квадрат, чтобы выбрать картинку.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = rounded(16), color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(88.dp).clickable { coverPicker.launch(arrayOf("image/*")) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (cover != null) {
                                AsyncImage(model = cover, contentDescription = "Обложка", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(painterResource(R.drawable.ms_add), null, Modifier.size(28.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Обложка", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (cover == null) "Не выбрана — будет аватар профиля" else "Выбрана",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (cover != null) TextButton(onClick = { cover = null }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Убрать") }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("Название") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Описание (необязательно)") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = genre, onValueChange = { genre = it }, singleLine = true,
                    label = { Text("Жанр (необязательно)") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it }, singleLine = true,
                    label = { Text("Теги через пробел (необязательно)") }, modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().clip(rounded(16)).clickable { isPublic = !isPublic }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Виден всем", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (isPublic) "Трек появится в вашем профиле и в поиске SoundCloud" else "Трек увидите только вы и те, кому дадите ссылку",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    ExpressiveSwitch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                Text(
                    "Загружайте только музыку, на которую у вас есть права.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), description.trim(), genre.trim(), tags.trim(), isPublic, cover) },
                enabled = title.isNotBlank(),
            ) { Text("Загрузить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}


/**
 * Окно «Изменить» для своего трека или альбома SoundCloud. Поля подгружаются с сервера; у трека можно ещё заменить обложку.
 * [onSave] получает название, описание, жанр, теги, видимость и новую обложку (null — не менять).
 */
@Composable
internal fun ScEditDialog(
    vm: AppViewModel, id: String, album: Boolean, onDismiss: () -> Unit,
    onSave: (title: String, description: String, genre: String, tags: String, isPublic: Boolean, cover: android.net.Uri?) -> Unit,
) {
    var loaded by remember(id) { mutableStateOf<app.ymusic.m3e.data.SoundCloudApi.ScEditable?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        val r = vm.scEditable(id, album)
        if (r == null) failed = true else loaded = r
    }
    val e = loaded
    var title by remember(e) { mutableStateOf(e?.title.orEmpty()) }
    var description by remember(e) { mutableStateOf(e?.description.orEmpty()) }
    var genre by remember(e) { mutableStateOf(e?.genre.orEmpty()) }
    var tags by remember(e) { mutableStateOf(e?.tags.orEmpty()) }
    var isPublic by remember(e) { mutableStateOf(e?.isPublic ?: true) }
    var cover by remember { mutableStateOf<android.net.Uri?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) cover = uri }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (album) "Изменить альбом" else "Изменить трек") },
        text = {
            when {
                failed -> Text("Не удалось загрузить данные. Проверьте интернет и попробуйте ещё раз", color = MaterialTheme.colorScheme.error)
                e == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!album) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = rounded(16), color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(88.dp).clickable { coverPicker.launch(arrayOf("image/*")) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val shown: Any? = cover ?: e.coverUrl
                                    if (shown != null) AsyncImage(model = shown, contentDescription = "Обложка", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else Icon(painterResource(R.drawable.ms_add), null, Modifier.size(28.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Обложка", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (cover == null) "Нажмите на квадрат, чтобы заменить" else "Новая обложка выбрана",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (cover != null) TextButton(onClick = { cover = null }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Отменить замену") }
                            }
                        }
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = genre, onValueChange = { genre = it }, singleLine = true, label = { Text("Жанр") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, singleLine = true, label = { Text("Теги через пробел") }, modifier = Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth().clip(rounded(16)).clickable { isPublic = !isPublic }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Виден всем", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (isPublic) "Виден в профиле и в поиске SoundCloud" else "Только вы и те, у кого есть секретная ссылка",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        ExpressiveSwitch(checked = isPublic, onCheckedChange = { isPublic = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), description.trim(), genre.trim(), tags.trim(), isPublic, cover); onDismiss() },
                enabled = e != null && title.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
