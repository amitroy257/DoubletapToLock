package com.amitroy.doubletaplock

/**
 * Everyday lock — accessibility `GLOBAL_ACTION_LOCK_SCREEN`, the power-button equivalent.
 * The keyguard comes up in its ordinary state, so **fingerprint unlocks it**.
 *
 * Requires the accessibility service to be enabled, which on a sideloaded build means
 * clearing the "Allow restricted settings" wall first. That extra step is the whole cost
 * of not going through Device Admin — and it is what keeps biometrics working.
 */
class ScreenLockTileService : LockTileBase() {
    override fun isReady() = lockController.isAccessibilityConnected()
    override fun performLock() = lockController.lockNormal()
    override val labelRes = R.string.tile_screen_label
    override val readySubtitleRes = R.string.tile_screen_ready
    override val setupSubtitleRes = R.string.tile_screen_setup
}
