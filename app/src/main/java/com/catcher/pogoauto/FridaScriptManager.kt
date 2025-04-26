package com.catcher.pogoauto

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import com.catcher.pogoauto.utils.LogUtils
import com.catcher.pogoauto.utils.LibraryUtils
import com.catcher.pogoauto.zygote.ZygoteMonitor

/**
 * Manager class for handling Frida scripts and interactions with Pokémon GO
 */
class FridaScriptManager(private val context: Context) {

    companion object {
        private const val TAG = "FridaScriptManager"
        const val SCRIPT_FILENAME = "pokemon-go-hook.js"
        const val ZYGOTE_SCRIPT_FILENAME = "zygote-monitor.js"
        const val BYPASS_SCRIPT_FILENAME = "frida-detection-bypass.js"
        const val FRIDA_PORT = 27042

        // List of possible Pokémon GO package names
        private val POKEMON_GO_PACKAGES = arrayOf(
            "com.nianticlabs.pokemongo",           // Standard release
            "com.nianticlabs.pokemongo.beta",      // Beta version
            "com.nianticlabs.pokemongo.uat",       // Testing version
            "com.pokemon.pokemongo",               // Alternative package name
            "com.nianticlabs.pokemongo.obb",       // OBB version
            "com.nianticlabs.pokemongo.dev",       // Development version
            "com.nianticlabs.pokemongo.qa",        // QA version
            "com.pokemongo.nianticlabs",           // Alternative order
            "com.pokemongo.pokemon"                // Another alternative
        )
    }

    // Zygote monitor
    private val zygoteMonitor = ZygoteMonitor(context)

    // Script configuration options
    private var perfectThrowEnabled = true
    private var perfectThrowCurveball = true
    private var perfectThrowType = "EXCELLENT" // EXCELLENT, GREAT, or NICE
    private var autoWalkEnabled = false
    private var autoWalkSpeed = 1.0f
    private var autoCatchEnabled = true  // Set to true by default for testing
    private var autoCatchDelay = 500
    private var autoCatchRetryOnEscape = true
    private var autoCatchMaxRetries = 3
    private var autoCatchBallType = "POKE_BALL" // POKE_BALL, GREAT_BALL, or ULTRA_BALL
    private var injectionDelay = 0 // Delay before launching Pokemon GO in milliseconds
    private var traceCategoriesEnabled = mutableMapOf<String, Boolean>()

    // Frida console output reader thread
    private var fridaLogcatThread: Thread? = null
    private var isCapturingLogs = false

    /**
     * Extracts the Frida scripts from assets to the app's files directory
     * @return true if successful, false otherwise
     */
    fun extractScriptFromAssets(): Boolean {
        LogUtils.i(TAG, "Starting Frida script extraction process")
        val startTime = System.currentTimeMillis()
        var tsScript = ""
        var jsScript = ""
        var zygoteScript = ""
        var bypassScript = ""
        var scriptFile: File? = null
        var zygoteScriptFile: File? = null
        var bypassScriptFile: File? = null

        try {
            // Extract the TypeScript file
            LogUtils.d(TAG, "Reading TypeScript file from assets")
            val tsScriptStartTime = System.currentTimeMillis()
            try {
                tsScript = context.assets.open("pokemon-go-hook.ts").bufferedReader().use { it.readText() }
                val tsScriptTime = System.currentTimeMillis() - tsScriptStartTime
                LogUtils.d(TAG, "TypeScript file read successfully (${tsScript.length} bytes, ${tsScriptTime}ms)")
            } catch (e: IOException) {
                LogUtils.e(TAG, "Failed to read TypeScript file from assets", e)
                return false
            }

            // Extract the Zygote monitor script
            LogUtils.d(TAG, "Reading Zygote monitor script from assets")
            val zygoteScriptStartTime = System.currentTimeMillis()
            try {
                zygoteScript = context.assets.open(ZYGOTE_SCRIPT_FILENAME).bufferedReader().use { it.readText() }
                val zygoteScriptTime = System.currentTimeMillis() - zygoteScriptStartTime
                LogUtils.d(TAG, "Zygote monitor script read successfully (${zygoteScript.length} bytes, ${zygoteScriptTime}ms)")
            } catch (e: IOException) {
                LogUtils.e(TAG, "Failed to read Zygote monitor script from assets", e)
                return false
            }

            // Extract the Frida detection bypass script
            LogUtils.d(TAG, "Reading Frida detection bypass script from assets")
            val bypassScriptStartTime = System.currentTimeMillis()
            try {
                bypassScript = context.assets.open("frida-detection-bypass.ts").bufferedReader().use { it.readText() }
                val bypassScriptTime = System.currentTimeMillis() - bypassScriptStartTime
                LogUtils.d(TAG, "Frida detection bypass script read successfully (${bypassScript.length} bytes, ${bypassScriptTime}ms)")
            } catch (e: IOException) {
                LogUtils.e(TAG, "Failed to read Frida detection bypass script from assets", e)
                return false
            }

            // Convert TypeScript to JavaScript
            LogUtils.d(TAG, "Converting TypeScript to JavaScript")
            val conversionStartTime = System.currentTimeMillis()
            try {
                jsScript = convertTypeScriptToJavaScript(tsScript)
                val conversionTime = System.currentTimeMillis() - conversionStartTime
                LogUtils.d(TAG, "TypeScript conversion completed (${jsScript.length} bytes, ${conversionTime}ms)")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Failed to convert TypeScript to JavaScript", e)
                return false
            }

            // Write the script to the app's files directory
            LogUtils.d(TAG, "Writing JavaScript file to app's files directory")
            val scriptWriteStartTime = System.currentTimeMillis()
            try {
                scriptFile = File(context.filesDir, SCRIPT_FILENAME)
                FileOutputStream(scriptFile).use { it.write(jsScript.toByteArray()) }
                val scriptWriteTime = System.currentTimeMillis() - scriptWriteStartTime
                LogUtils.d(TAG, "JavaScript file written successfully (${scriptWriteTime}ms)")

                // Verify the file was written correctly
                if (!scriptFile.exists() || scriptFile.length() == 0L) {
                    LogUtils.e(TAG, "Script file was not written correctly: exists=${scriptFile.exists()}, size=${scriptFile.length()}")
                    return false
                }
            } catch (e: IOException) {
                LogUtils.e(TAG, "Failed to write JavaScript file to app's files directory", e)
                return false
            }

            // Write the Zygote monitor script to the app's files directory
            LogUtils.d(TAG, "Writing Zygote monitor script to app's files directory")
            val zygoteScriptWriteStartTime = System.currentTimeMillis()
            try {
                zygoteScriptFile = File(context.filesDir, ZYGOTE_SCRIPT_FILENAME)
                FileOutputStream(zygoteScriptFile).use { it.write(zygoteScript.toByteArray()) }
                val zygoteScriptWriteTime = System.currentTimeMillis() - zygoteScriptWriteStartTime
                LogUtils.d(TAG, "Zygote monitor script written successfully (${zygoteScriptWriteTime}ms)")

                // Verify the file was written correctly
                if (!zygoteScriptFile.exists() || zygoteScriptFile.length() == 0L) {
                    LogUtils.e(TAG, "Zygote monitor script file was not written correctly: exists=${zygoteScriptFile.exists()}, size=${zygoteScriptFile.length()}")
                    return false
                }
            } catch (e: IOException) {
                LogUtils.e(TAG, "Failed to write Zygote monitor script to app's files directory", e)
                return false
            }

            // Convert the Frida detection bypass script to JavaScript
            LogUtils.d(TAG, "Converting Frida detection bypass script to JavaScript")
            val bypassJsScript = convertTypeScriptToJavaScript(bypassScript)

            // Write the Frida detection bypass script to the app's files directory
            LogUtils.d(TAG, "Writing Frida detection bypass script to app's files directory")
            val bypassScriptWriteStartTime = System.currentTimeMillis()
            try {
                bypassScriptFile = File(context.filesDir, BYPASS_SCRIPT_FILENAME)
                FileOutputStream(bypassScriptFile).use { it.write(bypassJsScript.toByteArray()) }
                val bypassScriptWriteTime = System.currentTimeMillis() - bypassScriptWriteStartTime
                LogUtils.d(TAG, "Frida detection bypass script written successfully (${bypassScriptWriteTime}ms)")

                // Verify the file was written correctly
                if (!bypassScriptFile.exists() || bypassScriptFile.length() == 0L) {
                    LogUtils.e(TAG, "Frida detection bypass script file was not written correctly: exists=${bypassScriptFile.exists()}, size=${bypassScriptFile.length()}")
                    return false
                }
            } catch (e: IOException) {
                LogUtils.e(TAG, "Failed to write Frida detection bypass script to app's files directory", e)
                return false
            }

            val totalTime = System.currentTimeMillis() - startTime
            LogUtils.i(TAG, "Script extraction completed successfully in ${totalTime}ms")
            LogUtils.i(TAG, "Script extracted to ${scriptFile.absolutePath}")
            LogUtils.i(TAG, "Zygote monitor script extracted to ${zygoteScriptFile?.absolutePath}")
            LogUtils.i(TAG, "Frida detection bypass script extracted to ${bypassScriptFile?.absolutePath}")

            // Log script content for debugging
            LogUtils.d(TAG, "Script content (first 100 chars): ${jsScript.take(100)}...")
            LogUtils.d(TAG, "Zygote monitor script content (first 100 chars): ${zygoteScript.take(100)}...")
            LogUtils.d(TAG, "Frida detection bypass script content (first 100 chars): ${bypassJsScript.take(100)}...")

            return true
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            LogUtils.e(TAG, "Failed to extract scripts after ${totalTime}ms", e)
            return false
        }
    }

    /**
     * Simple conversion of TypeScript to JavaScript
     * This is a very basic conversion that just removes TypeScript-specific syntax
     * In a real app, you would use a proper TypeScript compiler
     */
    private fun convertTypeScriptToJavaScript(tsScript: String): String {
        // Remove TypeScript type annotations
        var jsScript = tsScript
            .replace(": Il2Cpp.Object", "")
            .replace(": string", "")
            .replace(": number", "")
            .replace(": boolean", "")
            .replace(": any", "")
            .replace("<Il2Cpp.Object>", "")
            .replace("// @ts-expect-error", "")

        // Remove import statement
        jsScript = jsScript.replace("import \"frida-il2cpp-bridge\";", "")

        return jsScript
    }

    /**
     * Updates the Frida script with the current configuration
     * @return true if successful, false otherwise
     */
    fun updateScript(): Boolean {
        try {
            // Read the base script from assets
            val baseScript = context.assets.open("pokemon-go-hook.ts").bufferedReader().use { it.readText() }

            // Modify the script based on configuration
            var modifiedScript = baseScript

            // Update perfect throw settings
            if (!perfectThrowEnabled) {
                // Disable the perfect throw functionality by commenting out the modifications
                modifiedScript = modifiedScript.replace(
                    "obj.field(ThrowStructFields[2].name).value = 0.00;      // Killzone Size",
                    "// Perfect throw disabled: obj.field(ThrowStructFields[2].name).value = 0.00;      // Killzone Size"
                ).replace(
                    "obj.field(ThrowStructFields[3].name).value = true;      // Curveball",
                    "// Perfect throw disabled: obj.field(ThrowStructFields[3].name).value = true;      // Curveball"
                ).replace(
                    "obj.field(ThrowStructFields[4].name).value = true;      // Hit killzone",
                    "// Perfect throw disabled: obj.field(ThrowStructFields[4].name).value = true;      // Hit killzone"
                )
            } else {
                // Update curveball setting
                modifiedScript = modifiedScript.replace(
                    "obj.field(ThrowStructFields[3].name).value = true;      // Curveball",
                    "obj.field(ThrowStructFields[3].name).value = ${perfectThrowCurveball};      // Curveball"
                )

                // Update throw type (killzone size)
                val killzoneSize = when (perfectThrowType) {
                    "EXCELLENT" -> 0.00
                    "GREAT" -> 0.50
                    "NICE" -> 0.80
                    else -> 0.00
                }
                modifiedScript = modifiedScript.replace(
                    "obj.field(ThrowStructFields[2].name).value = 0.00;      // Killzone Size",
                    "obj.field(ThrowStructFields[2].name).value = $killzoneSize;      // Killzone Size"
                )
            }

            // Update auto walk settings
            // Replace the auto walk configuration
            modifiedScript = modifiedScript.replace(
                """const autoWalkConfig = {
        enabled: false,
        speed: 1.0
    };""",
                """const autoWalkConfig = {
        enabled: $autoWalkEnabled,
        speed: $autoWalkSpeed
    };"""
            )

            // Update auto catch settings
            // Replace the auto catch configuration
            try {
                // First try with the default configuration (false)
                var replaced = modifiedScript.replace(
                    """const autoCatchConfig = {
        enabled: false,
        delay: 500,
        retryOnEscape: true,  // New option to retry catching if Pokémon escapes
        maxRetries: 3         // Maximum number of retry attempts
    };""",
                    """const autoCatchConfig = {
        enabled: $autoCatchEnabled,
        delay: $autoCatchDelay,
        retryOnEscape: $autoCatchRetryOnEscape,  // New option to retry catching if Pokémon escapes
        maxRetries: $autoCatchMaxRetries,        // Maximum number of retry attempts
        ballType: "$autoCatchBallType"           // Type of ball to use: POKE_BALL, GREAT_BALL, ULTRA_BALL
    };"""
                )

                // If the script was already modified to have enabled: true, try that pattern too
                if (replaced == modifiedScript) {
                    replaced = modifiedScript.replace(
                        """const autoCatchConfig = {
        enabled: true,  // Set to true by default for testing
        delay: 500,
        retryOnEscape: true,  // New option to retry catching if Pokémon escapes
        maxRetries: 3         // Maximum number of retry attempts
    };""",
                        """const autoCatchConfig = {
        enabled: $autoCatchEnabled,
        delay: $autoCatchDelay,
        retryOnEscape: $autoCatchRetryOnEscape,  // New option to retry catching if Pokémon escapes
        maxRetries: $autoCatchMaxRetries,        // Maximum number of retry attempts
        ballType: "$autoCatchBallType"           // Type of ball to use: POKE_BALL, GREAT_BALL, ULTRA_BALL
    };"""
                    )
                }

                // Try with the new pattern that includes ballType
                if (replaced == modifiedScript) {
                    replaced = modifiedScript.replace(
                        """const autoCatchConfig = {
        enabled: true,  // Set to true by default for testing
        delay: 500,
        retryOnEscape: true,  // New option to retry catching if Pokémon escapes
        maxRetries: 3,        // Maximum number of retry attempts
        ballType: "POKE_BALL" // Type of ball to use: POKE_BALL, GREAT_BALL, ULTRA_BALL
    };""",
                        """const autoCatchConfig = {
        enabled: $autoCatchEnabled,
        delay: $autoCatchDelay,
        retryOnEscape: $autoCatchRetryOnEscape,  // New option to retry catching if Pokémon escapes
        maxRetries: $autoCatchMaxRetries,        // Maximum number of retry attempts
        ballType: "$autoCatchBallType"           // Type of ball to use: POKE_BALL, GREAT_BALL, ULTRA_BALL
    };"""
                    )
                }

                // Update the script with our changes
                modifiedScript = replaced

                // Log whether the replacement was successful
                if (replaced != modifiedScript) {
                    LogUtils.i(TAG, "Successfully updated auto catch configuration in script")
                } else {
                    LogUtils.w(TAG, "Could not find auto catch configuration pattern in script")
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error updating auto catch configuration", e)
            }

            // Update trace category configuration
            if (traceCategoriesEnabled.isNotEmpty()) {
                // Find the tracingConfig object in the script
                val configRegex = """const tracingConfig = \{([^}]*)\};""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val configMatch = configRegex.find(modifiedScript)

                if (configMatch != null) {
                    // Build a new config object with our settings
                    val configBuilder = StringBuilder("const tracingConfig = {\n")

                    // Add each category with its enabled status
                    for ((category, enabled) in traceCategoriesEnabled) {
                        configBuilder.append("        $category: $enabled,\n")
                    }

                    // Close the config object
                    configBuilder.append("    };")

                    // Replace the config in the script
                    modifiedScript = modifiedScript.replaceRange(
                        configMatch.range.first,
                        configMatch.range.last + 1,
                        configBuilder.toString()
                    )

                    LogUtils.i(TAG, "Updated trace categories in script")
                }
            }

            // Convert the modified TypeScript to JavaScript
            val jsScript = convertTypeScriptToJavaScript(modifiedScript)

            // Write the modified script to the app's files directory
            val outputFile = File(context.filesDir, SCRIPT_FILENAME)
            FileOutputStream(outputFile).use { it.write(jsScript.toByteArray()) }

            LogUtils.i(TAG, "Script updated with: perfectThrow=${perfectThrowEnabled}, " +
                "curveball=${perfectThrowCurveball}, throwType=${perfectThrowType}, " +
                "autoWalk=${autoWalkEnabled}, autoWalkSpeed=${autoWalkSpeed}, " +
                "autoCatch=${autoCatchEnabled}, autoCatchDelay=${autoCatchDelay}, " +
                "autoCatchRetryOnEscape=${autoCatchRetryOnEscape}, autoCatchMaxRetries=${autoCatchMaxRetries}")
            return true
        } catch (e: IOException) {
            LogUtils.e(TAG, "Failed to update script", e)
            return false
        }
    }

    /**
     * Sets the enabled status for a trace category
     * @param category the category name
     * @param enabled true to enable, false to disable
     */
    fun setTraceCategoryEnabled(category: String, enabled: Boolean) {
        traceCategoriesEnabled[category] = enabled
        updateScript()
    }

    /**
     * Sets the enabled status for all trace categories
     * @param categories map of category names to enabled status
     */
    fun setTraceCategories(categories: Map<String, Boolean>) {
        traceCategoriesEnabled.clear()
        traceCategoriesEnabled.putAll(categories)
        updateScript()
    }

    /**
     * Sets whether the perfect throw feature is enabled
     * @param enabled true to enable, false to disable
     */
    fun setPerfectThrowEnabled(enabled: Boolean) {
        perfectThrowEnabled = enabled
        updateScript()
    }

    /**
     * Sets whether to throw curveballs
     * @param enabled true to enable, false to disable
     */
    fun setPerfectThrowCurveball(enabled: Boolean) {
        perfectThrowCurveball = enabled
        updateScript()
    }

    /**
     * Sets the type of throw to perform
     * @param type the throw type (EXCELLENT, GREAT, or NICE)
     */
    fun setPerfectThrowType(type: String) {
        perfectThrowType = type
        updateScript()
    }

    /**
     * Sets whether auto walk is enabled
     * @param enabled true to enable, false to disable
     */
    fun setAutoWalkEnabled(enabled: Boolean) {
        autoWalkEnabled = enabled
        updateScript()
    }

    /**
     * Sets the auto walk speed
     * @param speed the walking speed in m/s
     */
    fun setAutoWalkSpeed(speed: Float) {
        autoWalkSpeed = speed
        updateScript()
    }

    /**
     * Sets whether auto catch is enabled
     * @param enabled true to enable, false to disable
     */
    fun setAutoCatchEnabled(enabled: Boolean) {
        autoCatchEnabled = enabled
        updateScript()
    }

    /**
     * Sets the auto catch delay
     * @param delay the delay in milliseconds
     */
    fun setAutoCatchDelay(delay: Int) {
        autoCatchDelay = delay
        updateScript()
    }

    /**
     * Sets whether to retry catching if a Pokémon escapes
     * @param enabled true to enable, false to disable
     */
    fun setAutoCatchRetryOnEscape(enabled: Boolean) {
        autoCatchRetryOnEscape = enabled
        updateScript()
    }

    /**
     * Sets the maximum number of retry attempts for auto catch
     * @param maxRetries the maximum number of retries
     */
    fun setAutoCatchMaxRetries(maxRetries: Int) {
        autoCatchMaxRetries = maxRetries
        updateScript()
    }

    /**
     * Sets the type of Pokéball to use for auto catch
     * @param ballType the ball type (POKE_BALL, GREAT_BALL, or ULTRA_BALL)
     */
    fun setAutoCatchBallType(ballType: String) {
        autoCatchBallType = ballType
        updateScript()
        LogUtils.i(TAG, "Auto catch ball type set to $ballType")
    }

    /**
     * Gets the current Pokéball type for auto catch
     * @return the current ball type
     */
    fun getAutoCatchBallType(): String {
        return autoCatchBallType
    }

    /**
     * Sets the delay before injecting the Frida script into Pokémon GO
     * This delay is applied after Pokémon GO is launched but before the script is injected
     * It gives the app time to initialize before the Frida script is injected
     * @param delay the delay in milliseconds
     */
    fun setInjectionDelay(delay: Int) {
        injectionDelay = delay
        LogUtils.i(TAG, "Frida script injection delay set to $delay ms")

        // Make sure Frida Server is running
        if (!LibraryUtils.isFridaServerRunning()) {
            LogUtils.i(TAG, "Starting Frida Server for future injection")
            LibraryUtils.startFridaServer(context)
        }
    }

    /**
     * Gets the path to the extracted script
     * @return the absolute path to the script
     */
    fun getScriptPath(): String {
        return File(context.filesDir, SCRIPT_FILENAME).absolutePath
    }



    /**
     * Checks if the Pokémon GO app is installed
     * @return true if installed, false otherwise
     */
    fun isPokemonGoInstalled(): Boolean {
        val packageManager = context.packageManager

        // Check all possible package names
        for (packageName in POKEMON_GO_PACKAGES) {
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                LogUtils.i(TAG, "Found Pokémon GO with package name: $packageName")
                return true
            } catch (e: Exception) {
                LogUtils.d(TAG, "Pokémon GO not found with package name: $packageName")
                // Continue checking other package names
            }
        }

        // Also try to find by launch intent (more reliable in some cases)
        val installedPackages = packageManager.getInstalledPackages(0)
        LogUtils.i(TAG, "Checking ${installedPackages.size} installed packages")

        for (packageInfo in installedPackages) {
            val applicationInfo = packageInfo.applicationInfo
            if (applicationInfo != null) {
                val appName = applicationInfo.loadLabel(packageManager).toString()
                if (appName.contains("Pokémon GO", ignoreCase = true) ||
                    appName.contains("Pokemon GO", ignoreCase = true) ||
                    (appName.contains("Pokémon", ignoreCase = true) && appName.contains("GO", ignoreCase = true)) ||
                    (appName.contains("Pokemon", ignoreCase = true) && appName.contains("GO", ignoreCase = true)) ||
                    packageInfo.packageName.contains("pokemongo")) {
                    LogUtils.i(TAG, "Found Pokémon GO by app name: $appName, package: ${packageInfo.packageName}")
                    return true
                }
            }
        }

        LogUtils.w(TAG, "Pokémon GO is not installed")
        return false
    }

    /**
     * Launches Pokémon GO
     * @return true if launched successfully, false otherwise
     */
    fun launchPokemonGo(): Boolean {
        LogUtils.i(TAG, "Starting Pokémon GO launch process")
        val startTime = System.currentTimeMillis()

        LogUtils.d(TAG, "Checking for Pokémon GO package")

        // Try all possible package names
        for (packageName in POKEMON_GO_PACKAGES) {
            LogUtils.d(TAG, "Trying package name: $packageName")
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                LogUtils.d(TAG, "Found launch intent for package: $packageName")

                // Add flags to ensure a clean start
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

                try {
                    LogUtils.d(TAG, "Starting Pokémon GO activity with intent: $intent")
                    context.startActivity(intent)

                    val launchTime = System.currentTimeMillis() - startTime
                    LogUtils.i(TAG, "Launched Pokémon GO with package name: $packageName (took ${launchTime}ms)")

                    // Start capturing Frida logs automatically
                    startCapturingFridaLogs()

                    return true
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Failed to launch Pokémon GO with package name: $packageName", e)
                    // Continue trying other packages
                }
            }
        }

        LogUtils.d(TAG, "Package name lookup failed, trying to find by app name")

        // If we couldn't find it by package name, try to find it by app name
        val packageManager = context.packageManager
        val installedPackages = packageManager.getInstalledPackages(0)
        LogUtils.d(TAG, "Checking ${installedPackages.size} installed packages")

        for (packageInfo in installedPackages) {
            val applicationInfo = packageInfo.applicationInfo
            if (applicationInfo != null) {
                val appName = applicationInfo.loadLabel(packageManager).toString()
                if (appName.contains("Pokémon GO", ignoreCase = true) ||
                    appName.contains("Pokemon GO", ignoreCase = true)) {
                    LogUtils.d(TAG, "Found potential match: $appName (${packageInfo.packageName})")

                    val intent = packageManager.getLaunchIntentForPackage(packageInfo.packageName)
                    if (intent != null) {
                        LogUtils.d(TAG, "Found launch intent for app: $appName")

                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

                        try {
                            LogUtils.d(TAG, "Starting Pokémon GO activity with intent: $intent")
                            context.startActivity(intent)

                            val launchTime = System.currentTimeMillis() - startTime
                            LogUtils.i(TAG, "Launched Pokémon GO by app name: $appName, package: ${packageInfo.packageName} (took ${launchTime}ms)")

                            // Start capturing Frida logs automatically
                            startCapturingFridaLogs()

                            return true
                        } catch (e: Exception) {
                            LogUtils.e(TAG, "Failed to launch Pokémon GO by app name: $appName", e)
                            // Continue trying other packages
                        }
                    }
                }
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        LogUtils.e(TAG, "Failed to get launch intent for Pokémon GO after ${totalTime}ms")
        return false
    }

    /**
     * Gets the actual package name of Pokémon GO installed on the device
     * @return the package name or null if not found
     */
    fun getPokemonGoPackageName(): String? {
        val packageManager = context.packageManager

        // First try the known package names
        for (packageName in POKEMON_GO_PACKAGES) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                return packageName
            } catch (e: Exception) {
                // Continue checking other package names
            }
        }

        // If not found, try to find by app name
        val installedPackages = packageManager.getInstalledPackages(0)
        for (packageInfo in installedPackages) {
            val applicationInfo = packageInfo.applicationInfo
            if (applicationInfo != null) {
                val appName = applicationInfo.loadLabel(packageManager).toString()
                if (appName.contains("Pokémon GO", ignoreCase = true) ||
                    appName.contains("Pokemon GO", ignoreCase = true) ||
                    (appName.contains("Pokémon", ignoreCase = true) && appName.contains("GO", ignoreCase = true)) ||
                    (appName.contains("Pokemon", ignoreCase = true) && appName.contains("GO", ignoreCase = true)) ||
                    packageInfo.packageName.contains("pokemongo")) {
                    return packageInfo.packageName
                }
            }
        }

        return null
    }

    /**
     * Executes a root command
     * @param command the command to execute
     * @return true if successful, false otherwise
     */
    fun executeRootCommand(command: String): Boolean {
        var process: Process? = null
        var outputStream: java.io.DataOutputStream? = null
        try {
            process = Runtime.getRuntime().exec("su")
            outputStream = java.io.DataOutputStream(process.outputStream)
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            val exitValue = process.waitFor()
            return exitValue == 0
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error executing root command: $command", e)
            return false
        } finally {
            try {
                outputStream?.close()
                process?.destroy()
            } catch (e: IOException) {
                // Ignore close error
            }
        }
    }

    /**
     * Start capturing Frida logs from logcat
     * This will capture all console.log output from the Frida script
     */
    fun startCapturingFridaLogs() {
        if (isCapturingLogs) {
            LogUtils.d(TAG, "Frida log capture already running, skipping")
            return
        }

        isCapturingLogs = true
        val startTime = System.currentTimeMillis()
        LogUtils.i(TAG, "Starting Frida log capture")

        fridaLogcatThread = Thread {
            try {
                // Clear logcat first
                LogUtils.d(TAG, "Clearing logcat buffer")
                val clearProcess = Runtime.getRuntime().exec("logcat -c")
                val clearResult = clearProcess.waitFor()
                LogUtils.d(TAG, "Logcat buffer cleared with result: $clearResult")

                // Start logcat process to capture Frida logs with expanded filters
                LogUtils.d(TAG, "Starting logcat process for Frida logs")

                // Use a much more inclusive filter to capture all possible Frida-related logs
                // This includes standard tags and our custom FRIDA_* prefixes
                // Note: We're now using a more comprehensive filter to catch all possible Frida-related logs
                val logcatCmd = "logcat -v threadtime" +
                        " frida:V frida-*:V Frida:V FRIDA:V" +  // Frida core logs
                        " FRIDA_*:V" +                          // Our custom FRIDA_ prefixed logs
                        " DEBUG:V ERROR:V TRACE:V" +            // Standard debug categories
                        " System.out:V System.err:V" +          // System output that might contain Frida logs
                        " Unity:V UnityMain:V" +                // Unity logs (Pokémon GO is a Unity game)
                        " ActivityThread:V" +                   // Activity thread logs for library loading
                        " AndroidRuntime:V" +                   // Runtime logs for native library issues
                        " dalvikvm:V art:V" +                   // VM logs that might show library loading issues
                        " libc:V" +                             // Native library loading logs
                        " dex2oat:V" +                          // DEX optimization logs
                        " PackageManager:V" +                   // Package manager logs
                        " *:S"                                  // Silence other logs

                LogUtils.d(TAG, "Logcat command: $logcatCmd")
                LogUtils.i(TAG, "Enhanced Frida log capture started with expanded filters")

                val process = Runtime.getRuntime().exec(logcatCmd)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                LogUtils.d(TAG, "Logcat process started, reading logs")

                // Also capture stderr to see any errors from the logcat process
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                Thread {
                    try {
                        var errorLine: String? = null
                        while (isCapturingLogs && errorReader.readLine().also { errorLine = it } != null) {
                            errorLine?.let { line ->
                                LogUtils.e(TAG, "Logcat error: $line")
                            }
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading logcat error stream", e)
                    }
                }.start()

                var line: String? = null
                var logCount = 0
                val captureStartTime = System.currentTimeMillis()

                while (isCapturingLogs && reader.readLine().also { line = it } != null) {
                    line?.let { logLine ->
                        // Enhanced filtering for Frida-related logs with more inclusive patterns
                        // We've expanded the filter to catch more potential Frida-related logs
                        if (logLine.contains("frida", ignoreCase = true) ||
                            logLine.contains("FRIDA", ignoreCase = true) ||  // Explicit FRIDA uppercase
                            logLine.contains("TRACE", ignoreCase = true) ||
                            logLine.contains("DEBUG", ignoreCase = true) ||
                            logLine.contains("ERROR", ignoreCase = true) ||
                            logLine.contains("FRIDA_", ignoreCase = true) ||  // Our custom FRIDA_ prefix
                            logLine.contains("INIT", ignoreCase = true) ||    // Initialization logs
                            logLine.contains("SELF_TEST", ignoreCase = true) || // Self-test logs
                            logLine.contains("HOOK", ignoreCase = true) ||    // Any hook-related logs
                            logLine.contains("gadget", ignoreCase = true) ||  // Frida gadget references
                            logLine.contains("script", ignoreCase = true) ||  // Script-related logs
                            logLine.contains("il2cpp", ignoreCase = true) ||  // IL2CPP bridge references
                            logLine.contains("pokemon", ignoreCase = true) || // Pokémon GO specific logs
                            logLine.contains("console.log", ignoreCase = true) || // JavaScript console logs
                            logLine.contains("console.error", ignoreCase = true) || // JavaScript error logs
                            logLine.contains("console.warn", ignoreCase = true) || // JavaScript warning logs
                            logLine.contains("console.info", ignoreCase = true) || // JavaScript info logs
                            logLine.contains("console.debug", ignoreCase = true) || // JavaScript debug logs
                            logLine.contains("library", ignoreCase = true) || // Library loading logs
                            logLine.contains("load", ignoreCase = true) ||    // Library loading logs
                            logLine.contains("dlopen", ignoreCase = true) ||  // Dynamic library opening
                            logLine.contains("linker", ignoreCase = true) ||  // Dynamic linker logs
                            logLine.contains("native", ignoreCase = true) ||  // Native code logs
                            logLine.contains("jni", ignoreCase = true) ||     // JNI logs
                            logLine.contains("so", ignoreCase = true) ||      // .so library references
                            logLine.contains("libfrida", ignoreCase = true) || // Specific Frida library references
                            logLine.contains("[+]") ||                        // Common Frida log prefix
                            logLine.contains("[-]")) {                        // Common Frida error prefix

                            // Log the raw line for debugging
                            LogUtils.d(TAG, "Raw Frida log: $logLine")

                            // Extract the actual message
                            val message = extractMessageFromLogcat(logLine)
                            if (message.isNotEmpty()) {
                                // Send to LogUtils as a trace message
                                LogUtils.trace(message)
                                logCount++

                                // Log statistics periodically
                                if (logCount % 25 == 0) {  // Reduced from 50 to 25 for more frequent updates
                                    val elapsedTime = System.currentTimeMillis() - captureStartTime
                                    val logsPerSecond = (logCount * 1000.0 / elapsedTime).toInt()
                                    LogUtils.d(TAG, "Frida log statistics: $logCount logs captured, $logsPerSecond logs/sec")
                                }
                            }
                        }
                    }
                }

                LogUtils.d(TAG, "Logcat reader loop ended, cleaning up")
                reader.close()
                errorReader.close()
                process.destroy()

                val totalLogs = logCount
                val totalTime = System.currentTimeMillis() - captureStartTime
                LogUtils.i(TAG, "Frida log capture ended: $totalLogs logs captured over ${totalTime/1000} seconds")

            } catch (e: Exception) {
                LogUtils.e(TAG, "Error capturing Frida logs", e)
            } finally {
                isCapturingLogs = false
                LogUtils.i(TAG, "Frida log capture thread terminated")

                // Try to restart log capture if it was terminated unexpectedly
                if (isCapturingLogs) {
                    LogUtils.w(TAG, "Log capture was terminated unexpectedly, attempting to restart")
                    Thread.sleep(1000) // Wait a bit before restarting
                    startCapturingFridaLogs()
                }
            }
        }

        fridaLogcatThread?.name = "FridaLogcatThread"
        fridaLogcatThread?.priority = Thread.MAX_PRIORITY
        fridaLogcatThread?.isDaemon = true // Make it a daemon thread so it doesn't prevent app exit
        fridaLogcatThread?.start()

        LogUtils.d(TAG, "Frida log capture thread started with name: ${fridaLogcatThread?.name}, priority: ${fridaLogcatThread?.priority}")
        LogUtils.i(TAG, "Frida log capture initialized in ${System.currentTimeMillis() - startTime}ms")

        // Also try to run a direct adb logcat command to see if we can capture more logs
        // This is a separate thread that uses a different approach to capture logs
        Thread {
            try {
                LogUtils.i(TAG, "Starting additional direct logcat capture")
                // Use a more comprehensive grep pattern to catch all relevant logs
                val directLogcatCmd = "logcat -v threadtime | grep -i 'frida\\|FRIDA\\|gadget\\|script\\|il2cpp\\|pokemon\\|\\[+\\]\\|\\[-\\]\\|library\\|load\\|dlopen\\|linker\\|native\\|jni\\|so\\|libfrida'"
                val directProcess = Runtime.getRuntime().exec(directLogcatCmd)
                val directReader = BufferedReader(InputStreamReader(directProcess.inputStream))

                var directLine: String? = null
                while (isCapturingLogs && directReader.readLine().also { directLine = it } != null) {
                    directLine?.let { logLine ->
                        LogUtils.d(TAG, "Direct logcat: $logLine")
                        LogUtils.trace(extractMessageFromLogcat(logLine))
                    }
                }

                directReader.close()
                directProcess.destroy()
                LogUtils.i(TAG, "Direct logcat capture ended")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error in direct logcat capture", e)
            }
        }.apply {
            name = "DirectLogcatThread"
            priority = Thread.MAX_PRIORITY - 1
            isDaemon = true
            start()
        }

        // Add a watchdog thread to ensure log capture continues even if the app is in the background
        Thread {
            try {
                LogUtils.i(TAG, "Starting log capture watchdog")
                while (isCapturingLogs) {
                    Thread.sleep(30000) // Check every 30 seconds

                    // If the main thread is no longer alive but we're still supposed to be capturing logs
                    if (fridaLogcatThread?.isAlive != true && isCapturingLogs) {
                        LogUtils.w(TAG, "Log capture thread died but isCapturingLogs=true, restarting")
                        fridaLogcatThread = null
                        startCapturingFridaLogs()
                    }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error in log capture watchdog", e)
            }
        }.apply {
            name = "LogWatchdogThread"
            isDaemon = true
            start()
        }
    }

    /**
     * Stop capturing Frida logs
     */
    fun stopCapturingFridaLogs() {
        if (!isCapturingLogs) {
            return
        }

        isCapturingLogs = false
        fridaLogcatThread?.interrupt()
        fridaLogcatThread = null
        LogUtils.i(TAG, "Stopped Frida log capture")
    }

    /**
     * Extract the actual message from a logcat line
     * Handles various logcat formats and extracts the most relevant part
     */
    private fun extractMessageFromLogcat(logLine: String): String {
        LogUtils.d(TAG, "Extracting message from logcat line: $logLine")

        try {
            // First check if this is a threadtime format logcat line
            // Format: date time PID-TID/tag priority: message
            val threadTimeRegex = """^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+[A-Z]\s+(.+?):\s+(.+)$""".toRegex()
            val threadTimeMatch = threadTimeRegex.find(logLine)

            if (threadTimeMatch != null) {
                val tag = threadTimeMatch.groupValues[1]
                val message = threadTimeMatch.groupValues[2].trim()
                LogUtils.d(TAG, "Extracted message from threadtime format: [$tag] $message")
                return message
            }

            // Try standard format: tag: message
            val standardParts = logLine.split(": ", limit = 2)
            if (standardParts.size > 1) {
                val message = standardParts[1].trim()
                LogUtils.d(TAG, "Extracted message from standard format: $message")
                return message
            }

            // Try to extract JSON-like messages that might be embedded
            val jsonRegex = """\{.+\}""".toRegex()
            val jsonMatch = jsonRegex.find(logLine)
            if (jsonMatch != null) {
                val jsonPart = jsonMatch.value
                LogUtils.d(TAG, "Extracted JSON part from logcat: $jsonPart")
                return jsonPart
            }

            // If we can't parse it with any known format, just return the whole line
            LogUtils.d(TAG, "Could not parse logcat line with known formats, returning as-is")
            return logLine
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error extracting message from logcat line", e)
            // If there's an error in parsing, return the original line
            return logLine
        }
    }

    /**
     * Check if Frida Server is running and properly connected to Pokémon GO
     * This uses direct ADB commands to check for Frida processes
     */
    fun checkFridaStatus(): Boolean {
        LogUtils.i(TAG, "Checking Frida Server status")

        try {
            // First check if Frida server is running
            if (!LibraryUtils.isFridaServerRunning()) {
                LogUtils.e(TAG, "Frida server is not running")
                return false
            }

            // Get the Pokémon GO package name
            val packageName = getPokemonGoPackageName()
            if (packageName == null) {
                LogUtils.e(TAG, "Cannot check Frida status: Pokémon GO not installed")
                return false
            }

            // Check if Pokémon GO is running
            val psProcess = Runtime.getRuntime().exec("ps")
            val psReader = BufferedReader(InputStreamReader(psProcess.inputStream))
            var isRunning = false
            var pid = ""

            var line: String?
            while (psReader.readLine().also { line = it } != null) {
                if (line?.contains(packageName) == true) {
                    isRunning = true
                    // Extract PID
                    val parts = line?.trim()?.split("\\s+".toRegex())
                    if (parts != null && parts.size > 1) {
                        pid = parts[1]
                    }
                    break
                }
            }

            if (!isRunning) {
                LogUtils.w(TAG, "Pokémon GO is not running, cannot check Frida status")
                return false
            }

            LogUtils.d(TAG, "Pokémon GO is running with PID: $pid")

            // Try to list processes using frida-ps to check if Frida can see Pokémon GO
            try {
                val fridaPsCmd = "frida-ps -p $pid"
                val fridaPsProcess = Runtime.getRuntime().exec(fridaPsCmd)
                val fridaPsReader = BufferedReader(InputStreamReader(fridaPsProcess.inputStream))
                val fridaPsOutput = fridaPsReader.readText()

                if (fridaPsOutput.contains(packageName) || fridaPsOutput.contains(pid)) {
                    LogUtils.i(TAG, "Frida can see Pokémon GO process: $fridaPsOutput")

                    // Check if our script is being loaded
                    try {
                        val scriptPath = "/data/data/${context.packageName}/files/$SCRIPT_FILENAME"
                        val checkScriptCmd = "ls -la $scriptPath"
                        val scriptProcess = Runtime.getRuntime().exec(checkScriptCmd)
                        val scriptReader = BufferedReader(InputStreamReader(scriptProcess.inputStream))
                        val scriptOutput = scriptReader.readText()

                        LogUtils.i(TAG, "Script file check: $scriptOutput")

                        // Check if the script is being loaded by Frida
                        val logcatProcess = Runtime.getRuntime().exec("logcat -d | grep -i frida")
                        val logcatReader = BufferedReader(InputStreamReader(logcatProcess.inputStream))
                        val logcatOutput = logcatReader.readText()

                        LogUtils.i(TAG, "Recent Frida logs from logcat: ${logcatOutput.take(500)}...")

                        // Check if auto-catch is enabled in the script
                        val scriptFile = File(context.filesDir, SCRIPT_FILENAME)
                        if (scriptFile.exists()) {
                            val scriptContent = scriptFile.readText()
                            val autoCatchEnabled = scriptContent.contains("enabled: true")
                            LogUtils.i(TAG, "Auto-catch enabled in script: $autoCatchEnabled")
                        } else {
                            LogUtils.w(TAG, "Script file does not exist at ${scriptFile.absolutePath}")
                        }

                        // Check for any Frida-related errors in logcat
                        val errorLogcatProcess = Runtime.getRuntime().exec("logcat -d | grep -i \"frida.*error\\|error.*frida\"")
                        val errorLogcatReader = BufferedReader(InputStreamReader(errorLogcatProcess.inputStream))
                        val errorLogcatOutput = errorLogcatReader.readText()

                        if (errorLogcatOutput.isNotEmpty()) {
                            LogUtils.w(TAG, "Found Frida-related errors in logcat: ${errorLogcatOutput.take(500)}...")
                        } else {
                            LogUtils.i(TAG, "No Frida-related errors found in logcat")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error checking script status", e)
                    }

                    return true
                } else {
                    LogUtils.w(TAG, "Frida cannot see Pokémon GO process")
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error running frida-ps", e)
            }

            // Alternative check: Try to attach to the process using frida
            try {
                val attachCmd = "frida -p $pid"
                val attachProcess = Runtime.getRuntime().exec(attachCmd)
                // Wait a bit to see if it attaches
                Thread.sleep(1000)
                // Kill the process since we just want to check if it can attach
                attachProcess.destroy()

                // If we got here without an exception, Frida can probably attach to the process
                LogUtils.i(TAG, "Frida can attach to Pokémon GO process")
                return true
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error attaching to Pokémon GO with Frida", e)
            }

            // Check logcat for any Frida-related messages
            val logcatProcess = Runtime.getRuntime().exec("logcat -d | grep -i \"frida\\|server\\|script\\|inject\"")
            val logcatReader = BufferedReader(InputStreamReader(logcatProcess.inputStream))
            val logcatOutput = logcatReader.readText()

            if (logcatOutput.isNotEmpty()) {
                LogUtils.i(TAG, "Found Frida-related logs: ${logcatOutput.take(500)}...")
                // If we have Frida logs, it might be working
                return true
            }

            LogUtils.w(TAG, "No evidence that Frida is connected to Pokémon GO")
            return false

        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking Frida status", e)
            return false
        }
    }

    /**
     * Launch Pokémon GO with tracing enabled
     */
    fun launchPokemonGoWithTracing(): Boolean {
        // Start capturing logs before launching
        startCapturingFridaLogs()

        // Launch the app
        val success = launchPokemonGo()

        if (success) {
            LogUtils.i(TAG, "Launched Pokémon GO with tracing enabled")

            // Check Frida status after a short delay to allow it to load
            Thread {
                try {
                    Thread.sleep(5000) // Wait 5 seconds
                    val fridaStatus = checkFridaStatus()
                    LogUtils.i(TAG, "Frida status check after launch: ${if (fridaStatus) "LOADED" else "NOT LOADED"}")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error in delayed Frida status check", e)
                }
            }.start()

        } else {
            // If launch failed, stop capturing logs
            stopCapturingFridaLogs()
            LogUtils.e(TAG, "Failed to launch Pokémon GO with tracing")
        }

        return success
    }

    /**
     * Stop Pokémon GO and clean up resources
     * @return true if the operation was successful, false otherwise
     */
    fun stopPokemonGo(): Boolean {
        LogUtils.i(TAG, "Stopping Pokémon GO")

        try {
            // Get the package name
            val packageName = getPokemonGoPackageName()

            if (packageName == null) {
                LogUtils.e(TAG, "Cannot stop Pokémon GO: Package name not found")
                return false
            }

            // First try to gracefully stop the app using the activity manager
            try {
                LogUtils.d(TAG, "Attempting to gracefully stop Pokémon GO")
                val stopCmd = "am force-stop $packageName"
                val process = Runtime.getRuntime().exec(stopCmd)
                val result = process.waitFor()

                LogUtils.i(TAG, "Force-stop command result: $result")

                // Check if the app is still running
                if (isAppRunning(packageName)) {
                    LogUtils.w(TAG, "Pokémon GO is still running after force-stop command")
                } else {
                    LogUtils.i(TAG, "Pokémon GO successfully stopped")
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error stopping Pokémon GO with activity manager", e)
            }

            // As a backup, try to kill the process directly
            try {
                LogUtils.d(TAG, "Attempting to kill Pokémon GO process")

                // Find the process ID
                val findPidCmd = "ps | grep $packageName"
                val findPidProcess = Runtime.getRuntime().exec(findPidCmd)
                val reader = BufferedReader(InputStreamReader(findPidProcess.inputStream))
                var line: String?
                var pid: String? = null

                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        if (it.contains(packageName)) {
                            // Extract the PID (second column in ps output)
                            val parts = it.trim().split("\\s+".toRegex())
                            if (parts.size > 1) {
                                pid = parts[1]
                                LogUtils.d(TAG, "Found Pokémon GO process with PID: $pid")
                            }
                        }
                    }
                }

                // Kill the process if we found a PID
                if (pid != null) {
                    val killCmd = "kill -9 $pid"
                    val killProcess = Runtime.getRuntime().exec(killCmd)
                    val killResult = killProcess.waitFor()
                    LogUtils.i(TAG, "Kill command result: $killResult")
                } else {
                    LogUtils.w(TAG, "Could not find Pokémon GO process ID")
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error killing Pokémon GO process", e)
            }

            // Stop capturing Frida logs
            stopCapturingFridaLogs()

            // Final check to see if the app is still running
            if (isAppRunning(packageName)) {
                LogUtils.w(TAG, "Pokémon GO is still running after all stop attempts")
                return false
            } else {
                LogUtils.i(TAG, "Pokémon GO successfully stopped")
                return true
            }

        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping Pokémon GO", e)
            return false
        }
    }

    /**
     * Check if an app is currently running
     * @param packageName the package name of the app to check
     * @return true if the app is running, false otherwise
     */
    fun isAppRunning(packageName: String): Boolean {
        try {
            val cmd = "ps | grep $packageName"
            val process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    if (it.contains(packageName)) {
                        return true
                    }
                }
            }

            return false
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking if app is running", e)
            return false
        }
    }

    /**
     * Start Zygote monitoring to detect when Pokémon GO is launched
     * @return true if successful, false otherwise
     */
    fun startZygoteMonitoring(): Boolean {
        LogUtils.i(TAG, "Starting Zygote monitoring")

        try {
            // First check if the Zygote script exists
            val zygoteScriptFile = File(context.filesDir, ZYGOTE_SCRIPT_FILENAME)
            if (!zygoteScriptFile.exists() || zygoteScriptFile.length() == 0L) {
                LogUtils.e(TAG, "Zygote script file missing or empty: ${zygoteScriptFile.absolutePath}")
                return false
            }

            // Check if Frida server is installed and running
            if (!LibraryUtils.isFridaServerInstalled(context)) {
                LogUtils.i(TAG, "Frida server is not installed, extracting and installing from assets")

                // Extract and install Frida server from assets
                val extractedPath = LibraryUtils.extractFridaServerFromAssets(context)
                if (extractedPath == null) {
                    LogUtils.e(TAG, "Failed to extract Frida server from assets")
                    return false
                }

                // Set executable permissions
                if (!LibraryUtils.setFridaServerPermissions(context, extractedPath)) {
                    LogUtils.e(TAG, "Failed to set permissions on Frida server at ${LibraryUtils.getFridaServerPath(context)}")
                    return false
                }
            }

            // Start Frida server if it's not already running
            if (!LibraryUtils.isFridaServerRunning()) {
                if (!LibraryUtils.startFridaServer(context)) {
                    LogUtils.e(TAG, "Failed to start Frida server")
                    return false
                }
            }

            // Start the Zygote monitor
            zygoteMonitor.startMonitoring { packageName, pid ->
                LogUtils.i(TAG, "Pokémon GO detected: $packageName (PID: $pid)")

                // Inject the Frida script into the process
                injectFridaScript(packageName, pid)
            }

            return true
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error starting Zygote monitoring", e)
            return false
        }
    }

    /**
     * Stop Zygote monitoring
     */
    fun stopZygoteMonitoring() {
        LogUtils.i(TAG, "Stopping Zygote monitoring")
        zygoteMonitor.stopMonitoring()

        // We don't stop the Frida server here as it might be used by other apps
        // If you want to stop it, uncomment the line below
        // LibraryUtils.stopFridaServer()
    }

    /**
     * Inject the Frida script into a running Pokémon GO process using Frida Server
     * @param packageName The package name of the Pokémon GO process
     * @param pid The process ID of the Pokémon GO process
     * @return true if successful, false otherwise
     */
    private fun injectFridaScript(packageName: String, pid: Int): Boolean {
        LogUtils.i(TAG, "Preparing to inject Frida script into Pokémon GO process: $packageName (PID: $pid)")

        try {
            // First verify the process is still running
            if (!isProcessRunning(pid)) {
                LogUtils.e(TAG, "Process $pid is no longer running, cannot inject script")
                return false
            }

            // Check if the script file exists
            val scriptFile = File(context.filesDir, SCRIPT_FILENAME)
            if (!scriptFile.exists() || scriptFile.length() == 0L) {
                LogUtils.e(TAG, "Script file missing or empty: ${scriptFile.absolutePath}")
                return false
            }

            // Check if the Frida detection bypass script exists
            val bypassScriptFile = File(context.filesDir, BYPASS_SCRIPT_FILENAME)
            if (!bypassScriptFile.exists() || bypassScriptFile.length() == 0L) {
                LogUtils.e(TAG, "Frida detection bypass script file missing or empty: ${bypassScriptFile.absolutePath}")
                return false
            }

            LogUtils.i(TAG, "Script file exists at ${scriptFile.absolutePath}, size: ${scriptFile.length()} bytes")
            LogUtils.i(TAG, "Frida detection bypass script exists at ${bypassScriptFile.absolutePath}, size: ${bypassScriptFile.length()} bytes")

            // Make sure Frida server is running
            if (!LibraryUtils.isFridaServerRunning()) {
                LogUtils.w(TAG, "Frida server is not running, attempting to start it")
                if (!LibraryUtils.startFridaServer(context)) {
                    LogUtils.e(TAG, "Failed to start Frida server, cannot inject script")
                    return false
                }
            }

            // Apply injection delay if set
            if (injectionDelay > 0) {
                LogUtils.i(TAG, "Applying injection delay of $injectionDelay ms before injecting Frida script")
                try {
                    Thread.sleep(injectionDelay.toLong())
                    LogUtils.d(TAG, "Injection delay completed, now injecting script")
                } catch (e: InterruptedException) {
                    LogUtils.e(TAG, "Injection delay was interrupted", e)
                }
            }

            // First inject the Frida detection bypass script
            try {
                LogUtils.i(TAG, "Injecting Frida detection bypass script first")
                val bypassInjectCmd = "frida -p $pid -l ${bypassScriptFile.absolutePath} --runtime=v8"
                LogUtils.d(TAG, "Running bypass command: $bypassInjectCmd")

                val bypassProcess = Runtime.getRuntime().exec(bypassInjectCmd)

                // Read the output to check if bypass injection was successful
                val bypassReader = BufferedReader(InputStreamReader(bypassProcess.inputStream))
                val bypassErrorReader = BufferedReader(InputStreamReader(bypassProcess.errorStream))

                // Start a thread to read the output
                Thread {
                    try {
                        var line: String?
                        while (bypassReader.readLine().also { line = it } != null) {
                            LogUtils.i(TAG, "Frida bypass output: $line")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading Frida bypass output", e)
                    }
                }.start()

                // Start a thread to read the error output
                Thread {
                    try {
                        var line: String?
                        while (bypassErrorReader.readLine().also { line = it } != null) {
                            LogUtils.e(TAG, "Frida bypass error: $line")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading Frida bypass error output", e)
                    }
                }.start()

                // Wait a bit to let the bypass script initialize
                Thread.sleep(1000)

                LogUtils.i(TAG, "Frida detection bypass script injected, now injecting main script")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error injecting Frida detection bypass script", e)
                // Continue anyway to try the main script
            }

            // Now try injection of the main script using frida command
            try {
                LogUtils.i(TAG, "Injecting main script using frida command")
                val injectCmd = "frida -p $pid -l ${scriptFile.absolutePath} --runtime=v8"
                LogUtils.d(TAG, "Running command: $injectCmd")

                val process = Runtime.getRuntime().exec(injectCmd)

                // Read the output to check if injection was successful
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                // Start a thread to read the output
                Thread {
                    try {
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            LogUtils.i(TAG, "Frida output: $line")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading Frida output", e)
                    }
                }.start()

                // Start a thread to read the error output
                Thread {
                    try {
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            LogUtils.e(TAG, "Frida error: $line")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading Frida error output", e)
                    }
                }.start()

                // Wait a bit to see if there are any immediate errors
                Thread.sleep(2000)

                // Check if the process is still running
                try {
                    val exitCode = process.exitValue()
                    LogUtils.w(TAG, "Frida process exited with code: $exitCode")
                    // If exit code is 0, it might have completed successfully
                    if (exitCode == 0) {
                        LogUtils.i(TAG, "Frida process completed successfully")
                        return true
                    }

                    // If we got here with a non-zero exit code, try the alternative method
                    LogUtils.w(TAG, "Frida command failed with exit code: $exitCode, trying alternative method")
                } catch (e: IllegalThreadStateException) {
                    // Process is still running, which is good
                    LogUtils.i(TAG, "Frida process is still running, injection likely successful")
                    return true
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error with frida command injection", e)
                // Continue to alternative method
            }

            // Alternative method: Using frida-inject command
            try {
                // First inject the bypass script
                LogUtils.i(TAG, "Trying alternative injection method for bypass script: frida-inject")
                val bypassInjectCmd = "frida-inject -p $pid -s ${bypassScriptFile.absolutePath} --runtime=v8"
                LogUtils.d(TAG, "Running bypass command: $bypassInjectCmd")

                val bypassProcess = Runtime.getRuntime().exec(bypassInjectCmd)

                // Read the output to check if bypass injection was successful
                val bypassReader = BufferedReader(InputStreamReader(bypassProcess.inputStream))
                val bypassErrorReader = BufferedReader(InputStreamReader(bypassProcess.errorStream))

                // Start a thread to read the output
                Thread {
                    try {
                        var line: String?
                        while (bypassReader.readLine().also { line = it } != null) {
                            LogUtils.i(TAG, "Frida bypass inject output: $line")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading Frida bypass inject output", e)
                    }
                }.start()

                // Start a thread to read the error output
                Thread {
                    try {
                        var line: String?
                        while (bypassErrorReader.readLine().also { line = it } != null) {
                            LogUtils.e(TAG, "Frida bypass inject error: $line")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading Frida bypass inject error output", e)
                    }
                }.start()

                // Wait a bit to let the bypass script initialize
                Thread.sleep(1000)

                LogUtils.i(TAG, "Frida detection bypass script injected using alternative method, now injecting main script")

                // Now inject the main script
                LogUtils.i(TAG, "Trying alternative injection method for main script: frida-inject")
                val injectCmd = "frida-inject -p $pid -s ${scriptFile.absolutePath} --runtime=v8"
                LogUtils.d(TAG, "Running command: $injectCmd")

                val process = Runtime.getRuntime().exec(injectCmd)

                // Read the output to check if injection was successful
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                // Start a thread to read the output
                Thread {
                    try {
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            LogUtils.i(TAG, "Frida inject output: $line")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading Frida inject output", e)
                    }
                }.start()

                // Start a thread to read the error output
                Thread {
                    try {
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            LogUtils.e(TAG, "Frida inject error: $line")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading Frida inject error output", e)
                    }
                }.start()

                // Wait a bit to see if there are any immediate errors
                Thread.sleep(2000)

                // Check if the process is still running
                try {
                    val exitCode = process.exitValue()
                    LogUtils.w(TAG, "Frida inject process exited with code: $exitCode")
                    // If exit code is 0, it might have completed successfully
                    if (exitCode == 0) {
                        LogUtils.i(TAG, "Frida inject process completed successfully")
                        return true
                    }

                    // If we got here with a non-zero exit code, all methods failed
                    LogUtils.e(TAG, "All injection methods failed")
                    return false
                } catch (e: IllegalThreadStateException) {
                    // Process is still running, which is good
                    LogUtils.i(TAG, "Frida inject process is still running, injection likely successful")
                    return true
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error with alternative injection method", e)
                return false
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error injecting Frida script", e)
            return false
        }
    }

    /**
     * Check if a process with the given PID is still running
     * @param pid The process ID to check
     * @return true if the process is running, false otherwise
     */
    private fun isProcessRunning(pid: Int): Boolean {
        try {
            // Check if the /proc/[pid] directory exists
            val procDir = File("/proc/$pid")
            if (!procDir.exists()) {
                LogUtils.w(TAG, "Process $pid does not exist")
                return false
            }

            // Try to read the status file
            val statusFile = File(procDir, "status")
            if (!statusFile.exists()) {
                LogUtils.w(TAG, "Status file for process $pid does not exist")
                return false
            }

            // Try to read the cmdline file
            val cmdlineFile = File(procDir, "cmdline")
            if (!cmdlineFile.exists()) {
                LogUtils.w(TAG, "Cmdline file for process $pid does not exist")
                return false
            }

            // Try using the kill command with signal 0 (doesn't actually kill the process)
            val killProcess = Runtime.getRuntime().exec("kill -0 $pid")
            val exitValue = killProcess.waitFor()

            if (exitValue != 0) {
                LogUtils.w(TAG, "Process $pid is not running (kill -0 returned $exitValue)")
                return false
            }

            LogUtils.d(TAG, "Process $pid is running")
            return true
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking if process $pid is running", e)
            return false
        }
    }

    /**
     * Get the path to the Zygote monitor script
     * @return The absolute path to the Zygote monitor script
     */
    fun getZygoteScriptPath(): String {
        return File(context.filesDir, ZYGOTE_SCRIPT_FILENAME).absolutePath
    }
}
