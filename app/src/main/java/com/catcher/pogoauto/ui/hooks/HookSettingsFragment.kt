package com.catcher.pogoauto.ui.hooks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.catcher.pogoauto.FridaScriptManager
import com.catcher.pogoauto.R
import com.catcher.pogoauto.databinding.FragmentHookSettingsBinding
import com.catcher.pogoauto.utils.LogUtils
import com.catcher.pogoauto.utils.SettingsManager

class HookSettingsFragment : Fragment() {
    companion object {
        private const val TAG = "HookSettingsFragment"
    }

    private var _binding: FragmentHookSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var hookSettingsViewModel: HookSettingsViewModel
    private lateinit var fridaScriptManager: FridaScriptManager
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize view model with application context
        val factory = HookSettingsViewModel.Factory(requireActivity().application)
        hookSettingsViewModel = ViewModelProvider(this, factory).get(HookSettingsViewModel::class.java)

        _binding = FragmentHookSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize managers
        fridaScriptManager = FridaScriptManager(requireContext())
        settingsManager = SettingsManager(requireContext())

        // Set up UI elements
        setupUI()

        return root
    }

    private fun setupUI() {
        // Set up title text
        hookSettingsViewModel.title.observe(viewLifecycleOwner) {
            binding.textHookSettings.text = it
        }

        // Set up injection settings
        setupInjectionSettings()

        // Set up perfect throw settings
        setupPerfectThrowSettings()

        // Set up movement settings
        setupMovementSettings()

        // Set up encounter settings
        setupEncounterSettings()

        // Set up apply button
        binding.buttonApplySettings.setOnClickListener {
            applySettings()
        }

        // Set up reset button
        binding.buttonResetSettings.setOnClickListener {
            resetSettings()
        }
    }

    private fun setupInjectionSettings() {
        // Injection delay settings
        hookSettingsViewModel.injectionDelay.observe(viewLifecycleOwner) {
            binding.sliderInjectionDelay.value = it.toFloat()
            binding.textInjectionDelayValue.text = String.format("%d ms", it)
        }

        binding.sliderInjectionDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                hookSettingsViewModel.setInjectionDelay(value.toInt())
                binding.textInjectionDelayValue.text = String.format("%d ms", value.toInt())
            }
        }
    }

    private fun setupPerfectThrowSettings() {
        // Perfect throw switch
        hookSettingsViewModel.isPerfectThrowEnabled.observe(viewLifecycleOwner) {
            binding.switchPerfectThrow.isChecked = it
        }

        binding.switchPerfectThrow.setOnCheckedChangeListener { _, isChecked ->
            hookSettingsViewModel.setPerfectThrowEnabled(isChecked)
            updatePerfectThrowSettingsVisibility()
        }

        // Perfect throw settings
        hookSettingsViewModel.perfectThrowCurveball.observe(viewLifecycleOwner) {
            binding.switchPerfectThrowCurveball.isChecked = it
        }

        binding.switchPerfectThrowCurveball.setOnCheckedChangeListener { _, isChecked ->
            hookSettingsViewModel.setPerfectThrowCurveball(isChecked)
        }

        hookSettingsViewModel.perfectThrowExcellent.observe(viewLifecycleOwner) {
            binding.radioPerfectThrowExcellent.isChecked = it
        }

        binding.radioPerfectThrowExcellent.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                hookSettingsViewModel.setPerfectThrowType(PerfectThrowType.EXCELLENT)
            }
        }

        hookSettingsViewModel.perfectThrowGreat.observe(viewLifecycleOwner) {
            binding.radioPerfectThrowGreat.isChecked = it
        }

        binding.radioPerfectThrowGreat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                hookSettingsViewModel.setPerfectThrowType(PerfectThrowType.GREAT)
            }
        }

        hookSettingsViewModel.perfectThrowNice.observe(viewLifecycleOwner) {
            binding.radioPerfectThrowNice.isChecked = it
        }

        binding.radioPerfectThrowNice.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                hookSettingsViewModel.setPerfectThrowType(PerfectThrowType.NICE)
            }
        }

        // Update visibility
        updatePerfectThrowSettingsVisibility()
    }

    private fun updatePerfectThrowSettingsVisibility() {
        val isVisible = hookSettingsViewModel.isPerfectThrowEnabled.value == true
        binding.layoutPerfectThrowSettings.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun setupMovementSettings() {
        // Auto walk switch
        hookSettingsViewModel.isAutoWalkEnabled.observe(viewLifecycleOwner) {
            binding.switchAutoWalk.isChecked = it
        }

        binding.switchAutoWalk.setOnCheckedChangeListener { _, isChecked ->
            hookSettingsViewModel.setAutoWalkEnabled(isChecked)
            updateAutoWalkSettingsVisibility()
        }

        // Auto walk settings
        hookSettingsViewModel.autoWalkSpeed.observe(viewLifecycleOwner) {
            binding.sliderAutoWalkSpeed.value = it
            binding.textAutoWalkSpeedValue.text = String.format("%.1f m/s", it)
        }

        binding.sliderAutoWalkSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                hookSettingsViewModel.setAutoWalkSpeed(value)
                binding.textAutoWalkSpeedValue.text = String.format("%.1f m/s", value)
            }
        }

        // Update visibility
        updateAutoWalkSettingsVisibility()
    }

    private fun updateAutoWalkSettingsVisibility() {
        val isVisible = hookSettingsViewModel.isAutoWalkEnabled.value == true
        binding.layoutAutoWalkSettings.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun setupEncounterSettings() {
        // Auto catch switch
        hookSettingsViewModel.isAutoCatchEnabled.observe(viewLifecycleOwner) {
            binding.switchAutoCatch.isChecked = it
        }

        binding.switchAutoCatch.setOnCheckedChangeListener { _, isChecked ->
            hookSettingsViewModel.setAutoCatchEnabled(isChecked)
            updateAutoCatchSettingsVisibility()
        }

        // Auto catch settings
        hookSettingsViewModel.autoCatchDelay.observe(viewLifecycleOwner) {
            binding.sliderAutoCatchDelay.value = it.toFloat()
            binding.textAutoCatchDelayValue.text = String.format("%d ms", it)
        }

        binding.sliderAutoCatchDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                hookSettingsViewModel.setAutoCatchDelay(value.toInt())
                binding.textAutoCatchDelayValue.text = String.format("%d ms", value.toInt())
            }
        }

        // Auto catch retry settings
        hookSettingsViewModel.autoCatchRetryOnEscape.observe(viewLifecycleOwner) {
            binding.switchAutoCatchRetry.isChecked = it
        }

        binding.switchAutoCatchRetry.setOnCheckedChangeListener { _, isChecked ->
            hookSettingsViewModel.setAutoCatchRetryOnEscape(isChecked)
        }

        // Max retries settings
        hookSettingsViewModel.autoCatchMaxRetries.observe(viewLifecycleOwner) {
            binding.sliderAutoCatchMaxRetries.value = it.toFloat()
            binding.textAutoCatchMaxRetriesValue.text = String.format("%d attempts", it)
        }

        binding.sliderAutoCatchMaxRetries.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                hookSettingsViewModel.setAutoCatchMaxRetries(value.toInt())
                binding.textAutoCatchMaxRetriesValue.text = String.format("%d attempts", value.toInt())
            }
        }

        // Pokéball type settings
        hookSettingsViewModel.pokeBallType.observe(viewLifecycleOwner) { type ->
            when (type) {
                PokeBallType.POKE_BALL -> binding.radioGroupPokeballType.check(R.id.radio_pokeball_type_poke_ball)
                PokeBallType.GREAT_BALL -> binding.radioGroupPokeballType.check(R.id.radio_pokeball_type_great_ball)
                PokeBallType.ULTRA_BALL -> binding.radioGroupPokeballType.check(R.id.radio_pokeball_type_ultra_ball)
            }
        }

        binding.radioGroupPokeballType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_pokeball_type_poke_ball -> {
                    hookSettingsViewModel.setPokeBallType(PokeBallType.POKE_BALL)
                }
                R.id.radio_pokeball_type_great_ball -> {
                    hookSettingsViewModel.setPokeBallType(PokeBallType.GREAT_BALL)
                }
                R.id.radio_pokeball_type_ultra_ball -> {
                    hookSettingsViewModel.setPokeBallType(PokeBallType.ULTRA_BALL)
                }
            }
        }

        // Update visibility
        updateAutoCatchSettingsVisibility()
    }

    private fun updateAutoCatchSettingsVisibility() {
        val isVisible = hookSettingsViewModel.isAutoCatchEnabled.value == true
        binding.layoutAutoCatchSettings.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun applySettings() {
        // Extract the Frida script
        if (fridaScriptManager.extractScriptFromAssets()) {
            // Update the Frida script with the current settings
            fridaScriptManager.setPerfectThrowEnabled(hookSettingsViewModel.isPerfectThrowEnabled.value ?: true)
            fridaScriptManager.setPerfectThrowCurveball(hookSettingsViewModel.perfectThrowCurveball.value ?: true)

            // Set throw type
            val throwType = when {
                hookSettingsViewModel.perfectThrowExcellent.value == true -> "EXCELLENT"
                hookSettingsViewModel.perfectThrowGreat.value == true -> "GREAT"
                hookSettingsViewModel.perfectThrowNice.value == true -> "NICE"
                else -> "EXCELLENT"
            }
            fridaScriptManager.setPerfectThrowType(throwType)

            // Set auto walk settings
            fridaScriptManager.setAutoWalkEnabled(hookSettingsViewModel.isAutoWalkEnabled.value ?: false)
            fridaScriptManager.setAutoWalkSpeed(hookSettingsViewModel.autoWalkSpeed.value ?: 1.0f)

            // Set injection settings
            fridaScriptManager.setInjectionDelay(hookSettingsViewModel.injectionDelay.value ?: 0)

            // Set auto catch settings
            fridaScriptManager.setAutoCatchEnabled(hookSettingsViewModel.isAutoCatchEnabled.value ?: false)
            fridaScriptManager.setAutoCatchDelay(hookSettingsViewModel.autoCatchDelay.value ?: 500)
            fridaScriptManager.setAutoCatchRetryOnEscape(hookSettingsViewModel.autoCatchRetryOnEscape.value ?: true)
            fridaScriptManager.setAutoCatchMaxRetries(hookSettingsViewModel.autoCatchMaxRetries.value ?: 3)

            // Set Pokéball type
            val ballType = when (hookSettingsViewModel.pokeBallType.value) {
                PokeBallType.POKE_BALL -> "POKE_BALL"
                PokeBallType.GREAT_BALL -> "GREAT_BALL"
                PokeBallType.ULTRA_BALL -> "ULTRA_BALL"
                else -> "POKE_BALL"
            }
            fridaScriptManager.setAutoCatchBallType(ballType)

            LogUtils.i(TAG, "Applied hook settings")
            Toast.makeText(requireContext(), "Settings applied", Toast.LENGTH_SHORT).show()
        } else {
            LogUtils.e(TAG, "Failed to extract Frida script")
            Toast.makeText(requireContext(), "Failed to extract Frida script", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetSettings() {
        // Reset all settings to defaults
        hookSettingsViewModel.resetToDefaults()

        LogUtils.i(TAG, "Reset hook settings to defaults")
        Toast.makeText(requireContext(), "Settings reset to defaults", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
