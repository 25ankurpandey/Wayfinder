package com.wayfinder.wayfinder.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manager for persistent user preferences.
 * Handles settings for dynamic rerouting and other app configurations.
 */
class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "wayfinder_prefs"
        
        // Keys
        private const val KEY_DYNAMIC_REROUTING_ENABLED = "dynamic_rerouting_enabled"
        private const val KEY_OFF_ROUTE_THRESHOLD = "off_route_threshold_meters"
        private const val KEY_REROUTE_DEBOUNCE_SECONDS = "reroute_debounce_seconds"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect_enabled"
        private const val KEY_LAST_CONNECTED_DEVICE = "last_connected_device"
        private const val KEY_SHOW_TRAFFIC_ALERTS = "show_traffic_alerts"
        private const val KEY_VOICE_NAVIGATION = "voice_navigation_enabled"
        
        // Defaults
        private const val DEFAULT_OFF_ROUTE_THRESHOLD = 30.0f
        private const val DEFAULT_REROUTE_DEBOUNCE = 10
    }
    
    /**
     * Whether dynamic rerouting is enabled.
     * When disabled, the app won't check for route deviations.
     */
    var isDynamicReroutingEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_REROUTING_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC_REROUTING_ENABLED, value) }
    
    /**
     * Distance threshold in meters to consider user off-route.
     */
    var offRouteThresholdMeters: Float
        get() = prefs.getFloat(KEY_OFF_ROUTE_THRESHOLD, DEFAULT_OFF_ROUTE_THRESHOLD)
        set(value) = prefs.edit { putFloat(KEY_OFF_ROUTE_THRESHOLD, value) }
    
    /**
     * Seconds to wait before triggering reroute after off-route detection.
     */
    var rerouteDebounceSeconds: Int
        get() = prefs.getInt(KEY_REROUTE_DEBOUNCE_SECONDS, DEFAULT_REROUTE_DEBOUNCE)
        set(value) = prefs.edit { putInt(KEY_REROUTE_DEBOUNCE_SECONDS, value) }
    
    /**
     * Whether to automatically reconnect to last device.
     */
    var isAutoReconnectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_RECONNECT, value) }
    
    /**
     * Last successfully connected device name.
     */
    var lastConnectedDeviceName: String?
        get() = prefs.getString(KEY_LAST_CONNECTED_DEVICE, null)
        set(value) = prefs.edit { putString(KEY_LAST_CONNECTED_DEVICE, value) }
    
    /**
     * Whether to show traffic alert notifications.
     */
    var showTrafficAlerts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TRAFFIC_ALERTS, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_TRAFFIC_ALERTS, value) }
    
    /**
     * Whether voice navigation prompts are enabled.
     */
    var isVoiceNavigationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_NAVIGATION, true)
        set(value) = prefs.edit { putBoolean(KEY_VOICE_NAVIGATION, value) }
    
    /**
     * Clear all preferences.
     */
    fun clearAll() {
        prefs.edit { clear() }
    }
}
