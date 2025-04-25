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
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        init {
            try {
                // Log detailed information about the loading process
                LogUtils.i(TAG, "Attempting to load Frida gadget library")

                // Check the app's native library directory
                try {
                    // We can't use createDeviceProtectedStorageContext here since we don't have a context yet
                    // Just log that we'll check the libraries later in verifyFridaConfiguration
                    LogUtils.i(TAG, "Will check native library directory during app initialization")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error checking native library directory", e)
                }

                // Try to get information about the library file in the app's data directory
                try {
                    // Check multiple possible locations for the Frida gadget library
                    val possiblePaths = listOf(
                        "/data/data/com.catcher.pogoauto/lib/libfrida-gadget.so",
                        "/data/data/com.catcher.pogoauto/app/src/main/jniLibs/arm64-v8a/libfrida-gadget.so",
                        "/data/app/~~3FCfbdahzqdvtXdxVqoEbg==/com.catcher.pogoauto-WXhXeyQ_ljsM-K99UJJoSQ==/lib/arm64/libfrida-gadget.so",
                        "/data/app/com.catcher.pogoauto/lib/arm64/libfrida-gadget.so"
                    )

                    var libraryFound = false
                    for (path in possiblePaths) {
                        val file = File(path)
                        if (file.exists()) {
                            LogUtils.i(TAG, "Frida gadget library file exists at $path, size: ${file.length()} bytes")
                            libraryFound = true
                            break
                        } else {
                            LogUtils.d(TAG, "Frida gadget library file does not exist at $path")
                        }
                    }

                    if (!libraryFound) {
                        LogUtils.w(TAG, "Frida gadget library file not found in any of the expected locations")
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error checking Frida gadget library file", e)
                }

                // Try to load the library with better error handling
                try {
                    LogUtils.i(TAG, "Attempting to load libfrida-gadget.so")
                    System.loadLibrary("frida-gadget")
                    LogUtils.i(TAG, "Frida gadget loaded successfully")

                    // Log additional diagnostic information
                    LogUtils.i(TAG, "Frida gadget initialization complete")
                } catch (e: UnsatisfiedLinkError) {
                    LogUtils.e(TAG, "Failed to load frida-gadget", e)
                    LogUtils.e(TAG, "Error details: ${e.message}")

                    // Try to get more information about the error
                    try {
                        val libraryPath = System.mapLibraryName("frida-gadget")
                        LogUtils.e(TAG, "Attempted to load library: $libraryPath")

                        // Check library paths
                        val paths = System.getProperty("java.library.path")
                        LogUtils.d(TAG, "Library search paths: $paths")

                        // We'll check available libraries in verifyFridaConfiguration
                        LogUtils.d(TAG, "Will check available libraries during app initialization")
                    } catch (ex: Exception) {
                        LogUtils.e(TAG, "Error getting additional library information", ex)
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Unexpected error loading Frida gadget", e)
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Critical error in Frida gadget initialization", e)
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

        // Check config file
        val configFile = File(fridaScriptManager.getConfigPath())
        if (configFile.exists() && configFile.length() > 0) {
            LogUtils.i(TAG, "Frida config file verified: ${configFile.absolutePath}, size: ${configFile.length()} bytes")

            // Verify config content
            try {
                val configContent = configFile.readText()
                LogUtils.d(TAG, "Config content: $configContent")

                val expectedScriptPath = "/data/data/${packageName}/files/${FridaScriptManager.SCRIPT_FILENAME}"

                if (configContent.contains(expectedScriptPath)) {
                    LogUtils.i(TAG, "Frida config contains correct script path")
                } else {
                    LogUtils.w(TAG, "Frida config may have incorrect script path, expected: $expectedScriptPath")

                    // Try to fix it
                    val fixedConfig = configContent.replace(
                        "\"path\": \"/data/data/com.catcher.pogoauto/files/pokemon-go-hook.js\"",
                        "\"path\": \"$expectedScriptPath\""
                    )

                    if (fixedConfig != configContent) {
                        // Write the fixed config
                        configFile.writeText(fixedConfig)
                        LogUtils.i(TAG, "Fixed Frida config script path")
                        Toast.makeText(this, "Fixed Frida config script path", Toast.LENGTH_SHORT).show()
                    }
                }

                // Check if the config has the correct interaction type
                if (!configContent.contains("\"type\": \"script\"")) {
                    LogUtils.w(TAG, "Config may have incorrect interaction type, should be 'script'")

                    // Try to fix it
                    val fixedConfig = configContent.replace(
                        "\"type\": \"listen\"",
                        "\"type\": \"script\""
                    )

                    if (fixedConfig != configContent) {
                        configFile.writeText(fixedConfig)
                        LogUtils.i(TAG, "Fixed Frida config interaction type")
                        Toast.makeText(this, "Fixed Frida config interaction type", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error verifying Frida config content", e)
            }
        } else {
            LogUtils.e(TAG, "Frida config file missing or empty: ${configFile.absolutePath}")
            Toast.makeText(this, "Frida config file missing or empty", Toast.LENGTH_SHORT).show()

            // Try to re-extract the config
            if (fridaScriptManager.extractScriptFromAssets()) {
                LogUtils.i(TAG, "Re-extracted Frida config successfully")
                Toast.makeText(this, "Re-extracted Frida config", Toast.LENGTH_SHORT).show()
            }
        }

        // Check Frida gadget library
        try {
            val libDir = File(applicationInfo.nativeLibraryDir)
            LogUtils.i(TAG, "Native library directory: ${libDir.absolutePath}")

            // List all files in the native library directory
            val files = libDir.listFiles()
            if (files != null) {
                LogUtils.i(TAG, "Native library directory contains ${files.size} files:")
                for (file in files) {
                    LogUtils.i(TAG, "- ${file.name} (${file.length()} bytes)")
                }
            } else {
                LogUtils.w(TAG, "Native library directory is empty or cannot be read")
            }

            val fridaLib = File(libDir, "libfrida-gadget.so")

            if (fridaLib.exists() && fridaLib.length() > 0) {
                LogUtils.i(TAG, "Frida gadget library verified: ${fridaLib.absolutePath}, size: ${fridaLib.length()} bytes")

                // Check file permissions
                try {
                    val execResult = Runtime.getRuntime().exec("ls -la ${fridaLib.absolutePath}")
                    val reader = BufferedReader(InputStreamReader(execResult.inputStream))
                    val output = reader.readText()
                    LogUtils.i(TAG, "Frida gadget library permissions: $output")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error checking Frida gadget library permissions", e)
                }
            } else {
                LogUtils.e(TAG, "Frida gadget library missing or empty: ${fridaLib.absolutePath}")

                // Check multiple possible locations for the library
                val possiblePaths = listOf(
                    "/data/data/com.catcher.pogoauto/lib/libfrida-gadget.so",
                    "/data/data/com.catcher.pogoauto/app/src/main/jniLibs/arm64-v8a/libfrida-gadget.so",
                    "/data/app/~~3FCfbdahzqdvtXdxVqoEbg==/com.catcher.pogoauto-WXhXeyQ_ljsM-K99UJJoSQ==/lib/arm64/libfrida-gadget.so",
                    "/data/app/com.catcher.pogoauto/lib/arm64/libfrida-gadget.so"
                )

                var foundLibrary = false
                for (path in possiblePaths) {
                    val file = File(path)
                    if (file.exists() && file.length() > 0) {
                        LogUtils.i(TAG, "Found Frida gadget library at: $path, size: ${file.length()} bytes")
                        foundLibrary = true

                        // Try to copy the library to the app's native library directory
                        try {
                            val destDir = File(applicationInfo.nativeLibraryDir)
                            if (!destDir.exists()) {
                                destDir.mkdirs()
                            }
                            val destFile = File(destDir, "libfrida-gadget.so")
                            file.copyTo(destFile, overwrite = true)
                            LogUtils.i(TAG, "Copied Frida gadget library to: ${destFile.absolutePath}")
                            Toast.makeText(this, "Copied Frida gadget library, please restart the app", Toast.LENGTH_LONG).show()
                            break
                        } catch (e: Exception) {
                            LogUtils.e(TAG, "Failed to copy Frida gadget library from $path", e)
                        }
                    }
                }

                if (!foundLibrary) {
                    // Check if the library exists in the jniLibs directory
                    val jniLibDir = File(applicationInfo.sourceDir)
                        .parentFile?.parentFile?.parentFile?.parentFile
                        ?.resolve("app/src/main/jniLibs/arm64-v8a")

                    if (jniLibDir != null && jniLibDir.exists()) {
                        LogUtils.i(TAG, "jniLibs directory exists: ${jniLibDir.absolutePath}")
                        val jniLibFiles = jniLibDir.listFiles()
                        if (jniLibFiles != null) {
                            LogUtils.i(TAG, "jniLibs directory contains ${jniLibFiles.size} files:")
                            for (file in jniLibFiles) {
                                LogUtils.i(TAG, "- ${file.name} (${file.length()} bytes)")
                            }

                            val jniFridaLib = File(jniLibDir, "libfrida-gadget.so")
                            if (jniFridaLib.exists() && jniFridaLib.length() > 0) {
                                LogUtils.i(TAG, "Frida gadget library found in jniLibs: ${jniFridaLib.absolutePath}, size: ${jniFridaLib.length()} bytes")

                                // Try to copy the library to the app's native library directory
                                try {
                                    val destDir = File(applicationInfo.nativeLibraryDir)
                                    if (!destDir.exists()) {
                                        destDir.mkdirs()
                                    }
                                    val destFile = File(destDir, "libfrida-gadget.so")
                                    jniFridaLib.copyTo(destFile, overwrite = true)
                                    LogUtils.i(TAG, "Copied Frida gadget library to: ${destFile.absolutePath}")
                                    Toast.makeText(this, "Copied Frida gadget library, please restart the app", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    LogUtils.e(TAG, "Failed to copy Frida gadget library", e)
                                }
                            } else {
                                LogUtils.e(TAG, "Frida gadget library not found in jniLibs")
                                Toast.makeText(this, "Frida gadget library not found. Please check installation.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            LogUtils.w(TAG, "jniLibs directory is empty or cannot be read")
                        }
                    } else {
                        LogUtils.w(TAG, "jniLibs directory does not exist")
                        Toast.makeText(this, "Frida gadget library missing or empty", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error verifying Frida gadget library", e)
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