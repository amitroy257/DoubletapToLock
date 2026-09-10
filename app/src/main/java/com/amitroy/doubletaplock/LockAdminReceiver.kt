package com.amitroy.doubletaplock

import android.app.admin.DeviceAdminReceiver

/**
 * Exists so the app can hold the `force-lock` policy (see res/xml/device_admin.xml) and
 * therefore call `DevicePolicyManager.lockNow()`. It claims no other policy — it cannot
 * wipe the device, read passwords, or change security settings.
 */
class LockAdminReceiver : DeviceAdminReceiver()
