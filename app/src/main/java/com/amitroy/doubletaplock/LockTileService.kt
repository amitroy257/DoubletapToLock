package com.amitroy.doubletaplock

/**
 * Secure lock — Device Admin. Forces PIN/pattern/password on the next unlock because
 * `lockNow()` sets `STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW`; biometrics are refused.
 * See [LockController] for why that cannot be switched off.
 *
 * Use [ScreenLockTileService] for the everyday, fingerprint-friendly lock.
 */
class LockTileService : LockTileBase() {
    override fun isReady() = lockController.isDeviceAdminActive()
    override fun performLock() = lockController.lockSecure()
    override val labelRes = R.string.tile_secure_label
    override val readySubtitleRes = R.string.tile_secure_ready
    override val setupSubtitleRes = R.string.tile_secure_setup
}
