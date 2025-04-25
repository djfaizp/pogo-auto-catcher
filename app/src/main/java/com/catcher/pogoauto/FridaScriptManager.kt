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

/**
 * Manager class for handling Frida scripts and interactions with Pokémon GO
 */
class FridaScriptManager(private val context: Context) {

    companion object {
        private const val TAG = "FridaScriptManager"
        private const val SCRIPT_FILENAME = "pokemon-go-hook.js"
        private const val CONFIG_FILENAME = "frida-gadget-config.json"

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

    // Script configuration options
    private var perfectThrowEnabled = true
    private var perfectThrowCurveball = true
    private var perfectThrowType = "EXCELLENT" // EXCELLENT, GREAT, or NICE
    private var autoWalkEnabled = false
    private var autoWalkSpeed = 1.0f
    private var autoCatchEnabled = false
    private var autoCatchDelay = 500
    private var autoCatchRetryOnEscape = true
    private var autoCatchMaxRetries = 3
    private var injectionDelay = 0 // Delay before launching Pokemon GO in milliseconds
    private var traceCategoriesEnabled = mutableMapOf<String, Boolean>()

    // Frida console output reader thread
    private var fridaLogcatThread: Thread? = null
    private var isCapturingLogs = false

    /**
     * Extracts the Frida script and configuration from assets to the app's files directory
     * @return true if successful, false otherwise
     */
    fun extractScriptFromAssets(): Boolean {
        try {
            // Extract the TypeScript file
            val tsScript = context.assets.open("pokemon-go-hook.ts").bufferedReader().use { it.readText() }

            // Extract the configuration file
            val configJson = context.assets.open("frida-gadget-config.json").bufferedReader().use { it.readText() }

            // Convert TypeScript to JavaScript (simple conversion for this example)
            // In a real app, you would use a proper TypeScript compiler
            val jsScript = convertTypeScriptToJavaScript(tsScript)

            // Write the script to the app's files directory
            val scriptFile = File(context.filesDir, SCRIPT_FILENAME)
            FileOutputStream(scriptFile).use { it.write(jsScript.toByteArray()) }

            // Write the configuration to the app's files directory
            val configFile = File(context.filesDir, CONFIG_FILENAME)
            FileOutputStream(configFile).use { it.write(configJson.toByteArray()) }

            LogUtils.i(TAG, "Script extracted to ${scriptFile.absolutePath}")
            LogUtils.i(TAG, "Config extracted to ${configFile.absolutePath}")
            return true
        } catch (e: IOException) {
            LogUtils.e(TAG, "Failed to extract script or config", e)
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
            modifiedScript = modifiedScript.replace(
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
        maxRetries: $autoCatchMaxRetries         // Maximum number of retry attempts
    };"""
            )

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
     * Sets the delay before launching Pokemon GO
     * @param delay the delay in milliseconds
     */
    fun setInjectionDelay(delay: Int) {
        injectionDelay = delay
        LogUtils.i(TAG, "Injection delay set to $delay ms")
    }

    /**
     * Gets the path to the extracted script
     * @return the absolute path to the script
     */
    fun getScriptPath(): String {
        return File(context.filesDir, SCRIPT_FILENAME).absolutePath
    }

    /**
     * Gets the path to the extracted configuration
     * @return the absolute path to the configuration
     */
    fun getConfigPath(): String {
        return File(context.filesDir, CONFIG_FILENAME).absolutePath
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
        // Apply injection delay if set
        if (injectionDelay > 0) {
            LogUtils.i(TAG, "Applying injection delay of $injectionDelay ms before launching Pokémon GO")
            try {
                Thread.sleep(injectionDelay.toLong())
            } catch (e: InterruptedException) {
                LogUtils.e(TAG, "Injection delay was interrupted", e)
            }
        }

        // Try all possible package names
        for (packageName in POKEMON_GO_PACKAGES) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                // Add flags to ensure a clean start
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                LogUtils.i(TAG, "Launched Pokémon GO with package name: $packageName")
                return true
            }
        }

        // If we couldn't find it by package name, try to find it by app name
        val packageManager = context.packageManager
        val installedPackages = packageManager.getInstalledPackages(0)

        for (packageInfo in installedPackages) {
            val applicationInfo = packageInfo.applicationInfo
            if (applicationInfo != null) {
                val appName = applicationInfo.loadLabel(packageManager).toString()
                if (appName.contains("Pokémon GO", ignoreCase = true) ||
                    appName.contains("Pokemon GO", ignoreCase = true)) {
                    val intent = packageManager.getLaunchIntentForPackage(packageInfo.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                        LogUtils.i(TAG, "Launched Pokémon GO by app name: $appName, package: ${packageInfo.packageName}")
                        return true
                    }
                }
            }
        }

        LogUtils.e(TAG, "Failed to get launch intent for Pokémon GO")
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
            return
        }

        isCapturingLogs = true
        LogUtils.i(TAG, "Starting Frida log capture")

        fridaLogcatThread = Thread {
            try {
                // Clear logcat first
                Runtime.getRuntime().exec("logcat -c").waitFor()

                // Start logcat process to capture Frida logs
                val process = Runtime.getRuntime().exec("logcat -v threadtime frida:V frida-*:V *:S")
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String? = null
                while (isCapturingLogs && reader.readLine().also { line = it } != null) {
                    line?.let { logLine ->
                        // Filter for Frida-related logs
                        if (logLine.contains("frida", ignoreCase = true) ||
                            logLine.contains("TRACE", ignoreCase = true)) {

                            // Extract the actual message
                            val message = extractMessageFromLogcat(logLine)
                            if (message.isNotEmpty()) {
                                // Send to LogUtils as a trace message
                                LogUtils.trace(message)
                            }
                        }
                    }
                }

                reader.close()
                process.destroy()

            } catch (e: Exception) {
                LogUtils.e(TAG, "Error capturing Frida logs", e)
            } finally {
                isCapturingLogs = false
            }
        }

        fridaLogcatThread?.start()
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
     */
    private fun extractMessageFromLogcat(logLine: String): String {
        // Try to extract the message part from the logcat line
        // Logcat format: date time PID TID level tag: message
        val messageParts = logLine.split(": ", limit = 2)
        if (messageParts.size > 1) {
            return messageParts[1].trim()
        }

        // If we can't parse it, just return the whole line
        return logLine
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
        } else {
            // If launch failed, stop capturing logs
            stopCapturingFridaLogs()
            LogUtils.e(TAG, "Failed to launch Pokémon GO with tracing")
        }

        return success
    }
}
