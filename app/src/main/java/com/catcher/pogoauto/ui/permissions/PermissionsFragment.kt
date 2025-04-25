package com.catcher.pogoauto.ui.permissions

import android.Manifest
import android.content.Intent
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
        permissionsViewModel.setStoragePermissionGranted(allGranted)
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
        // Check all permissions again after returning from settings
        checkAllPermissions()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LogUtils.i(TAG, "onCreateView called")
        try {
            permissionsViewModel = ViewModelProvider(this).get(PermissionsViewModel::class.java)
            _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
            val root: View = binding.root

            // Initialize permission manager with the activity
            val activity = requireActivity()
            if (activity is AppCompatActivity) {
                permissionManager = PermissionManager(activity)

                // Set up button click listeners
                setupButtonListeners()

                // Check current permission states
                checkAllPermissions()
            } else {
                LogUtils.e(TAG, "Activity is not AppCompatActivity: ${activity.javaClass.simpleName}")
                Toast.makeText(
                    requireContext(),
                    "Error initializing permissions",
                    Toast.LENGTH_SHORT
                ).show()
            }

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

            // App info button
            binding.buttonAppInfo.setOnClickListener {
                LogUtils.i(TAG, "App info button clicked")
                try {
                    if (::permissionManager.isInitialized) {
                        permissionManager.openAppInfoSettings(appSettingsLauncher)
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
            val hasStoragePermission = PermissionManager.BASIC_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(requireContext(), it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            permissionsViewModel.setStoragePermissionGranted(hasStoragePermission)

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
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                permissionsViewModel.setNotificationPermissionGranted(hasNotificationPermission)
            } else {
                // Notification permission not needed for older Android versions
                permissionsViewModel.setNotificationPermissionGranted(true)
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

    private fun requestStoragePermission() {
        LogUtils.i(TAG, "Requesting storage permissions")
        storagePermissionLauncher.launch(PermissionManager.BASIC_PERMISSIONS)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
