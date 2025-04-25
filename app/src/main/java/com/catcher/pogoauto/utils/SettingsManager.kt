package com.catcher.pogoauto.utils

import android.content.Context
import android.content.SharedPreferences
import com.catcher.pogoauto.utils.LogUtils

/**
 * Manager class for handling application settings persistence
 */
class SettingsManager(context: Context) {
    companion object {
        private const val TAG = "SettingsManager"
        private const val PREFS_NAME = "PogoAutoCatcherPrefs"

        // Hook settings keys
        private const val KEY_PERFECT_THROW_ENABLED = "perfect_throw_enabled"
        private const val KEY_PERFECT_THROW_CURVEBALL = "perfect_throw_curveball"
        private const val KEY_PERFECT_THROW_TYPE = "perfect_throw_type"
        private const val KEY_AUTO_WALK_ENABLED = "auto_walk_enabled"
        private const val KEY_AUTO_WALK_SPEED = "auto_walk_speed"
        private const val KEY_AUTO_CATCH_ENABLED = "auto_catch_enabled"
        private const val KEY_AUTO_CATCH_DELAY = "auto_catch_delay"
        private const val KEY_AUTO_CATCH_RETRY = "auto_catch_retry"
        private const val KEY_AUTO_CATCH_MAX_RETRIES = "auto_catch_max_retries"
        private const val KEY_AUTO_CATCH_BALL_TYPE = "auto_catch_ball_type"
        private const val KEY_INJECTION_DELAY = "injection_delay"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        LogUtils.i(TAG, "SettingsManager initialized")
    }

    // Perfect throw settings
    fun setPerfectThrowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERFECT_THROW_ENABLED, enabled).apply()
        LogUtils.i(TAG, "Saved perfect throw enabled: $enabled")
    }

    fun isPerfectThrowEnabled(): Boolean {
        return prefs.getBoolean(KEY_PERFECT_THROW_ENABLED, true)
    }

    fun setPerfectThrowCurveball(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERFECT_THROW_CURVEBALL, enabled).apply()
        LogUtils.i(TAG, "Saved perfect throw curveball: $enabled")
    }

    fun isPerfectThrowCurveball(): Boolean {
        return prefs.getBoolean(KEY_PERFECT_THROW_CURVEBALL, true)
    }

    fun setPerfectThrowType(type: String) {
        prefs.edit().putString(KEY_PERFECT_THROW_TYPE, type).apply()
        LogUtils.i(TAG, "Saved perfect throw type: $type")
    }

    fun getPerfectThrowType(): String {
        return prefs.getString(KEY_PERFECT_THROW_TYPE, "EXCELLENT") ?: "EXCELLENT"
    }

    // Auto walk settings
    fun setAutoWalkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_WALK_ENABLED, enabled).apply()
        LogUtils.i(TAG, "Saved auto walk enabled: $enabled")
    }

    fun isAutoWalkEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_WALK_ENABLED, false)
    }

    fun setAutoWalkSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_AUTO_WALK_SPEED, speed).apply()
        LogUtils.i(TAG, "Saved auto walk speed: $speed")
    }

    fun getAutoWalkSpeed(): Float {
        return prefs.getFloat(KEY_AUTO_WALK_SPEED, 1.0f)
    }

    // Auto catch settings
    fun setAutoCatchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CATCH_ENABLED, enabled).apply()
        LogUtils.i(TAG, "Saved auto catch enabled: $enabled")
    }

    fun isAutoCatchEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CATCH_ENABLED, false)
    }

    fun setAutoCatchDelay(delay: Int) {
        prefs.edit().putInt(KEY_AUTO_CATCH_DELAY, delay).apply()
        LogUtils.i(TAG, "Saved auto catch delay: $delay")
    }

    fun getAutoCatchDelay(): Int {
        return prefs.getInt(KEY_AUTO_CATCH_DELAY, 500)
    }

    fun setAutoCatchRetryOnEscape(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CATCH_RETRY, enabled).apply()
        LogUtils.i(TAG, "Saved auto catch retry on escape: $enabled")
    }

    fun isAutoCatchRetryOnEscape(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CATCH_RETRY, true)
    }

    fun setAutoCatchMaxRetries(maxRetries: Int) {
        prefs.edit().putInt(KEY_AUTO_CATCH_MAX_RETRIES, maxRetries).apply()
        LogUtils.i(TAG, "Saved auto catch max retries: $maxRetries")
    }

    fun getAutoCatchMaxRetries(): Int {
        return prefs.getInt(KEY_AUTO_CATCH_MAX_RETRIES, 3)
    }

    fun setAutoCatchBallType(ballType: String) {
        prefs.edit().putString(KEY_AUTO_CATCH_BALL_TYPE, ballType).apply()
        LogUtils.i(TAG, "Saved auto catch ball type: $ballType")
    }

    fun getAutoCatchBallType(): String {
        return prefs.getString(KEY_AUTO_CATCH_BALL_TYPE, "POKE_BALL") ?: "POKE_BALL"
    }

    // Injection settings
    fun setInjectionDelay(delay: Int) {
        prefs.edit().putInt(KEY_INJECTION_DELAY, delay).apply()
        LogUtils.i(TAG, "Saved injection delay: $delay")
    }

    fun getInjectionDelay(): Int {
        return prefs.getInt(KEY_INJECTION_DELAY, 0)
    }

    // Reset all settings to defaults
    fun resetToDefaults() {
        prefs.edit().apply {
            putBoolean(KEY_PERFECT_THROW_ENABLED, true)
            putBoolean(KEY_PERFECT_THROW_CURVEBALL, true)
            putString(KEY_PERFECT_THROW_TYPE, "EXCELLENT")
            putBoolean(KEY_AUTO_WALK_ENABLED, false)
            putFloat(KEY_AUTO_WALK_SPEED, 1.0f)
            putBoolean(KEY_AUTO_CATCH_ENABLED, false)
            putInt(KEY_AUTO_CATCH_DELAY, 500)
            putBoolean(KEY_AUTO_CATCH_RETRY, true)
            putInt(KEY_AUTO_CATCH_MAX_RETRIES, 3)
            putString(KEY_AUTO_CATCH_BALL_TYPE, "POKE_BALL")
            putInt(KEY_INJECTION_DELAY, 0)
            apply()
        }
        LogUtils.i(TAG, "Reset all settings to defaults")
    }
}
