package com.amitroy.doubletaplauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional alternative to Device Admin.
 *
 * Contrast this with the old `TapLockService`: this one does not inspect accessibility
 * events, does not try to work out which app is in the foreground, and never adds a
 * window to the WindowManager. It is a one-line capability provider — the only reason it
 * exists is that `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` can only be called from
 * inside an AccessibilityService.
 *
 * All the screen-state knowledge that the old service tried and failed to infer now comes
 * for free from [HomeActivity]'s own lifecycle.
 */
class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Intentionally empty. Nothing here needs to observe the screen. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockAccessibilityService? = null

        val isConnected: Boolean get() = instance != null

        fun lockNow(): Boolean {
            val service = instance ?: return false
            return runCatching {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }.getOrDefault(false)
        }
    }
}
