package app.ymusic.m3e.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class SessionKind { Queue, Wave }

data class SessionPosition(
    val index: Int,
    val positionMs: Long,
    val kind: SessionKind,
    val waveBatchId: String?,
    val shuffle: Boolean,
    val waveStation: String? = null,
)

class PlaybackStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("playback_store", Context.MODE_PRIVATE)

    fun loadHistory(owner: String?): List<Track> {
        if (owner == null || prefs.getString("history_owner", null) != owner) return emptyList()
        return decodeTracks(prefs.getString("history", null))
    }

    fun saveHistory(owner: String?, tracks: List<Track>) {
        if (owner == null) return
        prefs.edit()
            .putString("history_owner", owner)
            .putString("history", encodeTracks(tracks))
            .apply()
    }

    fun loadWave(owner: String?): List<Track> {
        if (owner == null || prefs.getString("wave_owner", null) != owner) return emptyList()
        return decodeTracks(prefs.getString("wave_history", null))
    }

    fun saveWave(owner: String?, tracks: List<Track>) {
        if (owner == null) return
        prefs.edit()
            .putString("wave_owner", owner)
            .putString("wave_history", encodeTracks(tracks))
            .apply()
    }

    fun saveQueue(owner: String?, tracks: List<Track>) {
        if (owner == null) return
        prefs.edit()
            .putString("queue_owner", owner)
            .putString("queue", encodeTracks(tracks))
            .apply()
    }

    fun savePosition(p: SessionPosition) {
        prefs.edit()
            .putInt("pos_index", p.index)
            .putLong("pos_ms", p.positionMs)
            .putString("pos_kind", p.kind.name)
            .putString("pos_batch", p.waveBatchId)
            .putBoolean("pos_shuffle", p.shuffle)
            .putString("pos_station", p.waveStation)
            .apply()
    }

    class SessionLog(val label: String, val tracks: List<Track>)

    fun saveSessionLog(owner: String?, label: String, tracks: List<Track>) {
        if (owner == null) return
        prefs.edit()
            .putString("session_owner", owner)
            .putString("session_label", label)
            .putString("session_tracks", encodeTracks(tracks))
            .apply()
    }

    fun loadSessionLog(owner: String?): SessionLog {
        if (owner == null || prefs.getString("session_owner", null) != owner) return SessionLog("", emptyList())
        return SessionLog(prefs.getString("session_label", "").orEmpty(), decodeTracks(prefs.getString("session_tracks", null)))
    }

    fun loadQueue(owner: String?): List<Track> {
        if (owner == null || prefs.getString("queue_owner", null) != owner) return emptyList()
        return decodeTracks(prefs.getString("queue", null))
    }

    fun loadPosition(): SessionPosition = SessionPosition(
        index = prefs.getInt("pos_index", 0),
        positionMs = prefs.getLong("pos_ms", 0L),
        kind = SessionKind.entries.firstOrNull { it.name == prefs.getString("pos_kind", null) } ?: SessionKind.Queue,
        waveBatchId = prefs.getString("pos_batch", null),
        shuffle = prefs.getBoolean("pos_shuffle", false),
        waveStation = prefs.getString("pos_station", null),
    )

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        fun encodeTracks(tracks: List<Track>): String {
            val arr = JSONArray()
            tracks.forEach { arr.put(encodeTrack(it)) }
            return arr.toString()
        }

        fun decodeTracks(json: String?): List<Track> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { runCatching { decodeTrack(arr.getJSONObject(it)) }.getOrNull() }
            }.getOrDefault(emptyList())
        }

        private fun encodeTrack(t: Track) = JSONObject().apply {
            put("id", t.id)
            put("albumId", t.albumId)
            put("title", t.title)
            put("artists", t.artists)
            put("cover", t.coverUri)
            put("dur", t.durationMs)
            put("albumTitle", t.albumTitle)
            put("explicit", t.explicit)
            put("local", t.localUri)
            put("file", t.fileName)
            put("lalbum", t.localAlbumId)
            put("tno", t.trackNo)
            put("year", t.year)
            put(
                "refs",
                JSONArray().apply {
                    t.artistRefs.forEach { r ->
                        put(JSONObject().put("id", r.id).put("name", r.name).put("cover", r.coverUri))
                    }
                },
            )
        }

        private fun JSONObject.optStr(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

        private fun decodeTrack(o: JSONObject): Track {
            val refs = o.optJSONArray("refs")?.let { a ->
                (0 until a.length()).map { i ->
                    val r = a.getJSONObject(i)
                    ArtistRef(r.getString("id"), r.optString("name"), r.optStr("cover"))
                }
            }.orEmpty()
            return Track(
                id = o.getString("id"),
                albumId = o.optStr("albumId"),
                title = o.optString("title"),
                artists = o.optString("artists"),
                coverUri = o.optStr("cover"),
                durationMs = o.optLong("dur"),
                artistRefs = refs,
                albumTitle = o.optStr("albumTitle"),
                explicit = o.optBoolean("explicit"),
                localUri = o.optStr("local"),
                year = o.optInt("year"),
                fileName = o.optStr("file"),
                localAlbumId = o.optLong("lalbum"),
                trackNo = o.optInt("tno"),
            )
        }
    }
}
