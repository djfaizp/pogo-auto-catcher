package com.catcher.pogoauto.ui.permissions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.catcher.pogoauto.utils.LogUtils

class PermissionsViewModel : ViewModel() {
    companion object {
        private const val TAG = "PermissionsViewModel"
    }

    private val _text = MutableLiveData<String>().apply {
        value = "App Permissions"
    }
    val text: LiveData<String> = _text

    // Permission states
    private val _storagePermissionGranted = MutableLiveData<Boolean>().apply { value = false }
    val storagePermissionGranted: LiveData<Boolean> = _storagePermissionGranted

    private val _overlayPermissionGranted = MutableLiveData<Boolean>().apply { value = false }
    val overlayPermissionGranted: LiveData<Boolean> = _overlayPermissionGranted

    private val _batteryOptimizationDisabled = MutableLiveData<Boolean>().apply { value = false }
    val batteryOptimizationDisabled: LiveData<Boolean> = _batteryOptimizationDisabled

    private val _notificationPermissionGranted = MutableLiveData<Boolean>().apply { value = false }
    val notificationPermissionGranted: LiveData<Boolean> = _notificationPermissionGranted

    private val _rootAccessGranted = MutableLiveData<Boolean>().apply { value = false }
    val rootAccessGranted: LiveData<Boolean> = _rootAccessGranted

    init {
        LogUtils.i(TAG, "PermissionsViewModel initialized with default values")
        // Initialize with default values to prevent null issues
        setStoragePermissionGranted(false)
        setOverlayPermissionGranted(false)
        setBatteryOptimizationDisabled(false)
        setNotificationPermissionGranted(false)
        setRootAccessGranted(false)
    }

    fun setStoragePermissionGranted(granted: Boolean) {
        _storagePermissionGranted.value = granted
        LogUtils.i(TAG, "Storage permission: ${if (granted) "granted" else "denied"}")
    }

    fun setOverlayPermissionGranted(granted: Boolean) {
        _overlayPermissionGranted.value = granted
        LogUtils.i(TAG, "Overlay permission: ${if (granted) "granted" else "denied"}")
    }

    fun setBatteryOptimizationDisabled(disabled: Boolean) {
        _batteryOptimizationDisabled.value = disabled
        LogUtils.i(TAG, "Battery optimization: ${if (disabled) "disabled" else "enabled"}")
    }

    fun setNotificationPermissionGranted(granted: Boolean) {
        _notificationPermissionGranted.value = granted
        LogUtils.i(TAG, "Notification permission: ${if (granted) "granted" else "denied"}")
    }

    fun setRootAccessGranted(granted: Boolean) {
        _rootAccessGranted.value = granted
        LogUtils.i(TAG, "Root access: ${if (granted) "granted" else "denied"}")
    }
}
