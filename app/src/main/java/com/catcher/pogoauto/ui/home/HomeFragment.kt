package com.catcher.pogoauto.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.catcher.pogoauto.FridaScriptManager
import com.catcher.pogoauto.R
import com.catcher.pogoauto.databinding.FragmentHomeBinding
import com.catcher.pogoauto.ui.log.LogEntryAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var fridaScriptManager: FridaScriptManager
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

        // Initialize Frida script manager
        fridaScriptManager = FridaScriptManager(requireContext())

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

                // Launch Pokémon GO
                if (fridaScriptManager.launchPokemonGo()) {
                    homeViewModel.setStatus(HomeViewModel.STATUS_RUNNING)
                    homeViewModel.appendLog("Launched Pokémon GO with Frida hook")
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

                // Launch Pokémon GO
                if (fridaScriptManager.launchPokemonGo()) {
                    homeViewModel.setStatus(HomeViewModel.STATUS_RUNNING)
                    homeViewModel.appendLog("Launched Pokémon GO with Frida hook")
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
            .setNeutralButton("Clear Filters") { _, _ ->
                // Clear all filters
                logAdapter.clearFilters()
                updateFilterChips()
            }
            .create()

        dialog.show()
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
                // Run the stop operation in a background thread
                Thread {
                    val success = fridaScriptManager.stopPokemonGo()

                    // Update UI on the main thread
                    activity?.runOnUiThread {
                        if (success) {
                            homeViewModel.setStatus(HomeViewModel.STATUS_STOPPED)
                            homeViewModel.appendLog("Pokémon GO stopped successfully")
                            Toast.makeText(requireContext(), "Pokémon GO stopped", Toast.LENGTH_SHORT).show()
                        } else {
                            homeViewModel.setStatus(HomeViewModel.STATUS_FAILED)
                            homeViewModel.appendLog("Failed to stop Pokémon GO")
                            Toast.makeText(requireContext(), "Failed to stop Pokémon GO", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
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
}