package com.catcher.pogoauto.ui.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.catcher.pogoauto.databinding.FragmentTraceSettingsBinding

class TraceSettingsFragment : Fragment() {
    companion object {
        private const val TAG = "TraceSettingsFragment"
    }

    private var _binding: FragmentTraceSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var traceSettingsViewModel: TraceSettingsViewModel

    // Map of category IDs to their switch views
    private val categorySwitches = mutableMapOf<String, Switch>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        traceSettingsViewModel = ViewModelProvider(this).get(TraceSettingsViewModel::class.java)

        _binding = FragmentTraceSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupCategorySwitches()
        setupObservers()
        setupButtonListeners()

        return root
    }

    private fun setupCategorySwitches() {
        // Map category names to their switch views
        categorySwitches["ENCOUNTER"] = binding.switchCategoryEncounter
        categorySwitches["CAPTURE"] = binding.switchCategoryCapture
        categorySwitches["MOVEMENT"] = binding.switchCategoryMovement
        categorySwitches["ITEM"] = binding.switchCategoryItem
        categorySwitches["NETWORK"] = binding.switchCategoryNetwork
        categorySwitches["GYM"] = binding.switchCategoryGym
        categorySwitches["RAID"] = binding.switchCategoryRaid
        categorySwitches["POKESTOP"] = binding.switchCategoryPokestop
        categorySwitches["FRIEND"] = binding.switchCategoryFriend
        categorySwitches["COLLECTION"] = binding.switchCategoryCollection
        categorySwitches["INIT"] = binding.switchCategoryInit
        categorySwitches["FRIDA"] = binding.switchCategoryFrida

        // Set up listeners for all category switches
        for ((category, switch) in categorySwitches) {
            switch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                traceSettingsViewModel.setTraceCategoryEnabled(category, isChecked)
            }
        }
    }

    private fun setupObservers() {
        // Master trace switch
        traceSettingsViewModel.isTraceEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.switchMasterTrace.isChecked = isEnabled
        }

        // Category switches
        traceSettingsViewModel.traceCategories.observe(viewLifecycleOwner) { categories ->
            for ((category, isEnabled) in categories) {
                categorySwitches[category]?.isChecked = isEnabled
            }
        }
    }

    private fun setupButtonListeners() {
        // Master trace switch
        binding.switchMasterTrace.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            traceSettingsViewModel.setTraceEnabled(isChecked)
            Toast.makeText(
                requireContext(),
                "Trace logging ${if (isChecked) "enabled" else "disabled"}",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Enable all button
        binding.buttonEnableAll.setOnClickListener {
            traceSettingsViewModel.setAllTraceCategoriesEnabled(true)
            Toast.makeText(requireContext(), "All trace categories enabled", Toast.LENGTH_SHORT).show()
        }

        // Disable all button
        binding.buttonDisableAll.setOnClickListener {
            traceSettingsViewModel.setAllTraceCategoriesEnabled(false)
            Toast.makeText(requireContext(), "All trace categories disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the trace categories when the fragment is resumed
        traceSettingsViewModel.refreshTraceCategories()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
