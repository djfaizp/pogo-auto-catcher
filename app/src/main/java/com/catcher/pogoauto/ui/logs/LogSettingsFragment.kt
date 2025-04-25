package com.catcher.pogoauto.ui.logs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.catcher.pogoauto.BuildConfig
import com.catcher.pogoauto.R
import com.catcher.pogoauto.databinding.FragmentLogSettingsBinding
import com.catcher.pogoauto.utils.LogUtils
import java.io.File

class LogSettingsFragment : Fragment() {
    companion object {
        private const val TAG = "LogSettingsFragment"
        private const val PC_RECEIVER_DOWNLOAD_URL = "https://github.com/yourusername/pogo-log-receiver/releases"
    }

    private var _binding: FragmentLogSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var logSettingsViewModel: LogSettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logSettingsViewModel = ViewModelProvider(this).get(LogSettingsViewModel::class.java)
        _binding = FragmentLogSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Set up observers
        setupObservers()

        // Set up button click listeners
        setupButtonListeners()

        return root
    }

    private fun setupObservers() {
        // File logging status
        logSettingsViewModel.isFileLoggingEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.buttonStartFileLogging.isEnabled = !isEnabled
            binding.buttonStopFileLogging.isEnabled = isEnabled
            binding.textFileLoggingStatus.text = if (isEnabled) {
                "Status: Logging to file"
            } else {
                "Status: Not logging to file"
            }
        }

        // Last exported file
        logSettingsViewModel.lastExportedFile.observe(viewLifecycleOwner) { file ->
            binding.buttonShareLogs.isEnabled = file != null
        }

        // Network streaming status
        logSettingsViewModel.isNetworkStreamingEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.buttonStartStreaming.isEnabled = !isEnabled
            binding.buttonStopStreaming.isEnabled = isEnabled
            binding.editIpAddress.isEnabled = !isEnabled
            binding.editPort.isEnabled = !isEnabled
            binding.textStreamingStatus.text = if (isEnabled) {
                val address = logSettingsViewModel.networkStreamingAddress.value
                val port = logSettingsViewModel.networkStreamingPort.value
                "Status: Streaming to $address:$port"
            } else {
                "Status: Not streaming"
            }
        }

        // Network streaming address
        logSettingsViewModel.networkStreamingAddress.observe(viewLifecycleOwner) { address ->
            if (!binding.editIpAddress.text.toString().equals(address)) {
                binding.editIpAddress.setText(address)
            }
        }

        // Network streaming port
        logSettingsViewModel.networkStreamingPort.observe(viewLifecycleOwner) { port ->
            if (port > 0 && binding.editPort.text.toString() != port.toString()) {
                binding.editPort.setText(port.toString())
            }
        }
    }

    private fun setupButtonListeners() {
        // File logging buttons
        binding.buttonStartFileLogging.setOnClickListener {
            if (logSettingsViewModel.startFileLogging(requireContext())) {
                Toast.makeText(requireContext(), "Started logging to file", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to start logging to file", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonStopFileLogging.setOnClickListener {
            logSettingsViewModel.stopFileLogging()
            Toast.makeText(requireContext(), "Stopped logging to file", Toast.LENGTH_SHORT).show()
        }

        // Export logs buttons
        binding.buttonExportLogs.setOnClickListener {
            val exportFile = logSettingsViewModel.exportLogs(requireContext())
            if (exportFile != null) {
                Toast.makeText(requireContext(), "Logs exported to ${exportFile.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to export logs", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonShareLogs.setOnClickListener {
            shareLogFile()
        }

        // Network streaming buttons
        binding.buttonStartStreaming.setOnClickListener {
            val ipAddress = binding.editIpAddress.text.toString()
            val portStr = binding.editPort.text.toString()

            if (ipAddress.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an IP address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val port = try {
                portStr.toInt()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "Please enter a valid port number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (port <= 0 || port > 65535) {
                Toast.makeText(requireContext(), "Port must be between 1 and 65535", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (logSettingsViewModel.startNetworkStreaming(ipAddress, port)) {
                Toast.makeText(requireContext(), "Started streaming to $ipAddress:$port", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to start streaming", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonStopStreaming.setOnClickListener {
            logSettingsViewModel.stopNetworkStreaming()
            Toast.makeText(requireContext(), "Stopped streaming", Toast.LENGTH_SHORT).show()
        }

        // Download PC receiver button
        binding.buttonDownloadReceiver.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PC_RECEIVER_DOWNLOAD_URL))
            startActivity(intent)
        }

        // Trace settings button
        binding.buttonTraceSettings.setOnClickListener {
            findNavController().navigate(R.id.nav_trace_settings)
        }

        // Clear logs button
        binding.buttonClearLogs.setOnClickListener {
            logSettingsViewModel.clearLogs()
            Toast.makeText(requireContext(), "Logs cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLogFile() {
        val file = logSettingsViewModel.lastExportedFile.value ?: return

        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "text/plain"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share Log File"))
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error sharing log file", e)
            Toast.makeText(requireContext(), "Error sharing log file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
