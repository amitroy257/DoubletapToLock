package com.amitroy.doubletaplock

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * A Quick Settings tile that locks the screen.
 *
 * ── Why a tile and not a gesture on the home screen ────────────────────────────
 *
 * Detecting a double tap on the Pixel Launcher's wallpaper is not possible from an
 * ordinary app. You would have to receive touch events over another app's window, which
 * needs an overlay — and an overlay that receives a touch also *consumes* it, breaking
 * every normal tap underneath. FLAG_NOT_TOUCHABLE forwards touches but then delivers
 * none to you. That is an anti-tapjacking boundary in Android, not an oversight.
 *
 * A tile sidesteps the whole problem: it is a surface the system hands us, so there is
 * nothing underneath to break and no ambiguity about what the user meant. One tap, no
 * double-tap timeout, works from inside any app rather than only on the home screen.
 */
class LockTileService : TileService() {

    private val lockController by lazy { LockController(this) }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (!lockController.isConfigured()) {
            openSetup()
            return
        }

        when (lockController.lock()) {
            LockResult.LOCKED -> Unit // screen is already off
            LockResult.FAILED, LockResult.NOT_CONFIGURED -> {
                refreshTile()
                openSetup()
            }
        }
    }

    /**
     * The tile reads as unavailable until a lock method is granted, so a tap that cannot
     * work looks wrong before you press it rather than after.
     */
    private fun refreshTile() {
        val tile = qsTile ?: return
        val ready = lockController.isConfigured()
        tile.state = if (ready) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_lock_tile)
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (ready) R.string.tile_subtitle_ready else R.string.tile_subtitle_setup
            )
        }
        tile.updateTile()
    }

    // The Intent overload is deprecated but is the only one that exists below API 34,
    // and minSdk here is 28. Each branch uses the correct call for its version.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openSetup() {
        val intent = Intent(this, SetupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Android 14 made the Intent overload throw UnsupportedOperationException;
        // a PendingIntent is now the only accepted form.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
