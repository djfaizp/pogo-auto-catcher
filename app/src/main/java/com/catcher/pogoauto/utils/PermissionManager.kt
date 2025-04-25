package com.catcher.pogoauto.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.catcher.pogoauto.utils.LogUtils

/**
 * Utility class to manage permissions
 */
class PermissionManager(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "PermissionManager"
        
        // Basic permissions needed for the app
        val BASIC_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        // Additional permissions for Android 13+
        val NOTIFICATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }
    
    // Permission request launcher
    private val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            LogUtils.i(TAG, "All requested permissions granted")
            onPermissionsGranted()
        } else {
            LogUtils.w(TAG, "Some permissions denied: ${permissions.filter { !it.value }.keys}")
            showPermissionExplanationDialog()
        }
    }
    
    // Callback for when permissions are granted
    private var onPermissionsGranted: () -> Unit = {}
    
    /**
     * Check and request all basic permissions
     */
    fun checkAndRequestBasicPermissions(onGranted: () -> Unit) {
        this.onPermissionsGranted = onGranted
        
        val permissionsToRequest = BASIC_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isEmpty()) {
            LogUtils.i(TAG, "All basic permissions already granted")
            onGranted()
        } else {
            LogUtils.i(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }
    
    /**
     * Check and request notification permission (Android 13+)
     */
    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                
                LogUtils.i(TAG, "Requesting notification permission")
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            } else {
                LogUtils.i(TAG, "Notification permission already granted")
            }
        } else {
            LogUtils.i(TAG, "Notification permission not needed for this Android version")
        }
    }
    
    /**
     * Check if the app has overlay permission
     */
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true // Overlay permission not needed for Android < 6.0
        }
    }
    
    /**
     * Request overlay permission
     */
    fun requestOverlayPermission(resultLauncher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                LogUtils.i(TAG, "Requesting overlay permission")
                
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                resultLauncher.launch(intent)
            } else {
                LogUtils.i(TAG, "Overlay permission already granted")
            }
        } else {
            LogUtils.i(TAG, "Overlay permission not needed for this Android version")
        }
    }
    
    /**
     * Check if the app is on the battery optimization whitelist
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(activity.packageName)
        } else {
            true // Battery optimization exemption not needed for Android < 6.0
        }
    }
    
    /**
     * Request to be added to the battery optimization whitelist
     */
    fun requestIgnoreBatteryOptimizations(resultLauncher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations()) {
                LogUtils.i(TAG, "Requesting battery optimization exemption")
                
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                resultLauncher.launch(intent)
            } else {
                LogUtils.i(TAG, "Battery optimization exemption already granted")
            }
        } else {
            LogUtils.i(TAG, "Battery optimization exemption not needed for this Android version")
        }
    }
    
    /**
     * Open battery usage settings
     */
    fun openBatteryUsageSettings(resultLauncher: ActivityResultLauncher<Intent>) {
        LogUtils.i(TAG, "Opening battery usage settings")
        
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        resultLauncher.launch(intent)
    }
    
    /**
     * Open app info settings
     */
    fun openAppInfoSettings(resultLauncher: ActivityResultLauncher<Intent>) {
        LogUtils.i(TAG, "Opening app info settings")
        
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        resultLauncher.launch(intent)
    }
    
    /**
     * Show dialog explaining why permissions are needed
     */
    private fun showPermissionExplanationDialog() {
        LogUtils.i(TAG, "Showing permission explanation dialog")
        
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage("This app needs storage permissions to function properly. Please grant these permissions in the app settings.")
            .setPositiveButton("App Settings") { _, _ ->
                openAppInfoSettings(activity.registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    // Check permissions again after returning from settings
                    checkAndRequestBasicPermissions(onPermissionsGranted)
                })
            }
            .setNegativeButton("Cancel") { _, _ ->
                LogUtils.w(TAG, "User declined to grant permissions from dialog")
            }
            .show()
    }
}
