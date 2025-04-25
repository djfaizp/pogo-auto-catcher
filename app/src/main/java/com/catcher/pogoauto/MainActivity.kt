package com.catcher.pogoauto

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.catcher.pogoauto.databinding.ActivityMainBinding
import com.catcher.pogoauto.utils.LogUtils
import com.catcher.pogoauto.utils.PermissionManager
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import java.io.DataOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        init {
            try {
                System.loadLibrary("frida-gadget")
                LogUtils.i(TAG, "Frida gadget loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                LogUtils.e(TAG, "Failed to load frida-gadget", e)
            }
        }
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var fridaScriptManager: FridaScriptManager
    private lateinit var permissionManager: PermissionManager

    // Launcher for battery optimization settings
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        LogUtils.i(TAG, "Returned from battery optimization settings")
        if (permissionManager.isIgnoringBatteryOptimizations()) {
            LogUtils.i(TAG, "Battery optimization exemption granted")
            Snackbar.make(binding.root, "Battery optimization exemption granted", Snackbar.LENGTH_SHORT).show()
        } else {
            LogUtils.w(TAG, "Battery optimization exemption denied")
            Snackbar.make(binding.root, "Battery optimization exemption denied", Snackbar.LENGTH_SHORT).show()
        }
    }

    // Launcher for overlay permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionManager.hasOverlayPermission()) {
            LogUtils.i(TAG, "Overlay permission granted")
            Snackbar.make(binding.root, "Overlay permission granted", Snackbar.LENGTH_SHORT).show()
            requestRootPermission()
        } else {
            LogUtils.w(TAG, "Overlay permission denied")
            Snackbar.make(binding.root, "Overlay permission denied", Snackbar.LENGTH_SHORT).show()
        }
    }

    // Launcher for app settings
    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        LogUtils.i(TAG, "Returned from app settings")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        fridaScriptManager = FridaScriptManager(this)
        permissionManager = PermissionManager(this)

        // Log app start
        LogUtils.i(TAG, "Application started")

        // Check and request permissions
        permissionManager.checkAndRequestBasicPermissions {
            // This is called when basic permissions are granted
            LogUtils.i(TAG, "Basic permissions granted, checking overlay permission")
            if (!permissionManager.hasOverlayPermission()) {
                permissionManager.requestOverlayPermission(overlayPermissionLauncher)
            } else {
                requestRootPermission()
            }
        }

        // Extract Frida script
        if (fridaScriptManager.extractScriptFromAssets()) {
            LogUtils.i(TAG, "Frida script extracted successfully")
        } else {
            LogUtils.e(TAG, "Failed to extract Frida script")
            Toast.makeText(this, "Failed to extract Frida script", Toast.LENGTH_SHORT).show()
        }

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            if (fridaScriptManager.isPokemonGoInstalled()) {
                if (fridaScriptManager.launchPokemonGo()) {
                    LogUtils.i(TAG, "Launching Pokémon GO with Frida hook")
                    Snackbar.make(view, "Launching Pokémon GO with Frida hook", Snackbar.LENGTH_LONG)
                        .setAction("Action", null)
                        .setAnchorView(R.id.fab).show()
                } else {
                    LogUtils.e(TAG, "Failed to launch Pokémon GO")
                    Snackbar.make(view, "Failed to launch Pokémon GO", Snackbar.LENGTH_LONG)
                        .setAction("Action", null)
                        .setAnchorView(R.id.fab).show()
                }
            } else {
                LogUtils.w(TAG, "Pokémon GO is not installed")
                Snackbar.make(view, "Pokémon GO is not installed", Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .setAnchorView(R.id.fab).show()
            }
        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_permissions, R.id.nav_log_settings,
                R.id.nav_trace_settings, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    /**
     * Request additional permissions for better app functionality
     */
    fun requestAdditionalPermissions() {
        LogUtils.i(TAG, "Requesting additional permissions")

        // Request notification permission if needed
        permissionManager.checkAndRequestNotificationPermission()

        // Request battery optimization exemption if needed
        if (!permissionManager.isIgnoringBatteryOptimizations()) {
            permissionManager.requestIgnoreBatteryOptimizations(batteryOptimizationLauncher)
        }
    }



    private fun requestRootPermission() {
        LogUtils.i(TAG, "Requesting root permission")
        var process: Process? = null
        var os: DataOutputStream? = null
        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            if (exitValue == 0) {
                // Root access granted
                LogUtils.i(TAG, "Root access granted")
                Snackbar.make(binding.root, "Root access granted", Snackbar.LENGTH_SHORT).show()

                // Now that we have all basic permissions, request additional ones
                requestAdditionalPermissions()
            } else {
                // Root access denied or error
                LogUtils.w(TAG, "Root access denied")
                Snackbar.make(binding.root, "Root access denied", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            // Error executing su
            LogUtils.e(TAG, "Error requesting root: ${e.message}", e)
            Snackbar.make(binding.root, "Error requesting root: ${e.message}", Snackbar.LENGTH_LONG).show()
        } catch (e: InterruptedException) {
            // Error waiting for process
            LogUtils.e(TAG, "Error requesting root: ${e.message}", e)
            Snackbar.make(binding.root, "Error requesting root: ${e.message}", Snackbar.LENGTH_LONG).show()
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