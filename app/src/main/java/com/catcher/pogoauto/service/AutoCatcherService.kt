package com.catcher.pogoauto.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.catcher.pogoauto.FridaScriptManager
import com.catcher.pogoauto.MainActivity
import com.catcher.pogoauto.R
import com.catcher.pogoauto.utils.LibraryUtils
import com.catcher.pogoauto.utils.LogUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service for maintaining Pokémon GO Auto Catcher functionality
 * when the app is in the background.
 */
class AutoCatcherService : Service() {

    companion object {
        private const val TAG = "AutoCatcherService"

        // Notification constants
        const val NOTIFICATION_CHANNEL_ID = "pogoauto_service_channel"
        const val NOTIFICATION_ID = 1001

        // Intent actions
        const val ACTION_START_SERVICE = "com.catcher.pogoauto.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.catcher.pogoauto.action.STOP_SERVICE"
        const val ACTION_STOP_POKEMON_GO = "com.catcher.pogoauto.action.STOP_POKEMON_GO"

        // Service status
        const val STATUS_IDLE = "Idle"
        const val STATUS_RUNNING = "Running"
        const val STATUS_STOPPING = "Stopping"
        const val STATUS_ERROR = "Error"
    }

    // Binder for client communication
    private val binder = LocalBinder()

    // Service state
    private var serviceStatus = STATUS_IDLE
    private val isRunning = AtomicBoolean(false)

    // Wake lock to keep CPU running
    private var wakeLock: PowerManager.WakeLock? = null

    // Frida script manager
    private lateinit var fridaScriptManager: FridaScriptManager

    // Log capture thread
    private var logCaptureThread: Thread? = null
    private val isCapturingLogs = AtomicBoolean(false)

    // Monitoring thread
    private var monitoringThread: Thread? = null
    private val isMonitoring = AtomicBoolean(false)

    /**
     * Binder class for client communication
     */
    inner class LocalBinder : Binder() {
        fun getService(): AutoCatcherService = this@AutoCatcherService
    }

    override fun onCreate() {
        super.onCreate()
        LogUtils.i(TAG, "Service onCreate")

        // Initialize Frida script manager
        fridaScriptManager = FridaScriptManager(this)

        // Create notification channel for Android 8.0+
        createNotificationChannel()

        // Check if Frida server is installed and running
        if (!LibraryUtils.isFridaServerInstalled()) {
            LogUtils.i(TAG, "Frida server not installed, extracting from assets")
            val extractedPath = LibraryUtils.extractFridaServerFromAssets(this)
            if (extractedPath != null) {
                LogUtils.i(TAG, "Successfully extracted Frida server from assets: $extractedPath")
            } else {
                LogUtils.e(TAG, "Failed to extract Frida server from assets")
            }
        } else {
            LogUtils.i(TAG, "Frida server is already installed")
        }

        // Extract Frida scripts
        if (fridaScriptManager.extractScriptFromAssets()) {
            LogUtils.i(TAG, "Frida scripts extracted successfully")
        } else {
            LogUtils.e(TAG, "Failed to extract Frida scripts")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.i(TAG, "Service onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                // Direct approach to start foreground service with DATA_SYNC type only
                try {
                    // Acquire wake lock to keep CPU running
                    acquireWakeLock()

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                            // Use both DATA_SYNC and CONNECTED_DEVICE types for Android 14+
                            LogUtils.i(TAG, "Starting foreground service with DATA_SYNC and CONNECTED_DEVICE types on Android 14+")
                            startForeground(NOTIFICATION_ID, createNotification(),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                        } else {
                            LogUtils.i(TAG, "Starting foreground service with default type")
                            startForeground(NOTIFICATION_ID, createNotification())
                        }
                    } catch (e: SecurityException) {
                        LogUtils.e(TAG, "Error starting foreground service", e)
                        // Fall back to DATA_SYNC type only
                        try {
                            LogUtils.i(TAG, "Falling back to starting foreground service with DATA_SYNC type only")
                            startForeground(NOTIFICATION_ID, createNotification(),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                        } catch (e2: Exception) {
                            LogUtils.e(TAG, "Error starting foreground service with DATA_SYNC type", e2)
                            // Last resort - try without a type
                            try {
                                LogUtils.i(TAG, "Falling back to starting foreground service without a type")
                                startForeground(NOTIFICATION_ID, createNotification())
                            } catch (e3: Exception) {
                                LogUtils.e(TAG, "Error starting foreground service without a type", e3)
                                // Last resort - try to keep the service running without foreground
                                Toast.makeText(applicationContext,
                                    "Failed to start foreground service. App may be killed in background.",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    // Set service status
                    serviceStatus = STATUS_RUNNING
                    isRunning.set(true)

                    // Start log capture and monitoring
                    startLogCapture()
                    startMonitoring()

                    // Start Zygote monitoring
                    if (fridaScriptManager.startZygoteMonitoring()) {
                        LogUtils.i(TAG, "Zygote monitoring started successfully")
                    } else {
                        LogUtils.e(TAG, "Failed to start Zygote monitoring")
                    }

                    LogUtils.i(TAG, "Foreground service started successfully")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error starting foreground service: ${e.message}", e)
                    stopSelf()
                }
            }
            ACTION_STOP_SERVICE -> stopService()
            ACTION_STOP_POKEMON_GO -> stopPokemonGo()
        }

        // If service is killed, restart it
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        LogUtils.i(TAG, "Service onDestroy")

        // Release wake lock if held
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        // Stop log capture
        stopLogCapture()

        // Stop monitoring
        stopMonitoring()

        super.onDestroy()
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.service_notification_channel_name)
            val descriptionText = getString(R.string.service_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            LogUtils.i(TAG, "Notification channel created")
        }
    }

    // The startForegroundService method has been removed and its functionality
    // has been moved directly into onStartCommand and launchPokemonGo methods
    // to ensure consistent foreground service type usage.

    /**
     * Stop the service
     */
    private fun stopService() {
        LogUtils.i(TAG, "Stopping service")

        // Set service status
        serviceStatus = STATUS_STOPPING
        isRunning.set(false)

        // Release wake lock if held
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        // Stop log capture
        stopLogCapture()

        // Stop monitoring
        stopMonitoring()

        // Stop Zygote monitoring
        fridaScriptManager.stopZygoteMonitoring()

        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // Stop the service
        stopSelf()

        LogUtils.i(TAG, "Service stopped")
    }

    /**
     * Stop Pokémon GO
     */
    private fun stopPokemonGo() {
        LogUtils.i(TAG, "Stopping Pokémon GO from service")

        // Run in a background thread
        Thread {
            val success = fridaScriptManager.stopPokemonGo()
            LogUtils.i(TAG, "Stop Pokémon GO result: $success")

            // Update notification
            updateNotification()
        }.start()
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(): Notification {
        // Create intent for opening the app
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create stop service intent
        val stopServiceIntent = Intent(this, AutoCatcherService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopServicePendingIntent = PendingIntent.getService(
            this, 1, stopServiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create stop Pokémon GO intent
        val stopPokemonGoIntent = Intent(this, AutoCatcherService::class.java).apply {
            action = ACTION_STOP_POKEMON_GO
        }
        val stopPokemonGoPendingIntent = PendingIntent.getService(
            this, 2, stopPokemonGoIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText("Service status: $serviceStatus")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.service_action_stop), stopServicePendingIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.service_action_stop_pokemon), stopPokemonGoPendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Update the notification with current status
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    /**
     * Acquire wake lock to keep CPU running
     */
    private fun acquireWakeLock() {
        // Check if we have the WAKE_LOCK permission
        val hasWakeLockPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WAKE_LOCK
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasWakeLockPermission) {
            LogUtils.e(TAG, "WAKE_LOCK permission not granted. Cannot acquire wake lock.")
            // Continue without wake lock
            return
        }

        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PogoAutoCatcher:WakeLock"
                )
                wakeLock?.setReferenceCounted(false)
            }

            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10*60*1000L /*10 minutes*/)
                LogUtils.i(TAG, "Wake lock acquired")
            }
        } catch (e: SecurityException) {
            LogUtils.e(TAG, "Security exception when acquiring wake lock: ${e.message}", e)
            // Continue without wake lock
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error acquiring wake lock: ${e.message}", e)
            // Continue without wake lock
        }
    }

    /**
     * Start capturing logs from Frida
     */
    private fun startLogCapture() {
        if (isCapturingLogs.get()) {
            LogUtils.i(TAG, "Log capture already running")
            return
        }

        LogUtils.i(TAG, "Starting log capture")
        isCapturingLogs.set(true)

        logCaptureThread = Thread {
            try {
                // Clear logcat first
                val clearProcess = Runtime.getRuntime().exec("logcat -c")
                clearProcess.waitFor()
                LogUtils.d(TAG, "Logcat buffer cleared")

                // Start logcat process to capture Frida logs
                val logcatCmd = "logcat -v threadtime" +
                        " frida:V frida-*:V Frida:V FRIDA:V" +
                        " FRIDA_*:V" +
                        " DEBUG:V ERROR:V TRACE:V" +
                        " System.out:V System.err:V" +
                        " Unity:V UnityMain:V" +
                        " ActivityThread:V" +
                        " *:S"

                val process = Runtime.getRuntime().exec(logcatCmd)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                // Start a thread to read error stream
                Thread {
                    try {
                        var errorLine: String?
                        while (errorReader.readLine().also { errorLine = it } != null) {
                            LogUtils.e(TAG, "Logcat error: $errorLine")
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Error reading logcat error stream", e)
                    }
                }.start()

                // Read logcat output
                var logLine: String? = null
                while (isCapturingLogs.get() && reader.readLine().also { logLine = it } != null) {
                    logLine?.let { line ->
                        // Filter for Frida-related logs
                        if (line.contains("frida", ignoreCase = true) ||
                            line.contains("FRIDA", ignoreCase = true) ||
                            line.contains("gadget", ignoreCase = true) ||
                            line.contains("script", ignoreCase = true) ||
                            line.contains("il2cpp", ignoreCase = true) ||
                            line.contains("pokemon", ignoreCase = true) ||
                            line.contains("[+]") ||
                            line.contains("[-]")) {

                            // Extract the actual message
                            val message = extractMessageFromLogcat(line)
                            if (message.isNotEmpty()) {
                                // Send to LogUtils as a trace message
                                LogUtils.trace(message)
                            }
                        }
                    }
                }

                LogUtils.d(TAG, "Logcat reader loop ended, cleaning up")
                reader.close()
                errorReader.close()
                process.destroy()

            } catch (e: Exception) {
                LogUtils.e(TAG, "Error capturing logs", e)
            } finally {
                isCapturingLogs.set(false)
                LogUtils.i(TAG, "Log capture thread terminated")
            }
        }

        logCaptureThread?.name = "LogCaptureThread"
        logCaptureThread?.isDaemon = true
        logCaptureThread?.start()

        LogUtils.i(TAG, "Log capture started")
    }

    /**
     * Stop capturing logs
     */
    private fun stopLogCapture() {
        if (!isCapturingLogs.get()) {
            return
        }

        LogUtils.i(TAG, "Stopping log capture")
        isCapturingLogs.set(false)

        try {
            logCaptureThread?.interrupt()
            logCaptureThread?.join(1000)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping log capture thread", e)
        }

        logCaptureThread = null
        LogUtils.i(TAG, "Log capture stopped")
    }

    /**
     * Extract message from logcat line
     */
    private fun extractMessageFromLogcat(logLine: String): String {
        // Try to extract the actual message from the logcat line
        val messageParts = logLine.split("): ")
        return if (messageParts.size > 1) {
            messageParts[1]
        } else {
            logLine
        }
    }

    /**
     * Start monitoring Pokémon GO and Frida status
     */
    private fun startMonitoring() {
        if (isMonitoring.get()) {
            LogUtils.i(TAG, "Monitoring already running")
            return
        }

        LogUtils.i(TAG, "Starting monitoring")
        isMonitoring.set(true)

        monitoringThread = Thread {
            try {
                while (isMonitoring.get()) {
                    // Check if Pokémon GO is running
                    val packageName = fridaScriptManager.getPokemonGoPackageName()
                    if (packageName != null) {
                        val isRunning = fridaScriptManager.isAppRunning(packageName)
                        LogUtils.d(TAG, "Pokémon GO running status: $isRunning")

                        if (isRunning) {
                            // Check Frida status
                            val fridaStatus = fridaScriptManager.checkFridaStatus()
                            LogUtils.d(TAG, "Frida status: $fridaStatus")

                            // Update notification with status
                            serviceStatus = if (fridaStatus) STATUS_RUNNING else STATUS_ERROR
                            updateNotification()
                        } else {
                            serviceStatus = STATUS_IDLE
                            updateNotification()
                        }
                    }

                    // Sleep for 30 seconds before next check
                    Thread.sleep(30000)
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error in monitoring thread", e)
            } finally {
                isMonitoring.set(false)
                LogUtils.i(TAG, "Monitoring thread terminated")
            }
        }

        monitoringThread?.name = "MonitoringThread"
        monitoringThread?.isDaemon = true
        monitoringThread?.start()

        LogUtils.i(TAG, "Monitoring started")
    }

    /**
     * Stop monitoring
     */
    private fun stopMonitoring() {
        if (!isMonitoring.get()) {
            return
        }

        LogUtils.i(TAG, "Stopping monitoring")
        isMonitoring.set(false)

        try {
            monitoringThread?.interrupt()
            monitoringThread?.join(1000)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping monitoring thread", e)
        }

        monitoringThread = null
        LogUtils.i(TAG, "Monitoring stopped")
    }

    /**
     * Get current service status
     */
    fun getServiceStatus(): String {
        return serviceStatus
    }

    /**
     * Check if service is running
     */
    fun isServiceRunning(): Boolean {
        return isRunning.get()
    }

    /**
     * Launch Pokémon GO with Frida hook
     */
    fun launchPokemonGo(): Boolean {
        LogUtils.i(TAG, "Launching Pokémon GO from service")

        // Make sure service is running in foreground
        if (!isRunning.get()) {
            try {
                // Direct approach to start foreground service with DATA_SYNC type only
                // Acquire wake lock to keep CPU running
                acquireWakeLock()

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                        // Use both DATA_SYNC and CONNECTED_DEVICE types for Android 14+
                        LogUtils.i(TAG, "Starting foreground service with DATA_SYNC and CONNECTED_DEVICE types on Android 14+")
                        startForeground(NOTIFICATION_ID, createNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                    } else {
                        LogUtils.i(TAG, "Starting foreground service with default type")
                        startForeground(NOTIFICATION_ID, createNotification())
                    }
                } catch (e: SecurityException) {
                    LogUtils.e(TAG, "Error starting foreground service", e)
                    // Fall back to DATA_SYNC type only
                    try {
                        LogUtils.i(TAG, "Falling back to starting foreground service with DATA_SYNC type only")
                        startForeground(NOTIFICATION_ID, createNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } catch (e2: Exception) {
                        LogUtils.e(TAG, "Error starting foreground service with DATA_SYNC type", e2)
                        // Last resort - try without a type
                        try {
                            LogUtils.i(TAG, "Falling back to starting foreground service without a type")
                            startForeground(NOTIFICATION_ID, createNotification())
                        } catch (e3: Exception) {
                            LogUtils.e(TAG, "Error starting foreground service without a type", e3)
                            // Last resort - try to keep the service running without foreground
                            Toast.makeText(applicationContext,
                                "Failed to start foreground service. App may be killed in background.",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // Set service status
                serviceStatus = STATUS_RUNNING
                isRunning.set(true)

                // Start log capture and monitoring if not already running
                if (!isCapturingLogs.get()) {
                    startLogCapture()
                }
                if (!isMonitoring.get()) {
                    startMonitoring()
                }

                LogUtils.i(TAG, "Foreground service started successfully in launchPokemonGo")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error starting foreground service in launchPokemonGo: ${e.message}", e)
            }
        }

        // Launch Pokémon GO
        val success = fridaScriptManager.launchPokemonGo()

        // Update notification
        serviceStatus = if (success) STATUS_RUNNING else STATUS_ERROR
        updateNotification()

        // Note: We don't need to explicitly inject the Frida script here
        // because the Zygote monitoring will detect the Pokémon GO process
        // and inject the script automatically

        return success
    }
}
