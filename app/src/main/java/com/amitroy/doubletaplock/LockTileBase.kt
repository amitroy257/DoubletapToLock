package com.amitroy.doubletaplock

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Shared behaviour for the two lock tiles. Subclasses differ only in which permission
 * they depend on, which lock call they make, and how they label themselves.
 *
 * A tile reads as unavailable until its own permission is granted, so a tap that cannot
 * work looks wrong before you press it rather than after. Tapping an unavailable tile
 * opens setup instead of failing silently.
 */
abstract class LockTileBase : TileService() {

    protected val lockController by lazy { LockController(this) }

    protected abstract fun isReady(): Boolean
    protected abstract fun performLock(): LockResult
    protected abstract val iconRes: Int
    protected abstract val labelRes: Int
    protected abstract val readySubtitleRes: Int
    protected abstract val setupSubtitleRes: Int

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (!isReady()) {
            openSetup()
            return
        }

        when (performLock()) {
            LockResult.LOCKED -> Unit // screen is already off
            LockResult.FAILED, LockResult.NOT_CONFIGURED -> {
                refreshTile()
                openSetup()
            }
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val ready = isReady()
        tile.state = if (ready) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.label = getString(labelRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (ready) readySubtitleRes else setupSubtitleRes)
        }
        tile.updateTile()
    }

    // The Intent overload is deprecated but is the only one that exists below API 34,
    // and minSdk here is 28. Android 14 makes that form throw, so both branches are
    // required and each uses the correct call for its version.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openSetup() {
        val intent = Intent(this, SetupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
