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
import com.catcher.pogoauto.service.ServiceManager
import com.catcher.pogoauto.utils.LibraryUtils
import com.catcher.pogoauto.utils.LogUtils
import com.catcher.pogoauto.utils.PermissionManager
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var fridaScriptManager: FridaScriptManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var serviceManager: ServiceManager

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
            requestRootPermission() // Still attempt root if needed
            requestAdditionalPermissions() // Request additional permissions now that overlay is granted
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
        serviceManager = ServiceManager(this)

        // Log app start
        LogUtils.i(TAG, "Application started")

        // Check and request permissions
        permissionManager.checkAndRequestBasicPermissions {
            // This is called when basic permissions are granted
            LogUtils.i(TAG, "Basic permissions granted, checking overlay permission")
            if (!permissionManager.hasOverlayPermission()) {
                permissionManager.requestOverlayPermission(overlayPermissionLauncher)
            } else {
                // Overlay permission already granted, proceed with root and additional permissions
                requestRootPermission() // Still attempt root if needed
                requestAdditionalPermissions() // Request additional permissions regardless of root outcome
            }
        }

        // Extract and install Frida server from assets
        val extractedPath = LibraryUtils.extractFridaServerFromAssets(this)
        if (extractedPath != null) {
            LogUtils.i(TAG, "Frida server extracted successfully: $extractedPath")
            Toast.makeText(this, "Frida server extracted successfully", Toast.LENGTH_SHORT).show()

            // Set executable permissions
            if (LibraryUtils.setFridaServerPermissions(this, extractedPath)) {
                LogUtils.i(TAG, "Frida server permissions set at ${LibraryUtils.getFridaServerPath(this)}")
                Toast.makeText(this, "Frida server installed successfully", Toast.LENGTH_SHORT).show()

                // Check if Frida server is running
                if (LibraryUtils.isFridaServerRunning()) {
                    LogUtils.i(TAG, "Frida server is already running")
                    Toast.makeText(this, "Frida server is already running", Toast.LENGTH_SHORT).show()
                } else {
                    LogUtils.i(TAG, "Frida server is not running, attempting to start it")
                    if (LibraryUtils.startFridaServer(this)) {
                        LogUtils.i(TAG, "Frida server started successfully")
                        Toast.makeText(this, "Frida server started successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        LogUtils.e(TAG, "Failed to start Frida server")
                        Toast.makeText(this, "Failed to start Frida server. Root access may be required.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                LogUtils.e(TAG, "Failed to set permissions on Frida server at ${LibraryUtils.getFridaServerPath(this)}")
                Toast.makeText(this, "Failed to set permissions on Frida server. Root access may be required.", Toast.LENGTH_SHORT).show()
            }
        } else {
            LogUtils.e(TAG, "Failed to extract Frida server from assets")
            Toast.makeText(this, "Failed to extract Frida server from assets", Toast.LENGTH_SHORT).show()
        }

        // Extract Frida script
        if (fridaScriptManager.extractScriptFromAssets()) {
            LogUtils.i(TAG, "Frida script extracted successfully")

            // Verify Frida configuration
            verifyFridaConfiguration()
        } else {
            LogUtils.e(TAG, "Failed to extract Frida script")
            Toast.makeText(this, "Failed to extract Frida script", Toast.LENGTH_SHORT).show()
        }

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            if (fridaScriptManager.isPokemonGoInstalled()) {
                // Start the foreground service first
                serviceManager.startService()

                // Launch Pokémon GO through the service
                if (serviceManager.launchPokemonGo()) {
                    LogUtils.i(TAG, "Launching Pokémon GO with Frida hook via service")
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

    override fun onDestroy() {
        super.onDestroy()
        LogUtils.i(TAG, "MainActivity onDestroy")

        // Unbind from service but don't stop it
        // This allows the service to continue running in the background
        serviceManager.unbindService()
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



    /**
     * Verify Frida configuration and setup
     * This checks that all necessary files are in place and properly configured
     */
    private fun verifyFridaConfiguration() {
        LogUtils.i(TAG, "Verifying Frida configuration")

        // Check script file
        val scriptFile = File(fridaScriptManager.getScriptPath())
        if (scriptFile.exists() && scriptFile.length() > 0) {
            LogUtils.i(TAG, "Frida script file verified: ${scriptFile.absolutePath}, size: ${scriptFile.length()} bytes")

            // Log the first few lines of the script for debugging
            try {
                val scriptContent = scriptFile.readText()
                LogUtils.d(TAG, "Script content preview (first 200 chars): ${scriptContent.take(200)}")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error reading script content", e)
            }
        } else {
            LogUtils.e(TAG, "Frida script file missing or empty: ${scriptFile.absolutePath}")
            Toast.makeText(this, "Frida script file missing or empty", Toast.LENGTH_SHORT).show()

            // Try to re-extract the script
            if (fridaScriptManager.extractScriptFromAssets()) {
                LogUtils.i(TAG, "Re-extracted Frida script successfully")
                Toast.makeText(this, "Re-extracted Frida script", Toast.LENGTH_SHORT).show()
            }
        }

        // Check Frida server status
        try {
            if (LibraryUtils.isFridaServerInstalled(this)) {
                LogUtils.i(TAG, "Frida server is installed at ${LibraryUtils.getFridaServerPath(this)}")

                if (LibraryUtils.isFridaServerRunning()) {
                    LogUtils.i(TAG, "Frida server is running")

                    // Check if we can connect to the Frida server
                    try {
                        // Use ps command to check for running processes instead of frida-ps
                        val process = Runtime.getRuntime().exec("ps -A")
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val output = reader.readText()

                        if (output.isNotEmpty()) {
                            LogUtils.i(TAG, "Successfully verified running processes")
                            LogUtils.d(TAG, "Process list sample: ${output.take(500)}")

                            // Check if our Frida server is in the process list
                            val fridaServerPath = LibraryUtils.getFridaServerPath(this)
                            val fridaServerFilename = File(fridaServerPath).name

                            if (output.contains(fridaServerFilename) || output.contains("frida-server")) {
                                LogUtils.i(TAG, "Frida server process found in process list")
                            } else {
                                LogUtils.w(TAG, "Frida server process not found in process list")
                            }
                        } else {
                            LogUtils.w(TAG, "Got empty process list when checking Frida server")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error checking Frida server process", e)
                    }
                } else {
                    LogUtils.w(TAG, "Frida server is installed but not running")
                    Toast.makeText(this, "Frida server is not running. Starting it now...", Toast.LENGTH_SHORT).show()

                    // Try to start the Frida server
                    if (LibraryUtils.startFridaServer(this)) {
                        LogUtils.i(TAG, "Successfully started Frida server")
                        Toast.makeText(this, "Successfully started Frida server", Toast.LENGTH_SHORT).show()
                    } else {
                        LogUtils.e(TAG, "Failed to start Frida server")
                        Toast.makeText(this, "Failed to start Frida server. Root access may be required.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                LogUtils.i(TAG, "Frida server is not installed, extracting and installing from assets")

                // Extract and install Frida server from assets
                val extractedPath = LibraryUtils.extractFridaServerFromAssets(this)
                if (extractedPath != null) {
                    LogUtils.i(TAG, "Frida server extracted successfully: $extractedPath")

                    // Set executable permissions
                    if (LibraryUtils.setFridaServerPermissions(this, extractedPath)) {
                        LogUtils.i(TAG, "Frida server permissions set at ${LibraryUtils.getFridaServerPath(this)}")
                        Toast.makeText(this, "Frida server installed successfully", Toast.LENGTH_SHORT).show()

                        // Try to start the Frida server
                        if (LibraryUtils.startFridaServer(this)) {
                            LogUtils.i(TAG, "Successfully started Frida server")
                            Toast.makeText(this, "Successfully started Frida server", Toast.LENGTH_SHORT).show()
                        } else {
                            LogUtils.e(TAG, "Failed to start Frida server")
                            Toast.makeText(this, "Failed to start Frida server. Root access may be required.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        LogUtils.e(TAG, "Failed to set permissions on Frida server at ${LibraryUtils.getFridaServerPath(this)}")
                        Toast.makeText(this, "Failed to set permissions on Frida server. Root access may be required.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    LogUtils.e(TAG, "Failed to extract Frida server from assets")
                    Toast.makeText(this, "Failed to extract Frida server from assets", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking Frida server status", e)
        }

        // Run a Frida status check
        Thread {
            try {
                val fridaStatus = fridaScriptManager.checkFridaStatus()
                LogUtils.i(TAG, "Initial Frida status check: ${if (fridaStatus) "LOADED" else "NOT LOADED"}")

                if (!fridaStatus) {
                    // Try to diagnose the issue
                    runOnUiThread {
                        Toast.makeText(this, "Frida not loaded in Pokémon GO. Checking for issues...", Toast.LENGTH_LONG).show()
                    }

                    // Check if Pokémon GO is running
                    val packageName = fridaScriptManager.getPokemonGoPackageName()
                    if (packageName != null) {
                        try {
                            val psProcess = Runtime.getRuntime().exec("ps")
                            val psReader = BufferedReader(InputStreamReader(psProcess.inputStream))
                            var isRunning = false

                            var line: String?
                            while (psReader.readLine().also { line = it } != null) {
                                if (line?.contains(packageName) == true) {
                                    isRunning = true
                                    break
                                }
                            }

                            if (!isRunning) {
                                LogUtils.w(TAG, "Pokémon GO is not running, launch it first")
                                runOnUiThread {
                                    Toast.makeText(this, "Pokémon GO is not running. Launch it first.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                LogUtils.i(TAG, "Pokémon GO is running, but Frida is not loaded")
                                runOnUiThread {
                                    Toast.makeText(this, "Pokémon GO is running, but Frida is not loaded. Check logs for details.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            LogUtils.e(TAG, "Error checking if Pokémon GO is running", e)
                        }
                    } else {
                        LogUtils.w(TAG, "Pokémon GO is not installed")
                        runOnUiThread {
                            Toast.makeText(this, "Pokémon GO is not installed", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Frida successfully loaded in Pokémon GO!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error in initial Frida status check", e)
                runOnUiThread {
                    Toast.makeText(this, "Error checking Frida status: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
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

                // Root access granted
                // Additional permissions are now requested earlier
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