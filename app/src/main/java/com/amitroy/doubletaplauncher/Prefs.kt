package com.amitroy.doubletaplauncher

import android.content.Context

/** Small typed wrapper over SharedPreferences. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var doubleTapEnabled: Boolean
        get() = sp.getBoolean(KEY_DOUBLE_TAP, true)
        set(value) = sp.edit().putBoolean(KEY_DOUBLE_TAP, value).apply()

    var hapticEnabled: Boolean
        get() = sp.getBoolean(KEY_HAPTIC, true)
        set(value) = sp.edit().putBoolean(KEY_HAPTIC, value).apply()

    /**
     * Dock contents as `package/activity` keys. Stored as one newline-joined string
     * rather than a StringSet because a Set does not preserve order.
     */
    var favourites: List<String>
        get() = sp.getString(KEY_FAVOURITES, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
        set(value) = sp.edit().putString(KEY_FAVOURITES, value.joinToString("\n")).apply()

    /** @return false if the dock is already full. */
    fun addFavourite(key: String): Boolean {
        val current = favourites.toMutableList()
        if (key in current) return true
        if (current.size >= MAX_DOCK) return false
        current += key
        favourites = current
        return true
    }

    fun removeFavourite(key: String) {
        favourites = favourites - key
    }

    companion object {
        const val MAX_DOCK = 5
        private const val KEY_DOUBLE_TAP = "double_tap_enabled"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_FAVOURITES = "favourites"
    }
}
