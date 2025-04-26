package com.catcher.pogoauto.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.catcher.pogoauto.utils.LogUtils

/**
 * Manager class for interacting with the AutoCatcherService
 */
class ServiceManager(private val context: Context) {

    companion object {
        private const val TAG = "ServiceManager"
    }

    // Service connection
    private var autoCatcherService: AutoCatcherService? = null
    private var isServiceBound = false

    // Service connection callback
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            LogUtils.i(TAG, "Service connected")
            val binder = service as AutoCatcherService.LocalBinder
            autoCatcherService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            LogUtils.i(TAG, "Service disconnected")
            autoCatcherService = null
            isServiceBound = false
        }
    }

    /**
     * Start the foreground service
     */
    fun startService() {
        LogUtils.i(TAG, "Starting service")
        val intent = Intent(context, AutoCatcherService::class.java).apply {
            action = AutoCatcherService.ACTION_START_SERVICE
        }
        context.startForegroundService(intent)
        bindService()
    }

    /**
     * Stop the foreground service
     */
    fun stopService() {
        LogUtils.i(TAG, "Stopping service")
        val intent = Intent(context, AutoCatcherService::class.java).apply {
            action = AutoCatcherService.ACTION_STOP_SERVICE
        }
        context.startService(intent)
        unbindService()
    }

    /**
     * Stop Pokémon GO from the service
     */
    fun stopPokemonGo() {
        LogUtils.i(TAG, "Stopping Pokémon GO from service manager")
        val intent = Intent(context, AutoCatcherService::class.java).apply {
            action = AutoCatcherService.ACTION_STOP_POKEMON_GO
        }
        context.startService(intent)
    }

    /**
     * Bind to the service
     */
    fun bindService() {
        if (!isServiceBound) {
            LogUtils.i(TAG, "Binding to service")
            val intent = Intent(context, AutoCatcherService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Unbind from the service
     */
    fun unbindService() {
        if (isServiceBound) {
            LogUtils.i(TAG, "Unbinding from service")
            context.unbindService(serviceConnection)
            isServiceBound = false
            autoCatcherService = null
        }
    }

    /**
     * Launch Pokémon GO with Frida hook through the service
     */
    fun launchPokemonGo(): Boolean {
        LogUtils.i(TAG, "Launching Pokémon GO through service")
        
        // Start service if not already running
        if (autoCatcherService == null || !isServiceBound) {
            startService()
            // Wait a bit for service to bind
            Thread.sleep(500)
        }
        
        // Launch through service if bound
        return if (autoCatcherService != null && isServiceBound) {
            autoCatcherService?.launchPokemonGo() ?: false
        } else {
            // Fallback to direct launch if service not bound
            LogUtils.w(TAG, "Service not bound, starting service and trying again")
            startService()
            false
        }
    }

    /**
     * Get service status
     */
    fun getServiceStatus(): String {
        return if (autoCatcherService != null && isServiceBound) {
            autoCatcherService?.getServiceStatus() ?: "Unknown"
        } else {
            "Not Connected"
        }
    }

    /**
     * Check if service is running
     */
    fun isServiceRunning(): Boolean {
        return autoCatcherService?.isServiceRunning() ?: false
    }
}
