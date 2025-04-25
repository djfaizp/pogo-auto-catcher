package com.catcher.pogoauto.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.catcher.pogoauto.utils.LogUtils

class HomeViewModel : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"

        // Status constants
        const val STATUS_NOT_RUNNING = "Not Running"
        const val STATUS_RUNNING = "Running"
        const val STATUS_STOPPED = "Stopped"
        const val STATUS_FAILED = "Failed"
    }

    private val _text = MutableLiveData<String>().apply {
        value = "Pokémon GO Auto Catcher"
    }
    val text: LiveData<String> = _text

    private val _status = MutableLiveData<String>().apply {
        value = "Not Running"
    }
    val status: LiveData<String> = _status

    // Use LogUtils for log output
    val logOutput: LiveData<String> = LogUtils.logLiveData

    init {
        // Initialize logs
        LogUtils.i(TAG, "Pokémon GO Auto Catcher initialized")
        LogUtils.i(TAG, "Ready for commands...")
    }

    fun setStatus(status: String) {
        _status.value = status
        LogUtils.i(TAG, "Status changed to: $status")
    }

    fun appendLog(message: String) {
        // Use LogUtils to log the message
        LogUtils.i(TAG, message)
    }

    fun clearLog() {
        LogUtils.clearLogs()
    }
}