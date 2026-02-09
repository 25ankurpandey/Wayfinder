package com.wayfinder.wayfinder.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.wayfinder.wayfinder.R
import com.wayfinder.wayfinder.data.preferences.PreferencesManager

/**
 * Settings fragment for configuring app behavior.
 * 
 * Settings include:
 * - Dynamic rerouting toggle
 * - Off-route threshold distance
 * - Auto-reconnect to last device
 * - Voice navigation (future)
 */
class SettingsFragment : Fragment() {

    private lateinit var preferencesManager: PreferencesManager
    
    // Views
    private lateinit var dynamicReroutingSwitch: SwitchMaterial
    private lateinit var offRouteThresholdSlider: Slider
    private lateinit var autoReconnectSwitch: SwitchMaterial
    private lateinit var voiceNavigationSwitch: SwitchMaterial

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferencesManager = PreferencesManager(requireContext())
        
        initializeViews(view)
        loadSettings()
        setupListeners()
    }
    
    private fun initializeViews(view: View) {
        dynamicReroutingSwitch = view.findViewById(R.id.switch_dynamic_rerouting)
        offRouteThresholdSlider = view.findViewById(R.id.slider_off_route_threshold)
        autoReconnectSwitch = view.findViewById(R.id.switch_auto_reconnect)
        voiceNavigationSwitch = view.findViewById(R.id.switch_voice_navigation)
    }
    
    private fun loadSettings() {
        // Use property access syntax (not function call)
        dynamicReroutingSwitch.isChecked = preferencesManager.isDynamicReroutingEnabled
        offRouteThresholdSlider.value = preferencesManager.offRouteThresholdMeters
        autoReconnectSwitch.isChecked = preferencesManager.isAutoReconnectEnabled
        voiceNavigationSwitch.isChecked = preferencesManager.isVoiceNavigationEnabled
        
        // Enable/disable threshold slider based on rerouting toggle
        offRouteThresholdSlider.isEnabled = dynamicReroutingSwitch.isChecked
    }
    
    private fun setupListeners() {
        dynamicReroutingSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.isDynamicReroutingEnabled = isChecked
            offRouteThresholdSlider.isEnabled = isChecked
        }
        
        offRouteThresholdSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferencesManager.offRouteThresholdMeters = value
            }
        }
        
        autoReconnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.isAutoReconnectEnabled = isChecked
        }
        
        voiceNavigationSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.isVoiceNavigationEnabled = isChecked
        }
    }
    
    companion object {
        fun newInstance() = SettingsFragment()
    }
}
