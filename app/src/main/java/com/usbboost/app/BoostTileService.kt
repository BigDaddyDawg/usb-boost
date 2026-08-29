package com.usbboost.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BoostTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        val settings = BoostPrefs(this).load()
        if (settings.enabled) {
            BoostPrefs(this).save(settings.copy(enabled = false))
            BoostService.stop(this)
        } else {
            if (!BoostService.canStartForeground(this)) {
                val open = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_ENABLE, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pending = PendingIntent.getActivity(
                        this,
                        0,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pending)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(open)
                }
                return
            }
            BoostPrefs(this).save(settings.copy(enabled = true))
            BoostEngine.start(this)
            BoostService.startSafely(this)
        }
        refreshTile()
    }

    private fun refreshTile() {
        val on = BoostPrefs(this).load().enabled
        qsTile?.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile?.subtitle = if (on) getString(R.string.boost_is_on) else getString(R.string.boost_is_off)
        }
        qsTile?.updateTile()
    }
}
