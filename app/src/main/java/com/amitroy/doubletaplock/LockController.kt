package com.amitroy.doubletaplock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

enum class LockResult {
    /** The screen is now off. */
    LOCKED,

    /** Neither lock method has been granted yet — send the user to setup. */
    NOT_CONFIGURED,

    /** A method was granted but the call was rejected. */
    FAILED
}

/**
 * Turns the screen off and locks the device.
 *
 * Two routes, in preference order:
 *
 *  1. **Device Admin** → `DevicePolicyManager.lockNow()`. One toggle, works on every
 *     Android version, and is not affected by the "Restricted setting" wall that
 *     Android 13+ puts in front of accessibility services for sideloaded APKs. This is
 *     the recommended route for a phone you flash from Android Studio.
 *
 *  2. **Accessibility** → `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`, API 28+.
 *     No Device Admin needed, so nothing blocks a plain uninstall — but enabling it on a
 *     sideloaded build takes the extra "Allow restricted settings" detour.
 *
 * Note there is no third route. An app cannot *unlock* the device: while the phone is
 * locked no app is in the foreground to receive a tap, and dismissing the keyguard
 * requires the user's biometric or PIN. Double-tap-to-wake is a display-controller
 * feature the Pixel 6a does not expose to apps either. Lock is the achievable half.
 */
class LockController(private val context: Context) {

    private val dpm: DevicePolicyManager
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName
        get() = ComponentName(context.applicationContext, LockAdminReceiver::class.java)

    fun isDeviceAdminActive(): Boolean = dpm.isAdminActive(adminComponent)

    fun isAccessibilityConnected(): Boolean = LockAccessibilityService.isConnected

    fun isConfigured(): Boolean = isDeviceAdminActive() || isAccessibilityConnected()

    fun lock(): LockResult {
        if (isDeviceAdminActive()) {
            return try {
                dpm.lockNow()
                LockResult.LOCKED
            } catch (e: SecurityException) {
                LockResult.FAILED
            }
        }
        if (LockAccessibilityService.isConnected) {
            return if (LockAccessibilityService.lockNow()) LockResult.LOCKED else LockResult.FAILED
        }
        return LockResult.NOT_CONFIGURED
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
