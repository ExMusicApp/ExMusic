package app.ymusic.m3e.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

data class LocalTrackEdit(val artist: String? = null, val coverPath: String? = null, val title: String? = null)

object LocalTrackEdits {
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val map = HashMap<String, LocalTrackEdit>()

    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val p = app.getSharedPreferences("local_edits", Context.MODE_PRIVATE)
        prefs = p
        synchronized(map) {
            map.clear()
            runCatching {
                val raw = p.getString("items", null) ?: return@runCatching
                val o = JSONObject(raw)
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val e = o.optJSONObject(k) ?: continue
                    val cover = e.optString("cover").ifBlank { null }?.takeIf { File(it).exists() }
                    map[k] = LocalTrackEdit(e.optString("artist").ifBlank { null }, cover, e.optString("title").ifBlank { null })
                }
            }
        }
    }

    fun get(id: String): LocalTrackEdit? = synchronized(map) { map[id] }

    private fun coversDir(): File? = appContext?.let { File(it.filesDir, "local_covers").apply { mkdirs() } }

    private fun save() {
        val o = JSONObject()
        synchronized(map) {
            map.forEach { (k, v) ->
                if (v.artist == null && v.coverPath == null && v.title == null) return@forEach
                o.put(k, JSONObject().put("artist", v.artist ?: "").put("cover", v.coverPath ?: "").put("title", v.title ?: ""))
            }
        }
        prefs?.edit()?.putString("items", o.toString())?.apply()
        version++
    }

    fun update(id: String, artist: String?, newCover: Uri?, resetCover: Boolean, title: String? = null): Boolean {
        val ctx = appContext ?: return false
        val old = get(id)
        var coverPath = if (resetCover) null else old?.coverPath
        if (newCover != null) {
            val saved = copyCover(ctx, id, newCover) ?: return false
            old?.coverPath?.let { runCatching { File(it).delete() } }
            coverPath = saved
        } else if (resetCover) {
            old?.coverPath?.let { runCatching { File(it).delete() } }
        }
        val edit = LocalTrackEdit(artist?.trim()?.ifBlank { null }, coverPath, title?.trim()?.ifBlank { null })
        synchronized(map) {
            if (edit.artist == null && edit.coverPath == null && edit.title == null) map.remove(id) else map[id] = edit
        }
        save()
        return true
    }

    fun reset(id: String) {
        update(id, artist = null, newCover = null, resetCover = true)
    }

    private fun copyCover(ctx: Context, id: String, uri: Uri): String? = runCatching {
        val dir = coversDir() ?: return null
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1000) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val file = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + "_" + System.currentTimeMillis() + ".jpg")
        file.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        bmp.recycle()
        file.absolutePath
    }.getOrNull()
}
