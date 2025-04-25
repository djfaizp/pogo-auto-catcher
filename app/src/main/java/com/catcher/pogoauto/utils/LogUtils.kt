package com.catcher.pogoauto.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Utility class for centralized logging
 */
object LogUtils {
    private const val TAG = "PogoAutoCatcher"

    // LiveData to observe log updates
    val logLiveData = MutableLiveData<String>()

    // Buffer to store logs
    private val logBuffer = StringBuilder()

    // Network streaming settings
    private var isNetworkStreamingEnabled = false
    private var networkStreamingAddress = ""
    private var networkStreamingPort = 0
    private val networkExecutor = Executors.newSingleThreadExecutor()

    // File logging settings
    private var isFileLoggingEnabled = false
    private var logFile: File? = null
    private var logWriter: OutputStreamWriter? = null

    // Trace settings
    private var isTraceEnabled = true

    // Trace category settings
    private val traceCategoryEnabled = mutableMapOf(
        "ENCOUNTER" to true,
        "CAPTURE" to true,
        "MOVEMENT" to true,
        "ITEM" to true,
        "NETWORK" to true,
        "GYM" to true,
        "POKESTOP" to true,
        "FRIEND" to true,
        "COLLECTION" to true,
        "RAID" to true,
        "FRIDA" to true,
        "INIT" to true,
        "AR" to true,          // AR/Camera related activities
        "UNITY" to true,       // Unity warnings and errors
        "FIREBASE" to true,    // Firebase analytics
        "PHYSICS" to true,     // Physics-related operations
        "AUTH" to true,        // Authentication operations
        "FRIDA_HOOK" to true,  // Frida hooking process
        "DEBUG" to true,       // Debug messages
        "ERROR" to true,       // Error messages
        "PERF" to true,        // Performance metrics
        "ASSEMBLY" to true,    // Assembly loading
        "DIAGNOSTICS" to true  // Diagnostic information
    )

    /**
     * Log a debug message
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addToLogBuffer("D", tag, message)
    }

    /**
     * Log an info message
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addToLogBuffer("I", tag, message)
    }

    /**
     * Log a warning message
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addToLogBuffer("W", tag, message)
    }

    /**
     * Log an error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            addToLogBuffer("E", tag, "$message\n${throwable.stackTraceToString()}")
        } else {
            Log.e(tag, message)
            addToLogBuffer("E", tag, message)
        }
    }

    /**
     * Log a trace message from Frida
     */
    fun trace(message: String) {
        if (!isTraceEnabled) return

        // Check for different message formats
        if (message.startsWith("[TRACE]")) {
            // Standard trace message format
            val categoryRegex = "\\[TRACE\\]\\[[^\\]]*\\]\\[([^\\]]+)\\]".toRegex()
            val categoryMatch = categoryRegex.find(message)
            val category = categoryMatch?.groupValues?.get(1) ?: "FRIDA"

            // Check if this category is enabled
            if (!isTraceCategoryEnabled(category)) {
                return
            }

            // Pass it through
            addRawToLogBuffer(message)
        } else if (message.startsWith("[DEBUG]")) {
            // Debug message format
            val categoryRegex = "\\[DEBUG\\]\\[[^\\]]*\\]\\[FRIDA_HOOK\\]\\[([^\\]]+)\\]".toRegex()
            val categoryMatch = categoryRegex.find(message)
            val category = categoryMatch?.groupValues?.get(1) ?: "DEBUG"

            // Check if this category is enabled
            if (!isTraceCategoryEnabled(category) || !isTraceCategoryEnabled("DEBUG")) {
                return
            }

            // Pass it through
            addRawToLogBuffer(message)
        } else if (message.startsWith("[ERROR]")) {
            // Error message format
            val categoryRegex = "\\[ERROR\\]\\[[^\\]]*\\]\\[FRIDA_HOOK\\]\\[([^\\]]+)\\]".toRegex()
            val categoryMatch = categoryRegex.find(message)
            val category = categoryMatch?.groupValues?.get(1) ?: "ERROR"

            // Error messages are always logged
            addRawToLogBuffer(message)
        } else if (message.contains("[FRIDA_HOOK]")) {
            // Other Frida hook messages
            val categoryRegex = "\\[[^\\]]*\\]\\[FRIDA_HOOK\\]\\[([^\\]]+)\\]".toRegex()
            val categoryMatch = categoryRegex.find(message)
            val category = categoryMatch?.groupValues?.get(1) ?: "FRIDA_HOOK"

            // Check if this category is enabled
            if (!isTraceCategoryEnabled(category) || !isTraceCategoryEnabled("FRIDA_HOOK")) {
                return
            }

            // Pass it through
            addRawToLogBuffer(message)
        } else {
            // Format it as a standard trace message
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
            val formattedMessage = "[TRACE][$timestamp][FRIDA] $message"

            // Check if FRIDA category is enabled
            if (!isTraceCategoryEnabled("FRIDA")) {
                return
            }

            addRawToLogBuffer(formattedMessage)
        }
    }

    /**
     * Enable or disable trace logging
     */
    fun setTraceEnabled(enabled: Boolean) {
        isTraceEnabled = enabled
        i(TAG, "Trace logging ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Enable or disable a specific trace category
     */
    fun setTraceCategoryEnabled(category: String, enabled: Boolean) {
        if (traceCategoryEnabled.containsKey(category)) {
            traceCategoryEnabled[category] = enabled
            i(TAG, "Trace category $category ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Check if a trace category is enabled
     */
    fun isTraceCategoryEnabled(category: String): Boolean {
        return traceCategoryEnabled[category] ?: false
    }

    /**
     * Get all trace categories and their enabled status
     */
    fun getTraceCategories(): Map<String, Boolean> {
        return traceCategoryEnabled.toMap()
    }

    /**
     * Enable or disable all trace categories
     */
    fun setAllTraceCategoriesEnabled(enabled: Boolean) {
        traceCategoryEnabled.keys.forEach { category ->
            traceCategoryEnabled[category] = enabled
        }
        i(TAG, "All trace categories ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Add a raw message directly to the log buffer
     */
    private fun addRawToLogBuffer(message: String) {
        synchronized(logBuffer) {
            logBuffer.append(message).append("\n")

            // Limit buffer size to prevent memory issues
            if (logBuffer.length > 50000) {
                val newStartIndex = logBuffer.indexOf("\n", logBuffer.length - 40000)
                if (newStartIndex > 0) {
                    logBuffer.delete(0, newStartIndex + 1)
                }
            }

            logLiveData.postValue(logBuffer.toString())
        }

        // Write to file if enabled
        if (isFileLoggingEnabled && logWriter != null) {
            try {
                logWriter?.write(message + "\n")
                logWriter?.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write to log file", e)
                // Disable file logging if there's an error
                stopFileLogging()
            }
        }

        // Send over network if enabled
        if (isNetworkStreamingEnabled) {
            sendLogOverNetwork(message)
        }
    }

    /**
     * Add a message to the log buffer and update LiveData
     */
    private fun addToLogBuffer(level: String, tag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedMessage = "[$timestamp] [$level/$tag] $message"

        synchronized(logBuffer) {
            logBuffer.append(formattedMessage).append("\n")

            // Limit buffer size to prevent memory issues
            if (logBuffer.length > 50000) {
                val newStartIndex = logBuffer.indexOf("\n", logBuffer.length - 40000)
                if (newStartIndex > 0) {
                    logBuffer.delete(0, newStartIndex + 1)
                }
            }

            logLiveData.postValue(logBuffer.toString())
        }

        // Write to file if enabled
        if (isFileLoggingEnabled && logWriter != null) {
            try {
                logWriter?.write(formattedMessage + "\n")
                logWriter?.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write to log file", e)
                // Disable file logging if there's an error
                stopFileLogging()
            }
        }

        // Send over network if enabled
        if (isNetworkStreamingEnabled) {
            sendLogOverNetwork(formattedMessage)
        }
    }

    /**
     * Clear the log buffer
     */
    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            logLiveData.postValue("")
        }
    }

    /**
     * Get the current log buffer as a string
     */
    fun getLogs(): String {
        synchronized(logBuffer) {
            return logBuffer.toString()
        }
    }

    /**
     * Start logging to a file
     */
    fun startFileLogging(context: Context): File? {
        if (isFileLoggingEnabled) {
            return logFile
        }

        try {
            // Create logs directory if it doesn't exist
            val logsDir = File(context.getExternalFilesDir(null), "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            // Create a new log file with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(logsDir, "pogo_log_$timestamp.txt")

            // Open the file for writing
            logWriter = OutputStreamWriter(FileOutputStream(logFile, true))

            // Write header
            logWriter?.write("=== Pokémon GO Auto Catcher Log ===\n")
            logWriter?.write("Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
            logWriter?.flush()

            isFileLoggingEnabled = true
            i(TAG, "Started logging to file: ${logFile?.absolutePath}")

            return logFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start file logging", e)
            return null
        }
    }

    /**
     * Stop logging to a file
     */
    fun stopFileLogging() {
        if (!isFileLoggingEnabled) {
            return
        }

        try {
            logWriter?.write("\n=== End of Log ===\n")
            logWriter?.flush()
            logWriter?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing log file", e)
        } finally {
            logWriter = null
            isFileLoggingEnabled = false
            i(TAG, "Stopped logging to file")
        }
    }

    /**
     * Export logs to a file
     */
    fun exportLogs(context: Context): File? {
        try {
            // Create exports directory if it doesn't exist
            val exportsDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportsDir.exists()) {
                exportsDir.mkdirs()
            }

            // Create a new export file with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(exportsDir, "pogo_export_$timestamp.txt")

            // Write logs to the export file
            FileOutputStream(exportFile).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    writer.write("=== Pokémon GO Auto Catcher Log Export ===\n")
                    writer.write("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
                    writer.write(getLogs())
                    writer.write("\n=== End of Export ===\n")
                }
            }

            i(TAG, "Exported logs to file: ${exportFile.absolutePath}")
            return exportFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to export logs", e)
            return null
        }
    }

    /**
     * Start streaming logs over the network
     */
    fun startNetworkStreaming(ipAddress: String, port: Int): Boolean {
        if (isNetworkStreamingEnabled) {
            stopNetworkStreaming()
        }

        networkStreamingAddress = ipAddress
        networkStreamingPort = port
        isNetworkStreamingEnabled = true

        i(TAG, "Started streaming logs to $ipAddress:$port")

        // Send initial connection message
        sendLogOverNetwork("=== Connected to Pokémon GO Auto Catcher Log Stream ===")

        return true
    }

    /**
     * Stop streaming logs over the network
     */
    fun stopNetworkStreaming() {
        if (!isNetworkStreamingEnabled) {
            return
        }

        // Send disconnection message
        sendLogOverNetwork("=== Disconnected from Pokémon GO Auto Catcher Log Stream ===")

        isNetworkStreamingEnabled = false
        i(TAG, "Stopped streaming logs to $networkStreamingAddress:$networkStreamingPort")
    }

    /**
     * Send a log message over the network
     */
    private fun sendLogOverNetwork(message: String) {
        if (!isNetworkStreamingEnabled) {
            return
        }

        networkExecutor.execute {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                val data = message.toByteArray()
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(networkStreamingAddress),
                    networkStreamingPort
                )
                socket.send(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send log over network", e)
            } finally {
                socket?.close()
            }
        }
    }

    /**
     * Check if network streaming is enabled
     */
    fun isNetworkStreamingEnabled(): Boolean {
        return isNetworkStreamingEnabled
    }

    /**
     * Get the current network streaming address
     */
    fun getNetworkStreamingAddress(): String {
        return networkStreamingAddress
    }

    /**
     * Get the current network streaming port
     */
    fun getNetworkStreamingPort(): Int {
        return networkStreamingPort
    }
}
