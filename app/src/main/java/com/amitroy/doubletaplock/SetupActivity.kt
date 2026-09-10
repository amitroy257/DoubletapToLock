package com.amitroy.doubletaplock

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Setup and settings. This is what the app icon opens, and where the tile sends you if it isn't ready. */
class SetupActivity : AppCompatActivity() {

    private lateinit var lockController: LockController

    private lateinit var lockStatus: TextView
    private lateinit var revokeAdminButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        lockController = LockController(this)

        val root = findViewById<View>(R.id.setupRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        lockStatus = findViewById(R.id.lockStatus)
        revokeAdminButton = findViewById(R.id.revokeAdminButton)

        findViewById<Button>(R.id.grantAdminButton).setOnClickListener {
            runCatching { startActivity(lockController.deviceAdminIntent()) }
                .onFailure { toast(getString(R.string.could_not_open_settings)) }
        }

        findViewById<Button>(R.id.grantAccessibilityButton).setOnClickListener {
            toast(getString(R.string.accessibility_hint))
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .onFailure { toast(getString(R.string.could_not_open_settings)) }
        }

        revokeAdminButton.setOnClickListener {
            lockController.removeDeviceAdmin()
            refreshStatus()
            toast(getString(R.string.admin_revoked))
        }

        findViewById<Button>(R.id.testLockButton).setOnClickListener {
            when (lockController.lock()) {
                LockResult.LOCKED -> Unit
                LockResult.FAILED -> toast(getString(R.string.lock_failed))
                LockResult.NOT_CONFIGURED -> toast(getString(R.string.lock_not_configured))
            }
        }

        // Android 13+ can offer to place the tile for the user instead of making them
        // dig through the Quick Settings edit screen. Older versions do it by hand.
        val addTileButton = findViewById<Button>(R.id.addTileButton)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addTileButton.setOnClickListener { requestAddTile() }
        } else {
            addTileButton.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val statusBarManager = getSystemService(StatusBarManager::class.java) ?: return
        runCatching {
            statusBarManager.requestAddTileService(
                ComponentName(this, LockTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_lock_tile),
                { it.run() },
                { /* result is informational; the system shows its own dialog */ }
            )
        }.onFailure { toast(getString(R.string.could_not_open_settings)) }
    }

    private fun refreshStatus() {
        val adminActive = lockController.isDeviceAdminActive()
        val a11yActive = lockController.isAccessibilityConnected()
        lockStatus.text = when {
            adminActive -> getString(R.string.lock_status_admin)
            a11yActive -> getString(R.string.lock_status_a11y)
            else -> getString(R.string.lock_status_missing)
        }
        lockStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (adminActive || a11yActive) R.color.status_ok else R.color.status_warn
            )
        )
        revokeAdminButton.visibility = if (adminActive) View.VISIBLE else View.GONE
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
