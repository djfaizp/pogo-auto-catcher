package com.catcher.pogoauto.ui.hooks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.catcher.pogoauto.utils.LogUtils
import com.catcher.pogoauto.utils.SettingsManager

enum class PerfectThrowType {
    EXCELLENT,
    GREAT,
    NICE
}

enum class PokeBallType {
    POKE_BALL,
    GREAT_BALL,
    ULTRA_BALL
}

class HookSettingsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "HookSettingsViewModel"
    }

    /**
     * Factory for creating a HookSettingsViewModel with a constructor that takes an Application
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HookSettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HookSettingsViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    // Settings manager for persistent storage
    private val settingsManager = SettingsManager(application.applicationContext)

    private val _title = MutableLiveData<String>().apply {
        value = "Hook Settings"
    }
    val title: LiveData<String> = _title

    // Perfect throw settings
    private val _isPerfectThrowEnabled = MutableLiveData<Boolean>()
    val isPerfectThrowEnabled: LiveData<Boolean> = _isPerfectThrowEnabled

    private val _perfectThrowCurveball = MutableLiveData<Boolean>()
    val perfectThrowCurveball: LiveData<Boolean> = _perfectThrowCurveball

    private val _perfectThrowExcellent = MutableLiveData<Boolean>()
    val perfectThrowExcellent: LiveData<Boolean> = _perfectThrowExcellent

    private val _perfectThrowGreat = MutableLiveData<Boolean>()
    val perfectThrowGreat: LiveData<Boolean> = _perfectThrowGreat

    private val _perfectThrowNice = MutableLiveData<Boolean>()
    val perfectThrowNice: LiveData<Boolean> = _perfectThrowNice

    // Auto walk settings
    private val _isAutoWalkEnabled = MutableLiveData<Boolean>()
    val isAutoWalkEnabled: LiveData<Boolean> = _isAutoWalkEnabled

    private val _autoWalkSpeed = MutableLiveData<Float>()
    val autoWalkSpeed: LiveData<Float> = _autoWalkSpeed

    // Injection settings
    private val _injectionDelay = MutableLiveData<Int>()
    val injectionDelay: LiveData<Int> = _injectionDelay

    // Auto catch settings
    private val _isAutoCatchEnabled = MutableLiveData<Boolean>()
    val isAutoCatchEnabled: LiveData<Boolean> = _isAutoCatchEnabled

    private val _autoCatchDelay = MutableLiveData<Int>()
    val autoCatchDelay: LiveData<Int> = _autoCatchDelay

    private val _autoCatchRetryOnEscape = MutableLiveData<Boolean>()
    val autoCatchRetryOnEscape: LiveData<Boolean> = _autoCatchRetryOnEscape

    private val _autoCatchMaxRetries = MutableLiveData<Int>()
    val autoCatchMaxRetries: LiveData<Int> = _autoCatchMaxRetries

    // Pokéball type settings
    private val _pokeBallType = MutableLiveData<PokeBallType>()
    val pokeBallType: LiveData<PokeBallType> = _pokeBallType

    init {
        LogUtils.i(TAG, "Hook settings initialized")
        loadSavedSettings()
    }

    /**
     * Load settings from SharedPreferences
     */
    private fun loadSavedSettings() {
        // Load perfect throw settings
        _isPerfectThrowEnabled.value = settingsManager.isPerfectThrowEnabled()
        _perfectThrowCurveball.value = settingsManager.isPerfectThrowCurveball()

        // Load perfect throw type
        val throwType = settingsManager.getPerfectThrowType()
        when (throwType) {
            "EXCELLENT" -> {
                _perfectThrowExcellent.value = true
                _perfectThrowGreat.value = false
                _perfectThrowNice.value = false
            }
            "GREAT" -> {
                _perfectThrowExcellent.value = false
                _perfectThrowGreat.value = true
                _perfectThrowNice.value = false
            }
            "NICE" -> {
                _perfectThrowExcellent.value = false
                _perfectThrowGreat.value = false
                _perfectThrowNice.value = true
            }
            else -> {
                _perfectThrowExcellent.value = true
                _perfectThrowGreat.value = false
                _perfectThrowNice.value = false
            }
        }

        // Load auto walk settings
        _isAutoWalkEnabled.value = settingsManager.isAutoWalkEnabled()
        _autoWalkSpeed.value = settingsManager.getAutoWalkSpeed()

        // Load injection settings
        _injectionDelay.value = settingsManager.getInjectionDelay()

        // Load auto catch settings
        _isAutoCatchEnabled.value = settingsManager.isAutoCatchEnabled()
        _autoCatchDelay.value = settingsManager.getAutoCatchDelay()
        _autoCatchRetryOnEscape.value = settingsManager.isAutoCatchRetryOnEscape()
        _autoCatchMaxRetries.value = settingsManager.getAutoCatchMaxRetries()

        // Load Pokéball type
        val ballType = settingsManager.getAutoCatchBallType()
        when (ballType) {
            "POKE_BALL" -> _pokeBallType.value = PokeBallType.POKE_BALL
            "GREAT_BALL" -> _pokeBallType.value = PokeBallType.GREAT_BALL
            "ULTRA_BALL" -> _pokeBallType.value = PokeBallType.ULTRA_BALL
            else -> _pokeBallType.value = PokeBallType.POKE_BALL
        }

        LogUtils.i(TAG, "Loaded settings from SharedPreferences")
    }

    fun setPerfectThrowEnabled(enabled: Boolean) {
        _isPerfectThrowEnabled.value = enabled
        settingsManager.setPerfectThrowEnabled(enabled)
        LogUtils.i(TAG, "Perfect throw ${if (enabled) "enabled" else "disabled"}")
    }

    fun setPerfectThrowCurveball(enabled: Boolean) {
        _perfectThrowCurveball.value = enabled
        settingsManager.setPerfectThrowCurveball(enabled)
        LogUtils.i(TAG, "Perfect throw curveball ${if (enabled) "enabled" else "disabled"}")
    }

    fun setPerfectThrowType(type: PerfectThrowType) {
        when (type) {
            PerfectThrowType.EXCELLENT -> {
                _perfectThrowExcellent.value = true
                _perfectThrowGreat.value = false
                _perfectThrowNice.value = false
                settingsManager.setPerfectThrowType("EXCELLENT")
                LogUtils.i(TAG, "Perfect throw type set to EXCELLENT")
            }
            PerfectThrowType.GREAT -> {
                _perfectThrowExcellent.value = false
                _perfectThrowGreat.value = true
                _perfectThrowNice.value = false
                settingsManager.setPerfectThrowType("GREAT")
                LogUtils.i(TAG, "Perfect throw type set to GREAT")
            }
            PerfectThrowType.NICE -> {
                _perfectThrowExcellent.value = false
                _perfectThrowGreat.value = false
                _perfectThrowNice.value = true
                settingsManager.setPerfectThrowType("NICE")
                LogUtils.i(TAG, "Perfect throw type set to NICE")
            }
        }
    }

    fun setAutoWalkEnabled(enabled: Boolean) {
        _isAutoWalkEnabled.value = enabled
        settingsManager.setAutoWalkEnabled(enabled)
        LogUtils.i(TAG, "Auto walk ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutoWalkSpeed(speed: Float) {
        _autoWalkSpeed.value = speed
        settingsManager.setAutoWalkSpeed(speed)
        LogUtils.i(TAG, "Auto walk speed set to $speed m/s")
    }

    fun setAutoCatchEnabled(enabled: Boolean) {
        _isAutoCatchEnabled.value = enabled
        settingsManager.setAutoCatchEnabled(enabled)
        LogUtils.i(TAG, "Auto catch ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutoCatchDelay(delay: Int) {
        _autoCatchDelay.value = delay
        settingsManager.setAutoCatchDelay(delay)
        LogUtils.i(TAG, "Auto catch delay set to $delay ms")
    }

    fun setAutoCatchRetryOnEscape(enabled: Boolean) {
        _autoCatchRetryOnEscape.value = enabled
        settingsManager.setAutoCatchRetryOnEscape(enabled)
        LogUtils.i(TAG, "Auto catch retry on escape ${if (enabled) "enabled" else "disabled"}")
    }

    fun setInjectionDelay(delay: Int) {
        _injectionDelay.value = delay
        settingsManager.setInjectionDelay(delay)
        LogUtils.i(TAG, "Injection delay set to $delay ms")
    }

    fun setAutoCatchMaxRetries(maxRetries: Int) {
        _autoCatchMaxRetries.value = maxRetries
        settingsManager.setAutoCatchMaxRetries(maxRetries)
        LogUtils.i(TAG, "Auto catch max retries set to $maxRetries")
    }

    fun setPokeBallType(type: PokeBallType) {
        _pokeBallType.value = type
        val ballTypeString = when (type) {
            PokeBallType.POKE_BALL -> "POKE_BALL"
            PokeBallType.GREAT_BALL -> "GREAT_BALL"
            PokeBallType.ULTRA_BALL -> "ULTRA_BALL"
        }
        settingsManager.setAutoCatchBallType(ballTypeString)
        LogUtils.i(TAG, "Pokéball type set to $ballTypeString")
    }

    fun resetToDefaults() {
        // Reset all settings to defaults in the settings manager
        settingsManager.resetToDefaults()

        // Reload settings from the settings manager
        loadSavedSettings()

        LogUtils.i(TAG, "Hook settings reset to defaults")
    }
}
