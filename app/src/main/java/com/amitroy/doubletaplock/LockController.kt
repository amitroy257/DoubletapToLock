package com.amitroy.doubletaplock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

enum class LockResult {
    /** The screen is now off. */
    LOCKED,

    /** The permission this route needs has not been granted — send the user to setup. */
    NOT_CONFIGURED,

    /** The permission was granted but the call was rejected. */
    FAILED
}

/**
 * Two ways to lock the screen. They are **not** interchangeable: they differ in what the
 * phone demands from you on the way back in.
 *
 * ── Normal lock — accessibility, [lockNormal] ─────────────────────────────────────────
 *
 * `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`, API 28+. Equivalent to pressing the
 * power button: screen off, keyguard in its ordinary state, **fingerprint works**.
 *
 * ── Secure lock — Device Admin, [lockSecure] ──────────────────────────────────────────
 *
 * `DevicePolicyManager.lockNow()` locks *and* sets the framework's
 * `STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW` flag. That flag tells the keyguard to accept
 * only PIN, pattern or password on the next unlock and to refuse biometrics — the same
 * state the phone enters after a reboot or the periodic strong-auth timeout.
 *
 * This is deliberate on Android's part: an admin-initiated lock is treated as a security
 * event. It cannot be opted out of from the app side. There is no flag to `lockNow()`
 * that suppresses it, and it is set by the system rather than by this code, so the only
 * way to get a biometric-friendly lock is to not use Device Admin for it.
 *
 * ── Why keep both ─────────────────────────────────────────────────────────────────────
 *
 * The PIN-only behaviour is a feature when you actually want it — handing the phone over,
 * or putting it down somewhere you would rather a sleeping fingerprint could not open it.
 * Each route is wired to its own Quick Settings tile so the choice is made at tap time.
 *
 * Note there is no route that *unlocks*. While the phone is locked no app is foreground
 * to receive input, and dismissing the keyguard requires the user's own biometric or PIN.
 */
class LockController(private val context: Context) {

    private val dpm: DevicePolicyManager
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName
        get() = ComponentName(context.applicationContext, LockAdminReceiver::class.java)

    fun isDeviceAdminActive(): Boolean = dpm.isAdminActive(adminComponent)

    fun isAccessibilityConnected(): Boolean = LockAccessibilityService.isConnected

    fun isConfigured(): Boolean = isDeviceAdminActive() || isAccessibilityConnected()

    /** Power-button-equivalent lock. Fingerprint unlocks afterwards. Needs the a11y service. */
    fun lockNormal(): LockResult {
        if (!LockAccessibilityService.isConnected) return LockResult.NOT_CONFIGURED
        return if (LockAccessibilityService.lockNow()) LockResult.LOCKED else LockResult.FAILED
    }

    /** Admin lock. Forces PIN/pattern/password on the next unlock. Needs Device Admin. */
    fun lockSecure(): LockResult {
        if (!isDeviceAdminActive()) return LockResult.NOT_CONFIGURED
        return try {
            dpm.lockNow()
            LockResult.LOCKED
        } catch (e: SecurityException) {
            LockResult.FAILED
        }
    }

    /** Consent screen for activating Device Admin. */
    fun deviceAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.admin_explanation)
            )
        }

    /**
     * Device Admin must be deactivated before Android will let the app be uninstalled,
     * so the setup screen exposes this directly.
     */
    fun removeDeviceAdmin() {
        if (isDeviceAdminActive()) {
            runCatching { dpm.removeActiveAdmin(adminComponent) }
        }
    }
}
