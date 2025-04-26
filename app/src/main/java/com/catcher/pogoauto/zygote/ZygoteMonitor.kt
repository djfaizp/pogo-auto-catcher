package com.catcher.pogoauto.zygote

import android.content.Context
import com.catcher.pogoauto.utils.LogUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZygoteMonitor - Monitors Zygote process forking events to detect when Pokémon GO is launched
 * and automatically attach Frida to it.
 */
class ZygoteMonitor(private val context: Context) {

    companion object {
        private const val TAG = "ZygoteMonitor"

        // Pokémon GO package names
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

    // Monitoring state
    private val isMonitoring = AtomicBoolean(false)
    private var monitorThread: Thread? = null

    // Callback for when Pokémon GO is detected
    private var onPokemonGoDetectedCallback: ((String, Int) -> Unit)? = null

    /**
     * Start monitoring Zygote process forking events
     * @param callback Callback function that will be called when Pokémon GO is detected
     *                 Parameters: packageName, pid
     */
    fun startMonitoring(callback: (String, Int) -> Unit) {
        if (isMonitoring.get()) {
            LogUtils.i(TAG, "Already monitoring Zygote events")
            return
        }

        LogUtils.i(TAG, "Starting Zygote monitoring")
        isMonitoring.set(true)
        onPokemonGoDetectedCallback = callback

        monitorThread = Thread {
            try {
                monitorZygoteEvents()
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error in Zygote monitoring thread", e)
            } finally {
                isMonitoring.set(false)
                LogUtils.i(TAG, "Zygote monitoring stopped")
            }
        }

        monitorThread?.name = "ZygoteMonitorThread"
        monitorThread?.isDaemon = true
        monitorThread?.start()

        LogUtils.i(TAG, "Zygote monitoring started")
    }

    /**
     * Stop monitoring Zygote process forking events
     */
    fun stopMonitoring() {
        if (!isMonitoring.get()) {
            LogUtils.i(TAG, "Not monitoring Zygote events")
            return
        }

        LogUtils.i(TAG, "Stopping Zygote monitoring")
        isMonitoring.set(false)

        try {
            monitorThread?.interrupt()
            monitorThread?.join(1000)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping Zygote monitoring thread", e)
        }

        monitorThread = null
        onPokemonGoDetectedCallback = null
        LogUtils.i(TAG, "Zygote monitoring stopped")
    }

    /**
     * Monitor Zygote process forking events
     */
    private fun monitorZygoteEvents() {
        LogUtils.i(TAG, "Starting Zygote event monitoring")

        try {
            // Clear logcat first to avoid processing old events
            Runtime.getRuntime().exec("logcat -c").waitFor()

            // Start logcat process to capture Zygote events with expanded filters for Android 15
            // Include more log tags to catch process creation events
            val logcatCmd = "logcat -v threadtime" +
                    " Zygote:V ZygoteProcess:V ZygoteInit:V" +  // Zygote-related logs
                    " ActivityManager:V ActivityManagerService:V" +  // Activity manager logs
                    " ProcessRecord:V ProcessList:V" +  // Process management logs
                    " am_proc_start:V am_proc_bound:V" +  // Process start events
                    " AndroidRuntime:V" +  // Runtime logs that might show process creation
                    " *:S"  // Silence other logs

            LogUtils.i(TAG, "Using expanded logcat command for Zygote monitoring: $logcatCmd")
            val process = Runtime.getRuntime().exec(logcatCmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            // Also capture stderr to see any errors from the logcat process
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            Thread {
                try {
                    var errorLine: String? = null
                    while (isMonitoring.get() && errorReader.readLine().also { errorLine = it } != null) {
                        errorLine?.let { line ->
                            LogUtils.e(TAG, "Logcat error: $line")
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading logcat error stream", e)
                }
            }.start()

            LogUtils.i(TAG, "Logcat process started for Zygote monitoring")

            var line: String? = null
            while (isMonitoring.get() && reader.readLine().also { line = it } != null) {
                line?.let { logLine ->
                    // Enhanced pattern matching for process creation events
                    // Look for process forking events with multiple patterns for Android 15 compatibility
                    if ((logLine.contains("Zygote") && (logLine.contains("fork") || logLine.contains("child"))) ||
                        (logLine.contains("ZygoteProcess") && logLine.contains("start")) ||
                        (logLine.contains("ZygoteInit") && logLine.contains("start"))) {

                        LogUtils.d(TAG, "Zygote event detected: $logLine")

                        // Extract PID with multiple regex patterns to handle different log formats
                        val pidPatterns = listOf(
                            Regex("pid=(\\d+)"),
                            Regex("PID:\\s*(\\d+)"),
                            Regex("\\s(\\d+)\\s"),
                            Regex("Process (\\d+)")
                        )

                        var pid: Int? = null
                        for (pattern in pidPatterns) {
                            val match = pattern.find(logLine)
                            if (match != null) {
                                pid = match.groupValues[1].toIntOrNull()
                                if (pid != null) {
                                    LogUtils.d(TAG, "Found PID $pid using pattern: ${pattern.pattern}")
                                    break
                                }
                            }
                        }

                        if (pid != null) {
                            LogUtils.d(TAG, "Process created with PID: $pid")

                            // Check if this is Pokémon GO
                            checkIfPokemonGo(pid)
                        }
                    }
                    // Enhanced activity manager process start detection
                    else if ((logLine.contains("ActivityManager") || logLine.contains("ActivityManagerService")) &&
                             (logLine.contains("Start proc") || logLine.contains("Starting process") ||
                              logLine.contains("Process start") || logLine.contains("am_proc_start"))) {

                        LogUtils.d(TAG, "Activity Manager process start detected: $logLine")

                        // Check if this is Pokémon GO with more thorough package name detection
                        for (packageName in POKEMON_GO_PACKAGES) {
                            if (logLine.contains(packageName)) {
                                LogUtils.i(TAG, "Pokémon GO process start detected: $packageName")

                                // Extract PID with multiple regex patterns
                                val pidPatterns = listOf(
                                    Regex("pid=(\\d+)"),
                                    Regex("PID:\\s*(\\d+)"),
                                    Regex("\\s(\\d+)\\s"),
                                    Regex("Process (\\d+)")
                                )

                                var pid: Int? = null
                                for (pattern in pidPatterns) {
                                    val match = pattern.find(logLine)
                                    if (match != null) {
                                        pid = match.groupValues[1].toIntOrNull()
                                        if (pid != null) {
                                            break
                                        }
                                    }
                                }

                                if (pid != null) {
                                    LogUtils.i(TAG, "Pokémon GO started with PID: $pid")
                                    onPokemonGoDetectedCallback?.invoke(packageName, pid)
                                } else {
                                    // If we couldn't extract the PID, try to find it
                                    LogUtils.w(TAG, "Could not extract PID from log, trying to find it manually")
                                    findPokemonGoPid(packageName)
                                }

                                break
                            }
                        }
                    }

                    // Also check for any mention of Pokémon GO in logs
                    for (packageName in POKEMON_GO_PACKAGES) {
                        if (logLine.contains(packageName)) {
                            LogUtils.d(TAG, "Found mention of Pokémon GO in logs: $logLine")
                            // Don't trigger callback here, just log it for debugging
                        }
                    }
                }
            }

            LogUtils.i(TAG, "Exiting Zygote monitoring loop")
            reader.close()
            errorReader.close()
            process.destroy()

        } catch (e: Exception) {
            LogUtils.e(TAG, "Error monitoring Zygote events", e)

            // Try to restart monitoring after a short delay
            if (isMonitoring.get()) {
                LogUtils.i(TAG, "Attempting to restart Zygote monitoring after error")
                Thread.sleep(2000)
                monitorZygoteEvents() // Recursive call to restart monitoring
            }
        }
    }

    /**
     * Check if a process with the given PID is Pokémon GO
     * @param pid Process ID to check
     */
    private fun checkIfPokemonGo(pid: Int) {
        LogUtils.d(TAG, "Checking if process $pid is Pokémon GO")

        try {
            // First check if the process still exists
            val procDir = File("/proc/$pid")
            if (!procDir.exists()) {
                LogUtils.w(TAG, "Process $pid no longer exists")
                return
            }

            // Check the cmdline file to get the process name
            val cmdlineFile = File("/proc/$pid/cmdline")
            if (cmdlineFile.exists()) {
                try {
                    val processName = cmdlineFile.readText().trim { it <= ' ' }
                    LogUtils.d(TAG, "Process $pid cmdline: $processName")

                    for (packageName in POKEMON_GO_PACKAGES) {
                        if (processName.contains(packageName)) {
                            LogUtils.i(TAG, "Pokémon GO process detected from cmdline: $packageName (PID: $pid)")
                            onPokemonGoDetectedCallback?.invoke(packageName, pid)
                            return
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading cmdline for process $pid", e)
                    // Continue with other methods
                }
            }

            // Alternative method: check the process status
            val statusFile = File("/proc/$pid/status")
            if (statusFile.exists()) {
                try {
                    val statusContent = statusFile.readText()
                    val nameMatch = Regex("Name:\\s+(.+)").find(statusContent)
                    val processName = nameMatch?.groupValues?.get(1)?.trim()

                    if (processName != null) {
                        LogUtils.d(TAG, "Process $pid name from status: $processName")

                        // Check if this is a Zygote or system_server process
                        if (processName == "zygote" || processName == "zygote64" || processName == "system_server") {
                            LogUtils.d(TAG, "Process $pid is a system process, not Pokémon GO")
                            return
                        }

                        // Some Android versions use app_process as the name for all apps
                        if (processName == "app_process" || processName == "app_process64") {
                            LogUtils.d(TAG, "Process $pid is app_process, checking maps for package name")
                            // Continue to check maps
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading status for process $pid", e)
                    // Continue with other methods
                }
            }

            // Check maps file for more details
            val mapsFile = File("/proc/$pid/maps")
            if (mapsFile.exists()) {
                try {
                    // Read maps file line by line to avoid OutOfMemoryError
                    val mapsReader = mapsFile.bufferedReader()
                    var line: String? = null

                    while (mapsReader.readLine().also { line = it } != null) {
                        line?.let { mapsLine ->
                            for (packageName in POKEMON_GO_PACKAGES) {
                                if (mapsLine.contains(packageName)) {
                                    LogUtils.i(TAG, "Pokémon GO process detected from maps: $packageName (PID: $pid)")
                                    mapsReader.close()
                                    onPokemonGoDetectedCallback?.invoke(packageName, pid)
                                    return
                                }
                            }
                        }
                    }

                    mapsReader.close()
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading maps for process $pid", e)
                    // Continue with other methods
                }
            }

            // Try using ps command as a fallback
            try {
                val psProcess = Runtime.getRuntime().exec("ps -p $pid -o ARGS")
                val reader = BufferedReader(InputStreamReader(psProcess.inputStream))
                var line: String? = null

                // Skip header line
                reader.readLine()

                while (reader.readLine().also { line = it } != null) {
                    line?.let { psLine ->
                        for (packageName in POKEMON_GO_PACKAGES) {
                            if (psLine.contains(packageName)) {
                                LogUtils.i(TAG, "Pokémon GO process detected from ps command: $packageName (PID: $pid)")
                                reader.close()
                                onPokemonGoDetectedCallback?.invoke(packageName, pid)
                                return
                            }
                        }
                    }
                }

                reader.close()
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error using ps command for process $pid", e)
            }

            // Try using the Android package manager as a last resort
            try {
                // This command lists all running processes with their package names
                val pmProcess = Runtime.getRuntime().exec("dumpsys activity processes | grep $pid")
                val reader = BufferedReader(InputStreamReader(pmProcess.inputStream))
                var line: String? = null

                while (reader.readLine().also { line = it } != null) {
                    line?.let { pmLine ->
                        for (packageName in POKEMON_GO_PACKAGES) {
                            if (pmLine.contains(packageName)) {
                                LogUtils.i(TAG, "Pokémon GO process detected from dumpsys: $packageName (PID: $pid)")
                                reader.close()
                                onPokemonGoDetectedCallback?.invoke(packageName, pid)
                                return
                            }
                        }
                    }
                }

                reader.close()
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error using dumpsys for process $pid", e)
            }

            LogUtils.d(TAG, "Process $pid is not Pokémon GO")
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking if process $pid is Pokémon GO", e)
        }
    }

    /**
     * Find the PID of a running Pokémon GO process
     * @param packageName The package name to look for
     */
    private fun findPokemonGoPid(packageName: String) {
        LogUtils.i(TAG, "Searching for PID of Pokémon GO process: $packageName")

        // Try multiple methods to find the PID

        // Method 1: Using ps command
        try {
            LogUtils.d(TAG, "Trying to find PID using ps command")
            val psProcess = Runtime.getRuntime().exec("ps -A")
            val reader = BufferedReader(InputStreamReader(psProcess.inputStream))
            var line: String? = null

            while (reader.readLine().also { line = it } != null) {
                if (line?.contains(packageName) == true) {
                    // Extract PID (second column in ps output)
                    val parts = line?.trim()?.split("\\s+".toRegex())
                    if (parts != null && parts.size > 1) {
                        val pid = parts[1].toIntOrNull()
                        if (pid != null) {
                            LogUtils.i(TAG, "Found Pokémon GO process using ps: $packageName (PID: $pid)")
                            onPokemonGoDetectedCallback?.invoke(packageName, pid)
                            return
                        }
                    }
                }
            }

            reader.close()
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error finding PID using ps command", e)
        }

        // Method 2: Using pidof command
        try {
            LogUtils.d(TAG, "Trying to find PID using pidof command")
            val pidofProcess = Runtime.getRuntime().exec("pidof $packageName")
            val reader = BufferedReader(InputStreamReader(pidofProcess.inputStream))
            val output = reader.readText().trim()

            if (output.isNotEmpty()) {
                val pid = output.toIntOrNull()
                if (pid != null) {
                    LogUtils.i(TAG, "Found Pokémon GO process using pidof: $packageName (PID: $pid)")
                    onPokemonGoDetectedCallback?.invoke(packageName, pid)
                    return
                }
            }

            reader.close()
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error finding PID using pidof command", e)
        }

        // Method 3: Using dumpsys activity processes
        try {
            LogUtils.d(TAG, "Trying to find PID using dumpsys activity processes")
            val dumpsysProcess = Runtime.getRuntime().exec("dumpsys activity processes | grep $packageName")
            val reader = BufferedReader(InputStreamReader(dumpsysProcess.inputStream))
            var line: String? = null

            while (reader.readLine().also { line = it } != null) {
                if (line?.contains(packageName) == true) {
                    // Try to extract PID using regex
                    val pidMatch = Regex("pid=(\\d+)").find(line ?: "")
                    if (pidMatch != null) {
                        val pid = pidMatch.groupValues[1].toIntOrNull()
                        if (pid != null) {
                            LogUtils.i(TAG, "Found Pokémon GO process using dumpsys: $packageName (PID: $pid)")
                            onPokemonGoDetectedCallback?.invoke(packageName, pid)
                            return
                        }
                    }
                }
            }

            reader.close()
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error finding PID using dumpsys command", e)
        }

        // Method 4: Scan /proc directory
        try {
            LogUtils.d(TAG, "Trying to find PID by scanning /proc directory")
            val procDir = File("/proc")
            val procFiles = procDir.listFiles { file ->
                file.isDirectory && file.name.matches(Regex("\\d+"))
            }

            if (procFiles != null) {
                for (procFile in procFiles) {
                    val pid = procFile.name.toIntOrNull() ?: continue

                    // Check cmdline file
                    val cmdlineFile = File(procFile, "cmdline")
                    if (cmdlineFile.exists()) {
                        try {
                            val cmdline = cmdlineFile.readText()
                            if (cmdline.contains(packageName)) {
                                LogUtils.i(TAG, "Found Pokémon GO process by scanning /proc: $packageName (PID: $pid)")
                                onPokemonGoDetectedCallback?.invoke(packageName, pid)
                                return
                            }
                        } catch (e: Exception) {
                            // Ignore errors for individual processes
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error scanning /proc directory", e)
        }

        LogUtils.w(TAG, "Could not find PID for Pokémon GO process: $packageName")
    }

    /**
     * Check if Pokémon GO is currently running
     * @return Pair of (packageName, pid) if running, null otherwise
     */
    fun isPokemonGoRunning(): Pair<String, Int>? {
        for (packageName in POKEMON_GO_PACKAGES) {
            try {
                val psProcess = Runtime.getRuntime().exec("ps | grep $packageName")
                val reader = BufferedReader(InputStreamReader(psProcess.inputStream))
                var line: String? = null

                while (reader.readLine().also { line = it } != null) {
                    if (line?.contains(packageName) == true) {
                        // Extract PID (second column in ps output)
                        val parts = line?.trim()?.split("\\s+".toRegex())
                        if (parts != null && parts.size > 1) {
                            val pid = parts[1].toIntOrNull()
                            if (pid != null) {
                                LogUtils.i(TAG, "Pokémon GO is running: $packageName (PID: $pid)")
                                return Pair(packageName, pid)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error checking if Pokémon GO is running", e)
            }
        }

        LogUtils.i(TAG, "Pokémon GO is not running")
        return null
    }
}
