package com.catcher.pogoauto.utils

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Utility class for handling Frida server and related functionality
 */
object LibraryUtils {
    private const val TAG = "LibraryUtils"
    private const val FRIDA_SERVER_FILENAME = "frida-server"
    private const val FRIDA_SERVER_PORT = 27042
    private const val FRIDA_SERVER_ASSET = "frida-server"
    private const val FRIDA_SERVER_COMPRESSED_ASSET = "frida-server.zip"

    /**
     * Get the Frida server path in the app's files directory
     * @param context the application context
     * @return the path to the Frida server
     */
    fun getFridaServerPath(context: Context? = null): String {
        return if (context != null) {
            File(context.filesDir, FRIDA_SERVER_FILENAME).absolutePath
        } else {
            // Return a placeholder if context is not available
            "app_files_dir/$FRIDA_SERVER_FILENAME"
        }
    }

    /**
     * Extract Frida server from assets to the app's private storage
     * @param context the application context
     * @return the path to the extracted Frida server or null if extraction failed
     */
    fun extractFridaServerFromAssets(context: Context): String? {
        try {
            val assetManager = context.assets
            val outputFile = File(context.filesDir, FRIDA_SERVER_FILENAME)

            LogUtils.i(TAG, "Extracting Frida server from assets to ${outputFile.absolutePath}")

            // Check if we have a compressed or uncompressed version in assets
            val assetList = assetManager.list("")
            val hasCompressedServer = assetList?.contains(FRIDA_SERVER_COMPRESSED_ASSET) == true
            val hasUncompressedServer = assetList?.contains(FRIDA_SERVER_ASSET) == true

            if (!hasCompressedServer && !hasUncompressedServer) {
                LogUtils.e(TAG, "Frida server asset not found in assets")
                return null
            }

            // Extract the server
            if (hasCompressedServer) {
                // Extract from compressed asset
                LogUtils.i(TAG, "Extracting Frida server from compressed asset")
                val zipStream = ZipInputStream(assetManager.open(FRIDA_SERVER_COMPRESSED_ASSET))
                var entry = zipStream.nextEntry

                while (entry != null) {
                    if (!entry.isDirectory && entry.name.contains("frida-server", ignoreCase = true)) {
                        LogUtils.i(TAG, "Found Frida server in zip: ${entry.name}")
                        FileOutputStream(outputFile).use { output ->
                            zipStream.copyTo(output)
                        }
                        break
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }

                zipStream.close()
            } else {
                // Extract from uncompressed asset
                LogUtils.i(TAG, "Extracting Frida server from uncompressed asset")
                assetManager.open(FRIDA_SERVER_ASSET).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Verify extraction
            if (!outputFile.exists() || outputFile.length() == 0L) {
                LogUtils.e(TAG, "Failed to extract Frida server: file does not exist or is empty")
                return null
            }

            LogUtils.i(TAG, "Frida server extracted successfully: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")

            // Set executable permission
            try {
                val execResult = Runtime.getRuntime().exec("chmod 755 ${outputFile.absolutePath}")
                val exitValue = execResult.waitFor()
                LogUtils.i(TAG, "Set executable permission on Frida server: $exitValue")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Failed to set executable permission on Frida server", e)
                // Continue anyway, we'll try to set permissions when copying to /data/local/tmp
            }

            return outputFile.absolutePath
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error extracting Frida server from assets", e)
            return null
        }
    }

    /**
     * Set executable permissions on the Frida server in the app's files directory
     * @param context the application context
     * @param serverPath the path to the extracted Frida server
     * @return true if successful, false otherwise
     */
    fun setFridaServerPermissions(context: Context, serverPath: String): Boolean {
        try {
            LogUtils.i(TAG, "Setting executable permissions on Frida server at $serverPath")

            // First try with root
            try {
                val suProcess = Runtime.getRuntime().exec("su")
                val outputStream = java.io.DataOutputStream(suProcess.outputStream)

                // Set permissions
                outputStream.writeBytes("chmod 755 $serverPath\n")
                // Exit su
                outputStream.writeBytes("exit\n")
                outputStream.flush()

                val exitValue = suProcess.waitFor()
                if (exitValue == 0) {
                    LogUtils.i(TAG, "Set executable permissions on Frida server with root")
                    return true
                }
            } catch (e: Exception) {
                LogUtils.w(TAG, "Failed to set permissions with root: ${e.message}")
            }

            // Try without root (might work on some devices)
            try {
                val chmodProcess = Runtime.getRuntime().exec("chmod 755 $serverPath")
                val exitValue = chmodProcess.waitFor()

                if (exitValue == 0) {
                    LogUtils.i(TAG, "Set executable permissions on Frida server without root")
                    return true
                }
            } catch (e: Exception) {
                LogUtils.w(TAG, "Failed to set permissions without root: ${e.message}")
            }

            LogUtils.e(TAG, "Failed to set executable permissions on Frida server")
            return false
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error setting permissions on Frida server", e)
            return false
        }
    }

    /**
     * For backward compatibility - redirects to setFridaServerPermissions
     * @param context the application context
     * @param sourcePath the path to the extracted Frida server
     * @return true if successful, false otherwise
     */
    fun copyFridaServerToDataLocalTmp(context: Context, sourcePath: String): Boolean {
        LogUtils.i(TAG, "Using app directory for Frida server instead of /data/local/tmp")
        return setFridaServerPermissions(context, sourcePath)
    }

    /**
     * Check if Frida server is installed on the device
     * @param context the application context (optional)
     * @return true if Frida server is found, false otherwise
     */
    fun isFridaServerInstalled(context: Context? = null): Boolean {
        try {
            // First check in app's files directory if context is provided
            if (context != null) {
                val serverPath = getFridaServerPath(context)
                val serverFile = File(serverPath)

                if (serverFile.exists() && serverFile.length() > 0) {
                    LogUtils.i(TAG, "Frida server found in app's files directory at $serverPath")
                    return true
                }
            }

            // Try to find frida-server in PATH
            val whichProcess = Runtime.getRuntime().exec("which frida-server")
            val whichExitCode = whichProcess.waitFor()

            if (whichExitCode == 0) {
                val reader = BufferedReader(InputStreamReader(whichProcess.inputStream))
                val path = reader.readLine()
                LogUtils.i(TAG, "Frida server found in PATH at $path")
                return true
            }

            LogUtils.w(TAG, "Frida server not found on device")
            return false
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking for Frida server", e)
            return false
        }
    }

    /**
     * Check if Frida server is running on the device
     * @return true if Frida server is running, false otherwise
     */
    fun isFridaServerRunning(): Boolean {
        try {
            LogUtils.d(TAG, "Checking if Frida server is running")

            // First try using netstat to check if the Frida port is open
            try {
                val netstatCmd = "netstat -tuln | grep :$FRIDA_SERVER_PORT"
                LogUtils.d(TAG, "Running command: $netstatCmd")
                val netstatProcess = Runtime.getRuntime().exec(netstatCmd)
                val netstatReader = BufferedReader(InputStreamReader(netstatProcess.inputStream))
                val netstatOutput = netstatReader.readText()

                if (netstatOutput.isNotEmpty()) {
                    LogUtils.i(TAG, "Frida server port $FRIDA_SERVER_PORT is open: $netstatOutput")
                    return true
                } else {
                    LogUtils.d(TAG, "Frida server port $FRIDA_SERVER_PORT is not open")
                }
            } catch (e: Exception) {
                LogUtils.w(TAG, "Error checking Frida server port with netstat: ${e.message}")
            }

            // Use ps to check if frida-server is running
            // Check for both "frida-server" and the filename in case it's renamed
            val psCmd = "ps -A | grep -e frida-server -e $FRIDA_SERVER_FILENAME"
            LogUtils.d(TAG, "Running command: $psCmd")
            val process = Runtime.getRuntime().exec(psCmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            var isRunning = false

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
                // Make sure we're not matching the grep command itself
                if ((line?.contains("frida-server") == true || line?.contains(FRIDA_SERVER_FILENAME) == true)
                    && line?.contains("grep") != true) {
                    isRunning = true
                }
            }

            if (output.isNotEmpty()) {
                LogUtils.d(TAG, "PS output: $output")
            }

            // Try using frida-ps as a final check
            try {
                val fridaPsCmd = "frida-ps"
                LogUtils.d(TAG, "Running command: $fridaPsCmd")
                val fridaPsProcess = Runtime.getRuntime().exec(fridaPsCmd)
                val fridaPsExitCode = fridaPsProcess.waitFor()

                if (fridaPsExitCode == 0) {
                    LogUtils.i(TAG, "frida-ps command succeeded, Frida server is running")
                    return true
                } else {
                    LogUtils.d(TAG, "frida-ps command failed with exit code $fridaPsExitCode")
                }
            } catch (e: Exception) {
                LogUtils.w(TAG, "Error running frida-ps: ${e.message}")
            }

            LogUtils.i(TAG, "Frida server running status (based on ps): $isRunning")
            return isRunning
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking if Frida server is running", e)
            return false
        }
    }

    /**
     * Start the Frida server on the device
     * @param context the application context
     * @return true if successfully started, false otherwise
     */
    fun startFridaServer(context: Context): Boolean {
        if (isFridaServerRunning()) {
            LogUtils.i(TAG, "Frida server is already running")
            return true
        }

        // Check if Frida server is installed, if not, extract and install it
        if (!isFridaServerInstalled(context)) {
            LogUtils.i(TAG, "Frida server not installed, extracting from assets")

            // Extract Frida server from assets
            val extractedPath = extractFridaServerFromAssets(context)
            if (extractedPath == null) {
                LogUtils.e(TAG, "Failed to extract Frida server from assets")
                return false
            }

            // Set executable permissions
            if (!setFridaServerPermissions(context, extractedPath)) {
                LogUtils.e(TAG, "Failed to set executable permissions on Frida server")
                return false
            }
        }

        try {
            val serverPath = getFridaServerPath(context)
            LogUtils.i(TAG, "Attempting to start Frida server from $serverPath")

            // Try with su first (for rooted devices)
            try {
                LogUtils.d(TAG, "Attempting to start Frida server with root: su -c '$serverPath -l 0.0.0.0:$FRIDA_SERVER_PORT &'")
                val suProcess = Runtime.getRuntime().exec("su -c '$serverPath -l 0.0.0.0:$FRIDA_SERVER_PORT &'")

                // Capture error output
                val errorReader = BufferedReader(InputStreamReader(suProcess.errorStream))
                val errorOutput = StringBuilder()
                var errorLine: String?
                while (errorReader.readLine().also { errorLine = it } != null) {
                    errorOutput.append(errorLine).append("\n")
                }

                val suExitCode = suProcess.waitFor()
                LogUtils.d(TAG, "su process exit code: $suExitCode")

                if (errorOutput.isNotEmpty()) {
                    LogUtils.e(TAG, "Error output from su command: $errorOutput")
                }

                if (suExitCode == 0) {
                    LogUtils.i(TAG, "Started Frida server with root permissions")
                    // Wait a bit for the server to start
                    Thread.sleep(1000)
                    val running = isFridaServerRunning()
                    LogUtils.i(TAG, "Frida server running check after root start: $running")
                    return running
                } else {
                    LogUtils.e(TAG, "Failed to start Frida server with root, exit code: $suExitCode")
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Exception starting Frida server with root", e)
            }

            // Try without su (might work on some devices)
            try {
                LogUtils.d(TAG, "Attempting to start Frida server without root: $serverPath -l 0.0.0.0:$FRIDA_SERVER_PORT &")
                val process = Runtime.getRuntime().exec("$serverPath -l 0.0.0.0:$FRIDA_SERVER_PORT &")

                // Capture error output
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errorOutput = StringBuilder()
                var errorLine: String?
                while (errorReader.readLine().also { errorLine = it } != null) {
                    errorOutput.append(errorLine).append("\n")
                }

                if (errorOutput.isNotEmpty()) {
                    LogUtils.e(TAG, "Error output from direct command: $errorOutput")
                }

                // Wait a bit for the server to start
                Thread.sleep(1000)

                val running = isFridaServerRunning()
                LogUtils.i(TAG, "Frida server running check after direct start: $running")
                if (running) {
                    LogUtils.i(TAG, "Started Frida server without root")
                    return true
                } else {
                    LogUtils.e(TAG, "Failed to start Frida server without root, server not running after attempt")
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Exception starting Frida server without root", e)
            }

            LogUtils.e(TAG, "Failed to start Frida server")
            return false
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error starting Frida server", e)
            return false
        }
    }

    /**
     * Stop the Frida server on the device
     * @return true if successfully stopped, false otherwise
     */
    fun stopFridaServer(): Boolean {
        if (!isFridaServerRunning()) {
            LogUtils.i(TAG, "Frida server is not running")
            return true
        }

        try {
            LogUtils.i(TAG, "Attempting to stop Frida server")

            // Try with su first (for rooted devices)
            try {
                // Try to kill both frida-server and our renamed version
                val suProcess = Runtime.getRuntime().exec("su -c 'pkill -f \"frida-server|$FRIDA_SERVER_FILENAME\"'")
                val suExitCode = suProcess.waitFor()

                if (suExitCode == 0) {
                    LogUtils.i(TAG, "Stopped Frida server with root permissions")
                    // Wait a bit for the server to stop
                    Thread.sleep(1000)
                    return !isFridaServerRunning()
                }
            } catch (e: Exception) {
                LogUtils.w(TAG, "Failed to stop Frida server with root: ${e.message}")
            }

            // Try without su
            try {
                // Try to kill both frida-server and our renamed version
                val process = Runtime.getRuntime().exec("pkill -f \"frida-server|$FRIDA_SERVER_FILENAME\"")
                // Wait a bit for the server to stop
                Thread.sleep(1000)

                if (!isFridaServerRunning()) {
                    LogUtils.i(TAG, "Stopped Frida server without root")
                    return true
                }
            } catch (e: Exception) {
                LogUtils.w(TAG, "Failed to stop Frida server without root: ${e.message}")
            }

            LogUtils.e(TAG, "Failed to stop Frida server")
            return false
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping Frida server", e)
            return false
        }
    }

    /**
     * Extract a Frida script from assets to the app's files directory
     * @param context the application context
     * @param assetPath the path to the script in assets
     * @param outputFilename the name of the output file
     * @return true if successful, false otherwise
     */
    fun extractScriptFromAssets(context: Context, assetPath: String, outputFilename: String): Boolean {
        try {
            val assetManager = context.assets

            LogUtils.i(TAG, "Extracting script from assets: $assetPath to $outputFilename")

            // Create output file
            val outputFile = File(context.filesDir, outputFilename)

            // Copy from assets to the output file
            assetManager.open(assetPath).use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            return outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to extract script from assets", e)
            return false
        }
    }

    /**
     * Get the Frida server port
     * @return the port number
     */
    fun getFridaServerPort(): Int {
        return FRIDA_SERVER_PORT
    }

    /**
     * Get the Frida server path (deprecated version)
     * @return the path to the Frida server
     * @deprecated Use getFridaServerPath(context) instead
     */
    @Deprecated("Use getFridaServerPath(context) instead", ReplaceWith("getFridaServerPath(context)"))
    fun getFridaServerPath(): String {
        return getFridaServerPath(null)
    }
}
