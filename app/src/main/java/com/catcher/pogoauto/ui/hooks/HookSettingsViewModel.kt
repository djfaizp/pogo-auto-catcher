package com.catcher.pogoauto.ui.hooks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.catcher.pogoauto.utils.LogUtils

enum class PerfectThrowType {
    EXCELLENT,
    GREAT,
    NICE
}

class HookSettingsViewModel : ViewModel() {
    companion object {
        private const val TAG = "HookSettingsViewModel"
    }

    private val _title = MutableLiveData<String>().apply {
        value = "Hook Settings"
    }
    val title: LiveData<String> = _title

    // Perfect throw settings
    private val _isPerfectThrowEnabled = MutableLiveData<Boolean>().apply {
        value = true
    }
    val isPerfectThrowEnabled: LiveData<Boolean> = _isPerfectThrowEnabled

    private val _perfectThrowCurveball = MutableLiveData<Boolean>().apply {
        value = true
    }
    val perfectThrowCurveball: LiveData<Boolean> = _perfectThrowCurveball

    private val _perfectThrowExcellent = MutableLiveData<Boolean>().apply {
        value = true
    }
    val perfectThrowExcellent: LiveData<Boolean> = _perfectThrowExcellent

    private val _perfectThrowGreat = MutableLiveData<Boolean>().apply {
        value = false
    }
    val perfectThrowGreat: LiveData<Boolean> = _perfectThrowGreat

    private val _perfectThrowNice = MutableLiveData<Boolean>().apply {
        value = false
    }
    val perfectThrowNice: LiveData<Boolean> = _perfectThrowNice

    // Auto walk settings
    private val _isAutoWalkEnabled = MutableLiveData<Boolean>().apply {
        value = false
    }
    val isAutoWalkEnabled: LiveData<Boolean> = _isAutoWalkEnabled

    private val _autoWalkSpeed = MutableLiveData<Float>().apply {
        value = 1.0f
    }
    val autoWalkSpeed: LiveData<Float> = _autoWalkSpeed

    // Injection settings
    private val _injectionDelay = MutableLiveData<Int>().apply {
        value = 0
    }
    val injectionDelay: LiveData<Int> = _injectionDelay

    // Auto catch settings
    private val _isAutoCatchEnabled = MutableLiveData<Boolean>().apply {
        value = false
    }
    val isAutoCatchEnabled: LiveData<Boolean> = _isAutoCatchEnabled

    private val _autoCatchDelay = MutableLiveData<Int>().apply {
        value = 500
    }
    val autoCatchDelay: LiveData<Int> = _autoCatchDelay

    private val _autoCatchRetryOnEscape = MutableLiveData<Boolean>().apply {
        value = true
    }
    val autoCatchRetryOnEscape: LiveData<Boolean> = _autoCatchRetryOnEscape

    private val _autoCatchMaxRetries = MutableLiveData<Int>().apply {
        value = 3
    }
    val autoCatchMaxRetries: LiveData<Int> = _autoCatchMaxRetries

    init {
        LogUtils.i(TAG, "Hook settings initialized")
    }

    fun setPerfectThrowEnabled(enabled: Boolean) {
        _isPerfectThrowEnabled.value = enabled
        LogUtils.i(TAG, "Perfect throw ${if (enabled) "enabled" else "disabled"}")
    }

    fun setPerfectThrowCurveball(enabled: Boolean) {
        _perfectThrowCurveball.value = enabled
        LogUtils.i(TAG, "Perfect throw curveball ${if (enabled) "enabled" else "disabled"}")
    }

    fun setPerfectThrowType(type: PerfectThrowType) {
        when (type) {
            PerfectThrowType.EXCELLENT -> {
                _perfectThrowExcellent.value = true
                _perfectThrowGreat.value = false
                _perfectThrowNice.value = false
                LogUtils.i(TAG, "Perfect throw type set to EXCELLENT")
            }
            PerfectThrowType.GREAT -> {
                _perfectThrowExcellent.value = false
                _perfectThrowGreat.value = true
                _perfectThrowNice.value = false
                LogUtils.i(TAG, "Perfect throw type set to GREAT")
            }
            PerfectThrowType.NICE -> {
                _perfectThrowExcellent.value = false
                _perfectThrowGreat.value = false
                _perfectThrowNice.value = true
                LogUtils.i(TAG, "Perfect throw type set to NICE")
            }
        }
    }

    fun setAutoWalkEnabled(enabled: Boolean) {
        _isAutoWalkEnabled.value = enabled
        LogUtils.i(TAG, "Auto walk ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutoWalkSpeed(speed: Float) {
        _autoWalkSpeed.value = speed
        LogUtils.i(TAG, "Auto walk speed set to $speed m/s")
    }

    fun setAutoCatchEnabled(enabled: Boolean) {
        _isAutoCatchEnabled.value = enabled
        LogUtils.i(TAG, "Auto catch ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutoCatchDelay(delay: Int) {
        _autoCatchDelay.value = delay
        LogUtils.i(TAG, "Auto catch delay set to $delay ms")
    }

    fun setAutoCatchRetryOnEscape(enabled: Boolean) {
        _autoCatchRetryOnEscape.value = enabled
        LogUtils.i(TAG, "Auto catch retry on escape ${if (enabled) "enabled" else "disabled"}")
    }

    fun setInjectionDelay(delay: Int) {
        _injectionDelay.value = delay
        LogUtils.i(TAG, "Injection delay set to $delay ms")
    }

    fun setAutoCatchMaxRetries(maxRetries: Int) {
        _autoCatchMaxRetries.value = maxRetries
        LogUtils.i(TAG, "Auto catch max retries set to $maxRetries")
    }

    fun resetToDefaults() {
        // Reset perfect throw settings
        _isPerfectThrowEnabled.value = true
        _perfectThrowCurveball.value = true
        _perfectThrowExcellent.value = true
        _perfectThrowGreat.value = false
        _perfectThrowNice.value = false

        // Reset auto walk settings
        _isAutoWalkEnabled.value = false
        _autoWalkSpeed.value = 1.0f

        // Reset injection settings
        _injectionDelay.value = 0

        // Reset auto catch settings
        _isAutoCatchEnabled.value = false
        _autoCatchDelay.value = 500
        _autoCatchRetryOnEscape.value = true
        _autoCatchMaxRetries.value = 3

        LogUtils.i(TAG, "Hook settings reset to defaults")
    }
}
