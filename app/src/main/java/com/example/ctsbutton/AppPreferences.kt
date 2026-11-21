package com.example.ctsbutton

import android.content.Context

object AppPreferences {

    private const val PREFS_NAME = "cts_button_prefs"
    private const val KEY_FALLBACK_ENABLED = "fallback_enabled"
    private const val KEY_BUTTON_ON_LEFT = "button_on_left"
    private const val KEY_HAPTIC_ENABLED = "haptic_enabled"

    var isFallbackEnabled: Boolean = true
        private set

    var isButtonLeft: Boolean = false
        private set

    var isHapticEnabled: Boolean = true
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        isFallbackEnabled = prefs.getBoolean(KEY_FALLBACK_ENABLED, true)
        isButtonLeft = prefs.getBoolean(KEY_BUTTON_ON_LEFT, false)
        isHapticEnabled = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
    }

    fun setFallbackEnabled(context: Context, enabled: Boolean) {
        isFallbackEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FALLBACK_ENABLED, enabled)
            .apply()
    }

    fun setButtonLeft(context: Context, onLeft: Boolean) {
        isButtonLeft = onLeft
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BUTTON_ON_LEFT, onLeft)
            .apply()
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        isHapticEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAPTIC_ENABLED, enabled)
            .apply()
    }
}
