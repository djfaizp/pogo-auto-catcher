package com.catcher.pogoauto.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.catcher.pogoauto.FridaScriptManager
import com.catcher.pogoauto.R
import com.catcher.pogoauto.databinding.FragmentHomeBinding
import com.catcher.pogoauto.service.ServiceManager
import com.catcher.pogoauto.utils.LibraryUtils
import java.io.File
import com.catcher.pogoauto.ui.log.LogEntryAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var fridaScriptManager: FridaScriptManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var logAdapter: LogEntryAdapter

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize managers
        fridaScriptManager = FridaScriptManager(requireContext())
        serviceManager = ServiceManager(requireContext())

        // Set up UI elements
        setupUI()

        return root
    }

    private fun setupUI() {
        // Set up title text
        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        // Set up status text
        homeViewModel.status.observe(viewLifecycleOwner) {
            binding.textViewStatus.text = "Status: $it"
        }

        // Set up RecyclerView for logs
        setupLogRecyclerView()

        // Set up log filter button
        binding.buttonFilterLog.setOnClickListener {
            showFilterDialog()
        }

        // Set up log clear button
        binding.buttonClearLog.setOnClickListener {
            homeViewModel.clearLog()
        }

        // Set up log output
        homeViewModel.logOutput.observe(viewLifecycleOwner) { logs ->
            // Update the log adapter
            logAdapter.updateLogs(logs)

            // Update log stats
            binding.textViewLogStats.text = "${logAdapter.itemCount} log entries"

            // Scroll to the bottom of the RecyclerView
            if (logAdapter.itemCount > 0) {
                binding.recyclerViewLog.scrollToPosition(logAdapter.itemCount - 1)
            }
        }

        // Set up check button
        binding.buttonCheck.setOnClickListener {
            checkPokemonGo()
        }

        // Set up launch button
        binding.buttonLaunch.setOnClickListener {
            launchPokemonGo()
        }

        // Set up stop button
        binding.buttonStop.setOnClickListener {
            stopPokemonGo()
        }

        // Set up open settings button
        binding.buttonOpenSettings.setOnClickListener {
            // Navigate to the Hook Settings fragment
            findNavController().navigate(R.id.nav_hook_settings)
        }
    }

    private fun launchPokemonGo() {
        // Check if Pokémon GO is installed and get its package name
        val packageName = fridaScriptManager.getPokemonGoPackageName()

        if (packageName != null) {
            homeViewModel.appendLog("Found Pokémon GO with package name: $packageName")

            // Extract and update the Frida script
            if (fridaScriptManager.extractScriptFromAssets()) {
                updateFridaScript()

                // Start the foreground service
                serviceManager.startService()
                homeViewModel.appendLog("Started foreground service")

                // Launch Pokémon GO through the service
                if (serviceManager.launchPokemonGo()) {
                    homeViewModel.setStatus(HomeViewModel.STATUS_RUNNING)
                    homeViewModel.appendLog("Launched Pokémon GO with Frida hook via service")
                } else {
                    homeViewModel.setStatus(HomeViewModel.STATUS_FAILED)
                    homeViewModel.appendLog("Failed to launch Pokémon GO (package: $packageName)")
                    Toast.makeText(requireContext(), "Failed to launch Pokémon GO", Toast.LENGTH_SHORT).show()
                }
            } else {
                homeViewModel.setStatus(HomeViewModel.STATUS_FAILED)
                homeViewModel.appendLog("Failed to extract Frida script")
                Toast.makeText(requireContext(), "Failed to extract Frida script", Toast.LENGTH_SHORT).show()
            }
        } else if (fridaScriptManager.isPokemonGoInstalled()) {
            // This is a fallback in case getPokemonGoPackageName() fails but isPokemonGoInstalled() succeeds
            homeViewModel.appendLog("Pokémon GO is installed but couldn't determine package name")

            // Extract and update the Frida script
            if (fridaScriptManager.extractScriptFromAssets()) {
                updateFridaScript()

                // Start the foreground service
                serviceManager.startService()
                homeViewModel.appendLog("Started foreground service")

                // Launch Pokémon GO through the service
                if (serviceManager.launchPokemonGo()) {
                    homeViewModel.setStatus(HomeViewModel.STATUS_RUNNING)
                    homeViewModel.appendLog("Launched Pokémon GO with Frida hook via service")
                } else {
                    homeViewModel.setStatus(HomeViewModel.STATUS_FAILED)
                    homeViewModel.appendLog("Failed to launch Pokémon GO")
                    Toast.makeText(requireContext(), "Failed to launch Pokémon GO", Toast.LENGTH_SHORT).show()
                }
            } else {
                homeViewModel.setStatus(HomeViewModel.STATUS_FAILED)
                homeViewModel.appendLog("Failed to extract Frida script")
                Toast.makeText(requireContext(), "Failed to extract Frida script", Toast.LENGTH_SHORT).show()
            }
        } else {
            homeViewModel.setStatus(HomeViewModel.STATUS_NOT_RUNNING)
            homeViewModel.appendLog("Pokémon GO is not installed")
            Toast.makeText(requireContext(), "Pokémon GO is not installed", Toast.LENGTH_SHORT).show()

            // List installed packages for debugging
            homeViewModel.appendLog("Checking for similar apps...")
            val packageManager = requireContext().packageManager
            val installedPackages = packageManager.getInstalledPackages(0)

            for (packageInfo in installedPackages) {
                val applicationInfo = packageInfo.applicationInfo
                if (applicationInfo != null) {
                    val appName = applicationInfo.loadLabel(packageManager).toString()
                    if (appName.contains("Pokémon", ignoreCase = true) ||
                        appName.contains("Pokemon", ignoreCase = true) ||
                        appName.contains("Niantic", ignoreCase = true)) {
                        homeViewModel.appendLog("Found similar app: $appName (${packageInfo.packageName})")
                    }
                }
            }
        }
    }

    private fun checkPokemonGo() {
        homeViewModel.clearLog()
        homeViewModel.appendLog("Checking for Pokémon GO installation...")

        // Check Frida server
        val fridaServerInstalled = LibraryUtils.isFridaServerInstalled(requireContext())
        homeViewModel.appendLog("Frida server: ${if (fridaServerInstalled) "Found" else "Not found"} at ${LibraryUtils.getFridaServerPath(requireContext())}")

        if (!fridaServerInstalled) {
            homeViewModel.appendLog("Extracting Frida server from assets...")
            val extractedPath = LibraryUtils.extractFridaServerFromAssets(requireContext())
            if (extractedPath != null) {
                homeViewModel.appendLog("Successfully extracted Frida server to $extractedPath")

                // Set executable permissions
                if (LibraryUtils.setFridaServerPermissions(requireContext(), extractedPath)) {
                    homeViewModel.appendLog("Successfully set executable permissions on Frida server")
                } else {
                    homeViewModel.appendLog("Failed to set executable permissions on Frida server")
                }
            } else {
                homeViewModel.appendLog("Failed to extract Frida server from assets")
            }
        }

        // Check if Frida server is running
        val fridaServerRunning = LibraryUtils.isFridaServerRunning()
        homeViewModel.appendLog("Frida server running: $fridaServerRunning")

        // Check if Pokémon GO is installed using our enhanced method
        val packageName = fridaScriptManager.getPokemonGoPackageName()

        if (packageName != null) {
            homeViewModel.setStatus(HomeViewModel.STATUS_NOT_RUNNING)
            homeViewModel.appendLog("Found Pokémon GO with package name: $packageName")

            // Get more details about the package
            try {
                val packageManager = requireContext().packageManager
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val applicationInfo = packageInfo.applicationInfo
                if (applicationInfo != null) {
                    val appName = applicationInfo.loadLabel(packageManager).toString()
                    val versionName = packageInfo.versionName
                    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }

                    homeViewModel.appendLog("App name: $appName")
                    homeViewModel.appendLog("Version: $versionName (code: $versionCode)")
                    homeViewModel.appendLog("Installation path: ${applicationInfo.sourceDir}")
                } else {
                    homeViewModel.appendLog("Application info is null")
                }

                Toast.makeText(requireContext(), "Pokémon GO found: $packageName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                homeViewModel.appendLog("Error getting package details: ${e.message}")
            }
        } else {
            homeViewModel.setStatus(HomeViewModel.STATUS_NOT_RUNNING)
            homeViewModel.appendLog("Pokémon GO is not installed")

            // List installed packages for debugging
            homeViewModel.appendLog("Checking for similar apps...")
            val packageManager = requireContext().packageManager
            val installedPackages = packageManager.getInstalledPackages(0)

            var foundSimilar = false
            for (packageInfo in installedPackages) {
                val applicationInfo = packageInfo.applicationInfo
                if (applicationInfo != null) {
                    val appName = applicationInfo.loadLabel(packageManager).toString()
                    if (appName.contains("Pokémon", ignoreCase = true) ||
                        appName.contains("Pokemon", ignoreCase = true) ||
                        appName.contains("Niantic", ignoreCase = true)) {
                        homeViewModel.appendLog("Found similar app: $appName (${packageInfo.packageName})")
                        foundSimilar = true
                    }
                }
            }

            if (!foundSimilar) {
                homeViewModel.appendLog("No similar apps found")
            }

            Toast.makeText(requireContext(), "Pokémon GO not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFridaScript() {
        // Extract the Frida script with default settings
        if (fridaScriptManager.extractScriptFromAssets()) {
            homeViewModel.appendLog("Updated Frida script with current settings")
        } else {
            homeViewModel.appendLog("Failed to update Frida script")
        }
    }

    /**
     * Set up the RecyclerView for logs
     */
    private fun setupLogRecyclerView() {
        // Initialize the adapter
        logAdapter = LogEntryAdapter()

        // Set up the RecyclerView
        binding.recyclerViewLog.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = logAdapter
        }
    }

    /**
     * Show the filter dialog
     */
    private fun showFilterDialog() {
        // Get all available categories
        val categories = logAdapter.getCategories().toList().sorted()

        // Create the dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter Logs")
            .setMultiChoiceItems(
                categories.toTypedArray(),
                categories.map { logAdapter.isFiltered(it) }.toBooleanArray()
            ) { _, which, isChecked ->
                // Toggle the filter
                logAdapter.toggleFilter(categories[which])
            }
            .setPositiveButton("Apply") { _, _ ->
                // Update the chip group visibility
                updateFilterChips()
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Add buttons for Select All and Clear Filters
        dialog.setOnShowListener {
            // Get the dialog buttons
            val positiveButton = dialog.getButton(MaterialAlertDialogBuilder.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(MaterialAlertDialogBuilder.BUTTON_NEGATIVE)

            // Create a horizontal layout for the additional buttons
            val layout = dialog.findViewById<ViewGroup>(android.R.id.buttonPanel)
            val selectAllButton = dialog.context.createButton("Select All") {
                // Select all filters
                logAdapter.selectAllFilters()

                // Update the dialog checkboxes
                val listView = dialog.findViewById<ListView>(android.R.id.list)
                for (i in 0 until categories.size) {
                    listView?.setItemChecked(i, true)
                }
            }

            val clearFiltersButton = dialog.context.createButton("Clear Filters") {
                // Clear all filters
                logAdapter.clearFilters()

                // Update the dialog checkboxes
                val listView = dialog.findViewById<ListView>(android.R.id.list)
                for (i in 0 until categories.size) {
                    listView?.setItemChecked(i, false)
                }
            }

            // Add the buttons to the layout
            layout?.addView(selectAllButton, 0)
            layout?.addView(clearFiltersButton, 1)

            // Adjust layout params for existing buttons
            positiveButton.layoutParams = (positiveButton.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.dialog_button_margin)
            }

            negativeButton.layoutParams = (negativeButton.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.dialog_button_margin)
            }
        }

        dialog.show()
    }

    /**
     * Extension function to create a button for the dialog
     */
    private fun Context.createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            this.setTextColor(resources.getColor(R.color.colorAccent, null))
            this.setBackgroundResource(android.R.color.transparent)
            this.setOnClickListener { onClick() }
            this.minWidth = resources.getDimensionPixelSize(R.dimen.dialog_button_min_width)
            this.setPadding(
                resources.getDimensionPixelSize(R.dimen.dialog_button_padding_horizontal),
                resources.getDimensionPixelSize(R.dimen.dialog_button_padding_vertical),
                resources.getDimensionPixelSize(R.dimen.dialog_button_padding_horizontal),
                resources.getDimensionPixelSize(R.dimen.dialog_button_padding_vertical)
            )
        }
    }

    /**
     * Stop Pokémon GO and clean up resources
     */
    private fun stopPokemonGo() {
        homeViewModel.appendLog("Attempting to stop Pokémon GO...")

        // Show a confirmation dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Stop Pokémon GO")
            .setMessage("Are you sure you want to stop Pokémon GO and the Frida hook?")
            .setPositiveButton("Stop") { _, _ ->
                // Stop Pokémon GO through the service
                serviceManager.stopPokemonGo()

                // Update UI
                homeViewModel.setStatus(HomeViewModel.STATUS_STOPPED)
                homeViewModel.appendLog("Stopping Pokémon GO through service")
                Toast.makeText(requireContext(), "Stopping Pokémon GO", Toast.LENGTH_SHORT).show()

                // Ask if user wants to stop the service too
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Stop Service")
                    .setMessage("Do you also want to stop the background service?")
                    .setPositiveButton("Yes") { _, _ ->
                        serviceManager.stopService()
                        homeViewModel.appendLog("Stopped background service")
                        Toast.makeText(requireContext(), "Background service stopped", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("No") { _, _ ->
                        homeViewModel.appendLog("Background service kept running")
                    }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Update the filter chips
     */
    private fun updateFilterChips() {
        // Get all available categories
        val categories = logAdapter.getCategories().toList().sorted()

        // Clear the chip group
        binding.chipGroupCategories.removeAllViews()

        // Add chips for active filters
        var hasActiveFilters = false
        for (category in categories) {
            if (logAdapter.isFiltered(category)) {
                hasActiveFilters = true

                // Create a chip for this category
                val chip = Chip(requireContext()).apply {
                    text = category
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        // Remove the filter
                        logAdapter.toggleFilter(category)
                        updateFilterChips()
                    }
                }

                // Add the chip to the group
                binding.chipGroupCategories.addView(chip)
            }
        }

        // Show/hide the chip group
        binding.horizontalScrollViewCategories.visibility = if (hasActiveFilters) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        // Check service status and update UI
        try {
            val serviceStatus = serviceManager.getServiceStatus()
            if (serviceManager.isServiceRunning()) {
                homeViewModel.setStatus(HomeViewModel.STATUS_RUNNING)
                homeViewModel.appendLog("Service is running with status: $serviceStatus")
            }
        } catch (e: Exception) {
            // Service might not be bound yet
        }
    }
}