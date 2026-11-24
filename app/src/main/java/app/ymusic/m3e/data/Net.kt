package app.ymusic.m3e.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Есть ли сейчас подключение к интернету (Wi-Fi, мобильная сеть, Ethernet). */
object Net {
    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
