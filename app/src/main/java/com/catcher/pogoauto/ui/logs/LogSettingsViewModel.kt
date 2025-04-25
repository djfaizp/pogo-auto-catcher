package com.catcher.pogoauto.ui.logs

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.catcher.pogoauto.utils.LogUtils
import java.io.File

class LogSettingsViewModel : ViewModel() {
    companion object {
        private const val TAG = "LogSettingsViewModel"
    }

    // File logging status
    private val _isFileLoggingEnabled = MutableLiveData<Boolean>().apply {
        value = false
    }
    val isFileLoggingEnabled: LiveData<Boolean> = _isFileLoggingEnabled

    // Current log file
    private val _currentLogFile = MutableLiveData<File?>()
    val currentLogFile: LiveData<File?> = _currentLogFile

    // Last exported log file
    private val _lastExportedFile = MutableLiveData<File?>()
    val lastExportedFile: LiveData<File?> = _lastExportedFile

    // Network streaming status
    private val _isNetworkStreamingEnabled = MutableLiveData<Boolean>().apply {
        value = LogUtils.isNetworkStreamingEnabled()
    }
    val isNetworkStreamingEnabled: LiveData<Boolean> = _isNetworkStreamingEnabled

    // Network streaming address
    private val _networkStreamingAddress = MutableLiveData<String>().apply {
        value = LogUtils.getNetworkStreamingAddress()
    }
    val networkStreamingAddress: LiveData<String> = _networkStreamingAddress

    // Network streaming port
    private val _networkStreamingPort = MutableLiveData<Int>().apply {
        value = LogUtils.getNetworkStreamingPort()
    }
    val networkStreamingPort: LiveData<Int> = _networkStreamingPort

    /**
     * Start logging to a file
     */
    fun startFileLogging(context: Context): Boolean {
        val logFile = LogUtils.startFileLogging(context)
        if (logFile != null) {
            _currentLogFile.value = logFile
            _isFileLoggingEnabled.value = true
            LogUtils.i(TAG, "Started file logging to ${logFile.absolutePath}")
            return true
        }
        return false
    }

    /**
     * Stop logging to a file
     */
    fun stopFileLogging() {
        LogUtils.stopFileLogging()
        _isFileLoggingEnabled.value = false
        LogUtils.i(TAG, "Stopped file logging")
    }

    /**
     * Export logs to a file
     */
    fun exportLogs(context: Context): File? {
        val exportFile = LogUtils.exportLogs(context)
        if (exportFile != null) {
            _lastExportedFile.value = exportFile
            LogUtils.i(TAG, "Exported logs to ${exportFile.absolutePath}")
        }
        return exportFile
    }

    /**
     * Start streaming logs over the network
     */
    fun startNetworkStreaming(ipAddress: String, port: Int): Boolean {
        val success = LogUtils.startNetworkStreaming(ipAddress, port)
        if (success) {
            _isNetworkStreamingEnabled.value = true
            _networkStreamingAddress.value = ipAddress
            _networkStreamingPort.value = port
            LogUtils.i(TAG, "Started network streaming to $ipAddress:$port")
        }
        return success
    }

    /**
     * Stop streaming logs over the network
     */
    fun stopNetworkStreaming() {
        LogUtils.stopNetworkStreaming()
        _isNetworkStreamingEnabled.value = false
        LogUtils.i(TAG, "Stopped network streaming")
    }

    /**
     * Clear logs
     */
    fun clearLogs() {
        LogUtils.clearLogs()
        LogUtils.i(TAG, "Logs cleared")
    }
}
