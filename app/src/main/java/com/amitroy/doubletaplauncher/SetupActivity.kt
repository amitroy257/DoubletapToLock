package com.amitroy.doubletaplauncher

import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.android.material.materialswitch.MaterialSwitch

/** Setup and settings. This is what the app icon opens, and what a long-press on the wallpaper opens. */
class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var lockController: LockController

    private lateinit var homeStatus: TextView
    private lateinit var lockStatus: TextView
    private lateinit var revokeAdminButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefs = Prefs(this)
        lockController = LockController(this)

        val root = findViewById<View>(R.id.setupRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        homeStatus = findViewById(R.id.homeStatus)
        lockStatus = findViewById(R.id.lockStatus)
        revokeAdminButton = findViewById(R.id.revokeAdminButton)

        findViewById<Button>(R.id.setHomeButton).setOnClickListener {
            // Opens Settings > Apps > Default apps > Home app.
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                .onFailure { toast(getString(R.string.could_not_open_settings)) }
        }

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

        findViewById<MaterialSwitch>(R.id.doubleTapSwitch).apply {
            isChecked = prefs.doubleTapEnabled
            setOnCheckedChangeListener { _, checked -> prefs.doubleTapEnabled = checked }
        }

        findViewById<MaterialSwitch>(R.id.hapticSwitch).apply {
            isChecked = prefs.hapticEnabled
            setOnCheckedChangeListener { _, checked -> prefs.hapticEnabled = checked }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val isHome = isDefaultHome()
        homeStatus.text = getString(
            if (isHome) R.string.home_status_ok else R.string.home_status_missing
        )
        homeStatus.setTextColor(statusColor(isHome))

        val adminActive = lockController.isDeviceAdminActive()
        val a11yActive = lockController.isAccessibilityConnected()
        lockStatus.text = when {
            adminActive -> getString(R.string.lock_status_admin)
            a11yActive -> getString(R.string.lock_status_a11y)
            else -> getString(R.string.lock_status_missing)
        }
        lockStatus.setTextColor(statusColor(adminActive || a11yActive))
        revokeAdminButton.visibility = if (adminActive) View.VISIBLE else View.GONE
    }

    private fun statusColor(ok: Boolean) = ContextCompat.getColor(
        this,
        if (ok) R.color.status_ok else R.color.status_warn
    )

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
