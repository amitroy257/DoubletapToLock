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

/**
 * Setup and settings. One section per tile, because the two tiles depend on different
 * permissions and either can work without the other.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var lockController: LockController

    private lateinit var a11yStatus: TextView
    private lateinit var adminStatus: TextView
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

        a11yStatus = findViewById(R.id.a11yStatus)
        adminStatus = findViewById(R.id.adminStatus)
        revokeAdminButton = findViewById(R.id.revokeAdminButton)

        // ── Screen Lock (fingerprint works) ──────────────────────────────────────────
        findViewById<Button>(R.id.enableA11yButton).setOnClickListener {
            toast(getString(R.string.accessibility_hint))
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.testScreenButton).setOnClickListener {
            report(lockController.lockNormal())
        }

        // ── Secure Lock (PIN required) ───────────────────────────────────────────────
        findViewById<Button>(R.id.grantAdminButton).setOnClickListener {
            openSettings(lockController.deviceAdminIntent())
        }

        findViewById<Button>(R.id.testSecureButton).setOnClickListener {
            report(lockController.lockSecure())
        }

        revokeAdminButton.setOnClickListener {
            lockController.removeDeviceAdmin()
            refreshStatus()
            toast(getString(R.string.admin_revoked))
        }

        // Android 13+ can place a tile for the user. Below that it is a manual drag, so
        // the buttons come off and a hint explains where to go instead.
        val addScreen = findViewById<Button>(R.id.addScreenTileButton)
        val addSecure = findViewById<Button>(R.id.addSecureTileButton)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addScreen.setOnClickListener {
                requestAddTile(
                    ScreenLockTileService::class.java,
                    R.string.tile_screen_label,
                    R.drawable.ic_tile_fingerprint
                )
            }
            addSecure.setOnClickListener {
                requestAddTile(
                    LockTileService::class.java,
                    R.string.tile_secure_label,
                    R.drawable.ic_tile_shield_lock
                )
            }
        } else {
            addScreen.visibility = View.GONE
            addSecure.visibility = View.GONE
            findViewById<TextView>(R.id.tileHint).visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestAddTile(service: Class<out LockTileBase>, labelRes: Int, iconRes: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val statusBarManager = getSystemService(StatusBarManager::class.java) ?: return
        runCatching {
            statusBarManager.requestAddTileService(
                ComponentName(this, service),
                getString(labelRes),
                Icon.createWithResource(this, iconRes),
                { it.run() },
                { /* result is informational; the system shows its own dialog */ }
            )
        }.onFailure { toast(getString(R.string.could_not_open_settings)) }
    }

    private fun refreshStatus() {
        val a11yOn = lockController.isAccessibilityConnected()
        setStatus(a11yStatus, a11yOn, R.string.status_ready_a11y, R.string.status_missing_a11y)

        val adminOn = lockController.isDeviceAdminActive()
        setStatus(adminStatus, adminOn, R.string.status_ready_admin, R.string.status_missing_admin)

        revokeAdminButton.visibility = if (adminOn) View.VISIBLE else View.GONE
    }

    private fun setStatus(view: TextView, ok: Boolean, okRes: Int, missingRes: Int) {
        view.text = getString(if (ok) okRes else missingRes)
        view.setTextColor(
            ContextCompat.getColor(this, if (ok) R.color.status_ok else R.color.status_warn)
        )
    }

    private fun report(result: LockResult) = when (result) {
        LockResult.LOCKED -> Unit // screen is already off
        LockResult.FAILED -> toast(getString(R.string.lock_failed))
        LockResult.NOT_CONFIGURED -> toast(getString(R.string.lock_not_configured))
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { toast(getString(R.string.could_not_open_settings)) }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
