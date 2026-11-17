package app.ymusic.m3e.recognition

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RecognizeTileService : TileService() {

    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.label = "Распознать музыку"
            if (Build.VERSION.SDK_INT >= 29) it.subtitle = "ExMusic"
            it.updateTile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = Intent(this, RecognizeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            startActivityAndCollapse(i)
        }
    }
}
