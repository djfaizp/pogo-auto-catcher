package com.catcher.pogoauto.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.catcher.pogoauto.FridaScriptManager
import com.catcher.pogoauto.utils.LogUtils

class TraceSettingsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "TraceSettingsViewModel"
    }

    // Frida script manager
    private val fridaScriptManager = FridaScriptManager(application.applicationContext)

    // Master trace enabled status
    private val _isTraceEnabled = MutableLiveData<Boolean>().apply {
        value = true
    }
    val isTraceEnabled: LiveData<Boolean> = _isTraceEnabled

    // Individual trace categories
    private val _traceCategories = MutableLiveData<Map<String, Boolean>>().apply {
        value = LogUtils.getTraceCategories()
    }
    val traceCategories: LiveData<Map<String, Boolean>> = _traceCategories

    /**
     * Enable or disable all tracing
     */
    fun setTraceEnabled(enabled: Boolean) {
        _isTraceEnabled.value = enabled
        LogUtils.setTraceEnabled(enabled)
        LogUtils.i(TAG, "Trace logging ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Enable or disable a specific trace category
     */
    fun setTraceCategoryEnabled(category: String, enabled: Boolean) {
        // Update LogUtils
        LogUtils.setTraceCategoryEnabled(category, enabled)

        // Update Frida script
        fridaScriptManager.setTraceCategoryEnabled(category, enabled)

        // Update the LiveData with the new state
        _traceCategories.value = LogUtils.getTraceCategories()

        LogUtils.i(TAG, "Trace category $category ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Enable or disable all trace categories
     */
    fun setAllTraceCategoriesEnabled(enabled: Boolean) {
        // Update LogUtils
        LogUtils.setAllTraceCategoriesEnabled(enabled)

        // Update Frida script with all categories
        fridaScriptManager.setTraceCategories(LogUtils.getTraceCategories())

        // Update the LiveData with the new state
        _traceCategories.value = LogUtils.getTraceCategories()

        LogUtils.i(TAG, "All trace categories ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Refresh the trace categories from LogUtils
     */
    fun refreshTraceCategories() {
        _traceCategories.value = LogUtils.getTraceCategories()
    }
}
