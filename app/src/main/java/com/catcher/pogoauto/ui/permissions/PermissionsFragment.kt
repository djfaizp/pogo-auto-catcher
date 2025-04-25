package com.catcher.pogoauto.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.catcher.pogoauto.MainActivity
import com.catcher.pogoauto.databinding.FragmentPermissionsBinding
import com.catcher.pogoauto.utils.LogUtils
import com.catcher.pogoauto.utils.PermissionManager
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class PermissionsFragment : Fragment() {
    companion object {
        private const val TAG = "PermissionsFragment"
    }

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var permissionsViewModel: PermissionsViewModel
    private lateinit var permissionManager: PermissionManager

    // Permission request launchers
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }

        // For Android 11+, we also need to check MANAGE_EXTERNAL_STORAGE
        val finalGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            allGranted && (permissionManager.hasManageExternalStoragePermission() ||
                          !needsManageExternalStorage())
        } else {
            allGranted
        }

        permissionsViewModel.setStoragePermissionGranted(finalGranted)
        updateButtonStates()

        // If we need MANAGE_EXTERNAL_STORAGE and don't have it, request it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            allGranted &&
            needsManageExternalStorage() &&
            !permissionManager.hasManageExternalStoragePermission()) {

            requestManageExternalStoragePermission()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if MANAGE_EXTERNAL_STORAGE permission was granted
        val hasManageStorage = permissionManager.hasManageExternalStoragePermission()

        // Update the storage permission state considering both basic and MANAGE_EXTERNAL_STORAGE
        val basicPermissionsGranted = PermissionManager.BASIC_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        val finalGranted = if (needsManageExternalStorage()) {
            basicPermissionsGranted && hasManageStorage
        } else {
            basicPermissionsGranted
        }

        permissionsViewModel.setStoragePermissionGranted(finalGranted)
        updateButtonStates()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val hasOverlayPermission = permissionManager.hasOverlayPermission()
        permissionsViewModel.setOverlayPermissionGranted(hasOverlayPermission)
        updateButtonStates()
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val isIgnoringBatteryOptimizations = permissionManager.isIgnoringBatteryOptimizations()
        permissionsViewModel.setBatteryOptimizationDisabled(isIgnoringBatteryOptimizations)
        updateButtonStates()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionsViewModel.setNotificationPermissionGranted(isGranted)
        updateButtonStates()
    }

    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        LogUtils.i(TAG, "Returned from app settings")
        try {
            // Check all permissions again after returning from settings
            if (isAdded) {
                checkAllPermissions()
                LogUtils.i(TAG, "Permissions rechecked after returning from settings")
            } else {
                LogUtils.w(TAG, "Fragment not attached, can't recheck permissions")
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking permissions after returning from settings: ${e.message}", e)
        }
    }

    // Location permission launchers
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        permissionsViewModel.setLocationPermissionGranted(allGranted)

        // If location permissions are granted, we can request background location
        if (allGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+, we need to request background location separately
            // and only after the user has granted the regular location permissions
            requestBackgroundLocationPermission()
        }

        updateButtonStates()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionsViewModel.setBackgroundLocationPermissionGranted(isGranted)
        updateButtonStates()
    }

    private val developerOptionsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if mock location is enabled after returning from developer options
        if (isAdded && ::permissionManager.isInitialized) {
            val mockLocationEnabled = permissionManager.isMockLocationEnabled()
            permissionsViewModel.setMockLocationEnabled(mockLocationEnabled)
            updateButtonStates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogUtils.i(TAG, "onCreate called")

        try {
            // Initialize ViewModel in onCreate
            permissionsViewModel = ViewModelProvider(this).get(PermissionsViewModel::class.java)

            // Initialize permission manager with the activity
            val activity = requireActivity()
            if (activity is AppCompatActivity) {
                permissionManager = PermissionManager(activity)
            } else {
                LogUtils.e(TAG, "Activity is not AppCompatActivity: ${activity.javaClass.simpleName}")
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LogUtils.i(TAG, "onCreateView called")
        try {
            _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
            val root: View = binding.root
            return root
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error in onCreateView: ${e.message}", e)
            // Create a minimal view if there's an error
            _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
            Toast.makeText(
                requireContext(),
                "Error initializing permissions: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            return binding.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogUtils.i(TAG, "onViewCreated called")

        try {
            if (::permissionManager.isInitialized) {
                // Set up button click listeners
                setupButtonListeners()

                // Check current permission states
                checkAllPermissions()
            } else {
                LogUtils.e(TAG, "PermissionManager not initialized")
                Toast.makeText(
                    requireContext(),
                    "Error initializing permissions",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error in onViewCreated: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error setting up permissions: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupButtonListeners() {
        LogUtils.i(TAG, "Setting up button listeners")
        try {
            // Storage permission button
            binding.buttonStoragePermission.setOnClickListener {
                LogUtils.i(TAG, "Storage permission button clicked")
                try {
                    requestStoragePermission()
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error requesting storage permission: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error requesting storage permission: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Overlay permission button
            binding.buttonOverlayPermission.setOnClickListener {
                LogUtils.i(TAG, "Overlay permission button clicked")
                try {
                    if (::permissionManager.isInitialized) {
                        permissionManager.requestOverlayPermission(overlayPermissionLauncher)
                    } else {
                        LogUtils.e(TAG, "PermissionManager not initialized")
                        Toast.makeText(
                            requireContext(),
                            "Error: Permission manager not initialized",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error requesting overlay permission: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error requesting overlay permission: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Battery optimization button
            binding.buttonBatteryOptimization.setOnClickListener {
                LogUtils.i(TAG, "Battery optimization button clicked")
                try {
                    if (::permissionManager.isInitialized) {
                        permissionManager.requestIgnoreBatteryOptimizations(batteryOptimizationLauncher)
                    } else {
                        LogUtils.e(TAG, "PermissionManager not initialized")
                        Toast.makeText(
                            requireContext(),
                            "Error: Permission manager not initialized",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error requesting battery optimization: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error requesting battery optimization: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Notification permission button
            binding.buttonNotificationPermission.setOnClickListener {
                LogUtils.i(TAG, "Notification permission button clicked")
                try {
                    requestNotificationPermission()
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error requesting notification permission: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error requesting notification permission: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Root access button
            binding.buttonRootAccess.setOnClickListener {
                LogUtils.i(TAG, "Root access button clicked")
                try {
                    requestRootAccess()
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error requesting root access: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error requesting root access: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Location permission button
            binding.buttonLocationPermission.setOnClickListener {
                LogUtils.i(TAG, "Location permission button clicked")
                try {
                    requestLocationPermission()
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error requesting location permission: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error requesting location permission: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Background location permission button
            binding.buttonBackgroundLocation.setOnClickListener {
                LogUtils.i(TAG, "Background location permission button clicked")
                try {
                    requestBackgroundLocationPermission()
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error requesting background location permission: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error requesting background location permission: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Mock location button
            binding.buttonMockLocation.setOnClickListener {
                LogUtils.i(TAG, "Mock location button clicked")
                try {
                    if (::permissionManager.isInitialized) {
                        permissionManager.openDeveloperOptions(developerOptionsLauncher)
                    } else {
                        LogUtils.e(TAG, "PermissionManager not initialized")
                        Toast.makeText(
                            requireContext(),
                            "Error: Permission manager not initialized",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error opening developer options: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error opening developer options: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // App info button
            binding.buttonAppInfo.setOnClickListener {
                LogUtils.i(TAG, "App info button clicked")
                try {
                    if (::permissionManager.isInitialized) {
                        try {
                            // Try using a direct intent first as a workaround for the crash
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${requireActivity().packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                            }

                            // Check if there's an activity that can handle this intent
                            val packageManager = requireActivity().packageManager
                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                                LogUtils.i(TAG, "App info settings opened directly")

                                // Schedule a permission check when we return to the app
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (isAdded) {
                                        checkAllPermissions()
                                    }
                                }, 1000)
                            } else {
                                // Fall back to the permission manager if direct intent fails
                                LogUtils.w(TAG, "Direct intent failed, using permission manager")
                                permissionManager.openAppInfoSettings(appSettingsLauncher)
                            }
                        } catch (e: Exception) {
                            LogUtils.e(TAG, "Error with direct intent, trying permission manager: ${e.message}", e)
                            // Try the original method as fallback
                            permissionManager.openAppInfoSettings(appSettingsLauncher)
                        }
                    } else {
                        LogUtils.e(TAG, "PermissionManager not initialized")
                        Toast.makeText(
                            requireContext(),
                            "Error: Permission manager not initialized",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error opening app info settings: ${e.message}", e)
                    Toast.makeText(
                        requireContext(),
                        "Error opening app info settings: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Show a helpful message to the user
                    Toast.makeText(
                        requireContext(),
                        "Please open Settings app and grant permissions manually",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error setting up button listeners: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error setting up permission buttons: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkAllPermissions() {
        LogUtils.i(TAG, "Checking all permissions")
        try {
            // Check storage permission
            val hasBasicStoragePermission = PermissionManager.BASIC_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
            }

            // For Android 11+, also check MANAGE_EXTERNAL_STORAGE if needed
            val hasFullStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && needsManageExternalStorage()) {
                if (::permissionManager.isInitialized) {
                    hasBasicStoragePermission && permissionManager.hasManageExternalStoragePermission()
                } else {
                    hasBasicStoragePermission
                }
            } else {
                hasBasicStoragePermission
            }

            permissionsViewModel.setStoragePermissionGranted(hasFullStoragePermission)

            // Check overlay permission
            if (::permissionManager.isInitialized) {
                val hasOverlayPermission = permissionManager.hasOverlayPermission()
                permissionsViewModel.setOverlayPermissionGranted(hasOverlayPermission)

                // Check battery optimization
                val isIgnoringBatteryOptimizations = permissionManager.isIgnoringBatteryOptimizations()
                permissionsViewModel.setBatteryOptimizationDisabled(isIgnoringBatteryOptimizations)
            } else {
                LogUtils.w(TAG, "PermissionManager not initialized, skipping some permission checks")
                permissionsViewModel.setOverlayPermissionGranted(false)
                permissionsViewModel.setBatteryOptimizationDisabled(false)
            }

            // Check notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotificationPermission = ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                permissionsViewModel.setNotificationPermissionGranted(hasNotificationPermission)
            } else {
                // Notification permission not needed for older Android versions
                permissionsViewModel.setNotificationPermissionGranted(true)
            }

            // Check location permissions
            if (::permissionManager.isInitialized) {
                // Check regular location permissions
                val hasLocationPermissions = permissionManager.hasLocationPermissions()
                permissionsViewModel.setLocationPermissionGranted(hasLocationPermissions)

                // Check background location permission
                val hasBackgroundLocation = permissionManager.hasBackgroundLocationPermission()
                permissionsViewModel.setBackgroundLocationPermissionGranted(hasBackgroundLocation)

                // Check if mock location is enabled
                val mockLocationEnabled = permissionManager.isMockLocationEnabled()
                permissionsViewModel.setMockLocationEnabled(mockLocationEnabled)
            } else {
                permissionsViewModel.setLocationPermissionGranted(false)
                permissionsViewModel.setBackgroundLocationPermissionGranted(false)
                permissionsViewModel.setMockLocationEnabled(false)
            }

            // Check root access in a background thread
            checkRootAccessAsync()

            // Update button states
            updateButtonStates()
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking permissions: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error checking permissions: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Determine if the app needs MANAGE_EXTERNAL_STORAGE permission
     * based on Android version and app requirements
     */
    private fun needsManageExternalStorage(): Boolean {
        // Only Android 11+ needs this permission
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission
     */
    private fun requestManageExternalStoragePermission() {
        LogUtils.i(TAG, "Requesting MANAGE_EXTERNAL_STORAGE permission")
        try {
            if (::permissionManager.isInitialized) {
                permissionManager.requestManageExternalStoragePermission(manageStorageLauncher)
            } else {
                LogUtils.e(TAG, "PermissionManager not initialized")
                Toast.makeText(
                    requireContext(),
                    "Error: Permission manager not initialized",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error requesting MANAGE_EXTERNAL_STORAGE: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error requesting storage permission: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestStoragePermission() {
        LogUtils.i(TAG, "Requesting storage permissions")

        try {
            // First request the basic permissions
            storagePermissionLauncher.launch(PermissionManager.BASIC_PERMISSIONS)

            // MANAGE_EXTERNAL_STORAGE will be requested in the callback if needed
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error requesting storage permissions: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error requesting storage permissions: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LogUtils.i(TAG, "Requesting notification permission")
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            LogUtils.i(TAG, "Notification permission not needed for this Android version")
            Toast.makeText(
                requireContext(),
                "Notification permission not needed for this Android version",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestLocationPermission() {
        LogUtils.i(TAG, "Requesting location permissions")
        try {
            if (::permissionManager.isInitialized) {
                permissionManager.requestLocationPermissions(locationPermissionLauncher)
            } else {
                LogUtils.e(TAG, "PermissionManager not initialized")
                Toast.makeText(
                    requireContext(),
                    "Error: Permission manager not initialized",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error requesting location permissions: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error requesting location permissions: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestBackgroundLocationPermission() {
        LogUtils.i(TAG, "Requesting background location permission")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (::permissionManager.isInitialized) {
                    // Check if we have the regular location permissions first
                    if (permissionManager.hasLocationPermissions()) {
                        permissionManager.requestBackgroundLocationPermission(backgroundLocationLauncher)
                    } else {
                        LogUtils.w(TAG, "Regular location permissions must be granted before requesting background location")
                        Toast.makeText(
                            requireContext(),
                            "Please grant location permissions first",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Request regular location permissions first
                        requestLocationPermission()
                    }
                } else {
                    LogUtils.e(TAG, "PermissionManager not initialized")
                    Toast.makeText(
                        requireContext(),
                        "Error: Permission manager not initialized",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                LogUtils.i(TAG, "Background location permission not needed for this Android version")
                Toast.makeText(
                    requireContext(),
                    "Background location permission not needed for this Android version",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error requesting background location permission: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "Error requesting background location permission: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestRootAccess() {
        LogUtils.i(TAG, "Requesting root access")
        // Show a loading message
        Toast.makeText(
            requireContext(),
            "Checking root access...",
            Toast.LENGTH_SHORT
        ).show()

        // Run root check in background thread
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            var process: Process? = null
            var os: DataOutputStream? = null
            try {
                process = Runtime.getRuntime().exec("su")
                os = DataOutputStream(process.outputStream)
                os.writeBytes("exit\n")
                os.flush()
                val exitValue = process.waitFor()
                val rootGranted = exitValue == 0

                // Update UI on main thread
                handler.post {
                    try {
                        permissionsViewModel.setRootAccessGranted(rootGranted)
                        updateButtonStates()

                        if (isAdded) { // Check if fragment is still attached
                            Toast.makeText(
                                requireContext(),
                                if (rootGranted) "Root access granted" else "Root access denied",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error updating UI after root check: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error requesting root: ${e.message}", e)

                // Update UI on main thread
                handler.post {
                    try {
                        permissionsViewModel.setRootAccessGranted(false)
                        updateButtonStates()

                        if (isAdded) { // Check if fragment is still attached
                            Toast.makeText(
                                requireContext(),
                                "Error requesting root: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error updating UI after root check failure: ${e.message}", e)
                    }
                }
            } finally {
                try {
                    os?.close()
                    process?.destroy()
                } catch (e: IOException) {
                    // Ignore close error
                }
            }
        }
    }

    private fun checkRootAccessAsync() {
        LogUtils.i(TAG, "Checking root access asynchronously")

        // Run root check in background thread
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            var process: Process? = null
            var os: DataOutputStream? = null
            try {
                process = Runtime.getRuntime().exec("su")
                os = DataOutputStream(process.outputStream)
                os.writeBytes("exit\n")
                os.flush()
                val exitValue = process.waitFor()
                val rootGranted = exitValue == 0

                // Update UI on main thread
                handler.post {
                    try {
                        permissionsViewModel.setRootAccessGranted(rootGranted)
                        updateButtonStates()
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error updating UI after root check: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error checking root: ${e.message}", e)

                // Update UI on main thread
                handler.post {
                    try {
                        permissionsViewModel.setRootAccessGranted(false)
                        updateButtonStates()
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error updating UI after root check failure: ${e.message}", e)
                    }
                }
            } finally {
                try {
                    os?.close()
                    process?.destroy()
                } catch (e: IOException) {
                    // Ignore close error
                }
            }
        }
    }

    private fun updateButtonStates() {
        LogUtils.i(TAG, "Updating button states")
        try {
            if (_binding == null) {
                LogUtils.w(TAG, "Binding is null, cannot update button states")
                return
            }

            // Update storage permission button
            val storageGranted = permissionsViewModel.storagePermissionGranted.value ?: false
            updateButtonState(
                binding.buttonStoragePermission,
                storageGranted,
                "Grant Storage Permissions",
                "Storage Permissions Granted"
            )

            // Update overlay permission button
            val overlayGranted = permissionsViewModel.overlayPermissionGranted.value ?: false
            updateButtonState(
                binding.buttonOverlayPermission,
                overlayGranted,
                "Grant Overlay Permission",
                "Overlay Permission Granted"
            )

            // Update battery optimization button
            val batteryOptDisabled = permissionsViewModel.batteryOptimizationDisabled.value ?: false
            updateButtonState(
                binding.buttonBatteryOptimization,
                batteryOptDisabled,
                "Disable Battery Optimization",
                "Battery Optimization Disabled"
            )

            // Update notification permission button
            val notificationGranted = permissionsViewModel.notificationPermissionGranted.value ?: false
            updateButtonState(
                binding.buttonNotificationPermission,
                notificationGranted,
                "Grant Notification Permission",
                "Notification Permission Granted"
            )

            // Update root access button
            val rootGranted = permissionsViewModel.rootAccessGranted.value ?: false
            updateButtonState(
                binding.buttonRootAccess,
                rootGranted,
                "Request Root Access",
                "Root Access Granted"
            )

            // Update location permission button
            val locationGranted = permissionsViewModel.locationPermissionGranted.value ?: false
            updateButtonState(
                binding.buttonLocationPermission,
                locationGranted,
                "Grant Location Permissions",
                "Location Permissions Granted"
            )

            // Update background location button
            val backgroundLocationGranted = permissionsViewModel.backgroundLocationPermissionGranted.value ?: false
            updateButtonState(
                binding.buttonBackgroundLocation,
                backgroundLocationGranted,
                "Grant Background Location",
                "Background Location Granted"
            )

            // Update mock location button
            val mockLocationEnabled = permissionsViewModel.mockLocationEnabled.value ?: false
            updateButtonState(
                binding.buttonMockLocation,
                mockLocationEnabled,
                "Open Developer Options",
                "Mock Location Enabled"
            )
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error updating button states: ${e.message}", e)
        }
    }

    private fun updateButtonState(button: Button, isGranted: Boolean, grantText: String, grantedText: String) {
        try {
            button.text = if (isGranted) grantedText else grantText
            button.isEnabled = !isGranted
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error updating button state: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        LogUtils.i(TAG, "onResume called")
        try {
            // Recheck permissions when returning to the fragment
            if (::permissionManager.isInitialized) {
                checkAllPermissions()
                LogUtils.i(TAG, "Permissions rechecked in onResume")
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking permissions in onResume: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
