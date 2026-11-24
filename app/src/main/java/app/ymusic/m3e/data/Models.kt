package app.ymusic.m3e.data

data class Account(
    val uid: Long,
    val login: String,
    val name: String,
    val hasPlus: Boolean,
    /** Ссылка на аватар из Яндекс ID (null, если токен не отдаёт аватар). */
    val avatarUrl: String? = null,
    /** Сервис аккаунта: Яндекс Музыка или VK Музыка. */
    val provider: Provider = Provider.YANDEX,
) {
    /** Идентификатор пользователя для путей API: uid, а если Яндекс его не вернул — логин. */
    val apiId: String get() = if (uid != 0L) uid.toString() else login.ifBlank { "me" }
}

data class Track(
    val id: String,
    val albumId: String?,
    val title: String,
    val artists: String,
    val coverUri: String?,
    val durationMs: Long,
    val artistRefs: List<ArtistRef> = emptyList(),
    val albumTitle: String? = null,
    /** Трек с пометкой 18+ (explicit). */
    val explicit: Boolean = false,
    /** content://-адрес файла на устройстве; null — трек из Яндекс Музыки. */
    val localUri: String? = null,
    /** Год выхода альбома (0 — неизвестен), лейбл и жанр — для информации о треке и тегов скачанного файла. */
    val year: Int = 0,
    val label: String? = null,
    val genre: String? = null,
    /** Приписка к названию: «минус», «a.k.a. …», «Remix» и т. п. (поле version в API Яндекс Музыки). */
    val version: String? = null,
    /** Только для файлов с устройства: имя файла, id альбома в MediaStore и номер трека в альбоме (диск·1000 + номер). */
    val fileName: String? = null,
    val localAlbumId: Long = 0L,
    val trackNo: Int = 0,
    /** Только для файлов с устройства: полный путь к файлу (MediaStore DATA) — по нему треки собираются в плейлист-папку. */
    val filePath: String? = null,
    /** Только VK: ключ доступа к аудио (нужен для чужих и групповых треков). */
    val accessKey: String? = null,
) {
    val isLocal: Boolean get() = localUri != null

    fun cover(size: Int = 400): String? =
        coverUri?.let { buildCoverUrl(it, size) }
}

/** Ссылка на артиста внутри трека: по ней открывается страница артиста. */
data class ArtistRef(val id: String, val name: String, val coverUri: String? = null) {
    fun cover(size: Int = 200): String? = coverUri?.let { buildCoverUrl(it, size) }
}

/** Артист: имя, обложка и популярные треки (треки пустые в результатах поиска). */
data class ArtistInfo(
    val id: String,
    val name: String,
    val coverUri: String?,
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumInfo> = emptyList(),
    /** Все фото исполнителя (allCovers из brief-info), основное — первым. Пусто, если сервер их не отдал. */
    val allCovers: List<String> = emptyList(),
    /** Слушателей за последний месяц (stats.lastMonthListeners из brief-info). 0 — сервис не отдал. */
    val monthlyListeners: Long = 0L,
    /** Подписчиков (у SoundCloud слушателей нет, есть только подписчики). 0 — сервис не отдал. */
    val followers: Long = 0L,
    /** Описание / биография исполнителя, если сервис его отдаёт. Пусто — блок «Об артисте» не показывается. */
    val description: String = "",
) {
    /** Описание для показа: без пробелов по краям; пусто, если сервис отдал заглушку («null», тире, пробелы) без единой буквы или цифры. */
    val bio: String
        get() = description.trim().takeIf { d -> d.any { it.isLetterOrDigit() } && !d.equals("null", ignoreCase = true) }.orEmpty()

    fun cover(size: Int = 400): String? =
        coverUri?.let { buildCoverUrl(it, size) }

    /** Ссылки на все фото исполнителя для просмотра; если отдельного списка нет — только основное. */
    fun photos(size: Int = 1000): List<String> =
        if (allCovers.isNotEmpty()) allCovers.map { buildCoverUrl(it, size) }
        else listOfNotNull(cover(size))
}

/** Альбом: обложка, исполнители, год и треки (треки пустые в списках и поиске). */
data class AlbumInfo(
    val id: String,
    val title: String,
    val artists: String = "",
    val artistRefs: List<ArtistRef> = emptyList(),
    val coverUri: String? = null,
    val year: Int = 0,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList(),
    /** Дата выхода как пришла с сервера (ISO, «2026-11-01T00:00:00+03:00»); пусто, если сервер её не отдал. */
    val releaseDate: String = "",
) {
    fun cover(size: Int = 400): String? =
        coverUri?.let { buildCoverUrl(it, size) }

    /** Дата выхода «гггг-мм-дд» для сортировки; если точной даты нет — 1 января года выхода; пусто — неизвестна. */
    val sortDate: String
        get() = releaseDate.take(10).ifBlank { if (year > 0) "$year-01-01" else "" }
}

data class PlaylistInfo(
    val kind: Int,
    val ownerUid: String,
    val title: String,
    val trackCount: Int,
    val coverUri: String?,
    /** Видимость плейлиста на сервере: "public" / "private"; null — неизвестно (старый офлайн-кэш). */
    val visibility: String? = null,
    /** Логин владельца (для красивой ссылки «поделиться»); в офлайн-кэше не хранится. */
    val ownerLogin: String? = null,
    /** Только VK: ключ доступа к плейлисту (нужен для чужих и алгоритмических плейлистов). */
    val accessKey: String? = null,
    /** Описание (VK: например, «Подборка на основе ваших предпочтений»). */
    val description: String? = null,
) {
    fun cover(size: Int = 400): String? =
        coverUri?.let { buildCoverUrl(it, size) }

    /** Публичный плейлист: его можно открыть по ссылке, поэтому им можно делиться. */
    val isPublic: Boolean get() = visibility == "public"
}

/** Собирает ссылку на обложку: подставляет размер вместо %% и добавляет схему, если её нет. */
internal fun buildCoverUrl(uri: String, size: Int): String {
    if (uri.startsWith("content://") || uri.startsWith("file://")) return uri
    val sized = uri.replace("%%", "${size}x$size")
    return if (sized.startsWith("http")) sized else "https://" + sized.removePrefix("//")
}

/** Настройки «Моей волны» (значения как в API Яндекс Музыки). */
data class WaveSettings(
    val diversity: String = "default",   // favorite | discover | popular | default
    val moodEnergy: String = "all",      // active | fun | calm | sad | all
    val language: String = "any",        // russian | not-russian | without-words | any
)

/** Можно ли редактировать плейлист: он свой и это не системный «Мне нравится» (kind = 3). */
fun PlaylistInfo.isEditableBy(acc: Account?): Boolean =
    acc != null && kind != 3 &&
        (ownerUid == acc.uid.toString() || ownerUid == acc.apiId || (acc.login.isNotBlank() && ownerUid == acc.login))

/** Ключ плейлиста для локальных данных (например, своей обложки). */
fun PlaylistInfo.coverKey(): String = "$ownerUid:$kind"

/** Подробная информация о треке (экран «Информация о треке»). Пустые поля не показываются. */
data class TrackInfo(
    val title: String,
    val version: String? = null,
    val artists: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val year: Int = 0,
    /** Дата выхода в виде «дд.мм.гггг» (или как пришла с сервера). */
    val releaseDate: String? = null,
    val genre: String? = null,
    val labels: List<String> = emptyList(),
    /** Авторы текста. */
    val writers: List<String> = emptyList(),
    /** Авторы музыки (композиторы). */
    val composers: List<String> = emptyList(),
    val explicit: Boolean = false,
    val durationMs: Long = 0,
)
