package com.catcher.pogoauto.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Utility class to manage permissions
 */
class PermissionManager(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "PermissionManager"

        // Basic permissions needed for the app
        val BASIC_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses more granular permissions
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 still uses READ_EXTERNAL_STORAGE
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            // Android 10 and below use both READ and WRITE
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        // Additional permissions for Android 13+
        val NOTIFICATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

        // Location permissions
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Background location permission (separate because it needs to be requested separately)
        val BACKGROUND_LOCATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else {
            "" // Not needed for Android < 10
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
     * Check if the app has MANAGE_EXTERNAL_STORAGE permission (Android 11+)
     */
    fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Not needed for Android < 11
        }
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission (Android 11+)
     */
    fun requestManageExternalStoragePermission(resultLauncher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                LogUtils.i(TAG, "Requesting MANAGE_EXTERNAL_STORAGE permission")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${activity.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    resultLauncher.launch(intent)
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error requesting MANAGE_EXTERNAL_STORAGE: ${e.message}", e)
                    // Fallback to general storage settings
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        resultLauncher.launch(intent)
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error with fallback for MANAGE_EXTERNAL_STORAGE: ${e.message}", e)
                    }
                }
            } else {
                LogUtils.i(TAG, "MANAGE_EXTERNAL_STORAGE permission already granted")
            }
        } else {
            LogUtils.i(TAG, "MANAGE_EXTERNAL_STORAGE permission not needed for this Android version")
        }
    }

    /**
     * Check and request all basic permissions
     */
    fun checkAndRequestBasicPermissions(onGranted: () -> Unit) {
        this.onPermissionsGranted = onGranted

        // For Android 11+, we need to check MANAGE_EXTERNAL_STORAGE separately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                LogUtils.i(TAG, "Need to request MANAGE_EXTERNAL_STORAGE permission")
                // We'll handle this separately with requestManageExternalStoragePermission
                // Still check the regular permissions
            }
        }

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
     * Check if the app has location permissions
     */
    fun hasLocationPermissions(): Boolean {
        return LOCATION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if the app has background location permission
     */
    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed for Android < 10
        }
    }

    /**
     * Request location permissions
     */
    fun requestLocationPermissions(resultLauncher: ActivityResultLauncher<Array<String>>) {
        if (!hasLocationPermissions()) {
            LogUtils.i(TAG, "Requesting location permissions")
            resultLauncher.launch(LOCATION_PERMISSIONS)
        } else {
            LogUtils.i(TAG, "Location permissions already granted")
        }
    }

    /**
     * Request background location permission (must be requested separately after location permissions)
     */
    fun requestBackgroundLocationPermission(resultLauncher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasBackgroundLocationPermission()) {
                LogUtils.i(TAG, "Requesting background location permission")
                resultLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                LogUtils.i(TAG, "Background location permission already granted")
            }
        } else {
            LogUtils.i(TAG, "Background location permission not needed for this Android version")
        }
    }

    /**
     * Check if mock location is enabled in developer options
     */
    fun isMockLocationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6.0+
            try {
                val locationMode = Settings.Secure.getInt(
                    activity.contentResolver,
                    Settings.Secure.ALLOW_MOCK_LOCATION, 0
                )
                locationMode != 0
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error checking mock location setting: ${e.message}", e)
                false
            }
        } else {
            // For older Android versions
            try {
                Settings.Secure.getInt(
                    activity.contentResolver,
                    Settings.Secure.ALLOW_MOCK_LOCATION, 0
                ) != 0
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error checking mock location setting: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Open developer options to enable mock location
     */
    fun openDeveloperOptions(resultLauncher: ActivityResultLauncher<Intent>) {
        LogUtils.i(TAG, "Opening developer options")
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            resultLauncher.launch(intent)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error opening developer options: ${e.message}", e)
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                resultLauncher.launch(intent)
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error with fallback for developer options: ${e.message}", e)
            }
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

        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }

            // Check if there's an activity that can handle this intent
            val packageManager = activity.packageManager
            if (intent.resolveActivity(packageManager) != null) {
                resultLauncher.launch(intent)
                LogUtils.i(TAG, "App info settings intent launched successfully")
            } else {
                // Fallback to a more generic settings page if specific one isn't available
                LogUtils.w(TAG, "No activity found to handle app details settings, trying alternative")
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                resultLauncher.launch(fallbackIntent)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error launching app info settings: ${e.message}", e)
            // Try direct method as a fallback
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error with fallback method for app settings: ${e.message}", e)
            }
        }
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
                try {
                    // Intent to open app settings with better error handling
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    }

                    // Check if there's an activity that can handle this intent
                    val packageManager = activity.packageManager
                    if (intent.resolveActivity(packageManager) != null) {
                        activity.startActivity(intent)
                        LogUtils.i(TAG, "App info settings intent launched successfully from dialog")
                    } else {
                        // Fallback to a more generic settings page if specific one isn't available
                        LogUtils.w(TAG, "No activity found to handle app details settings, trying alternative")
                        val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                        activity.startActivity(fallbackIntent)
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error launching app settings from dialog: ${e.message}", e)
                    Toast.makeText(
                        activity,
                        "Could not open app settings. Please open settings manually.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // Note: We might want to re-check permissions in onResume or similar lifecycle event
            }
            .setNegativeButton("Cancel") { _, _ ->
                LogUtils.w(TAG, "User declined to grant permissions from dialog")
            }
            .show()
    }
}
