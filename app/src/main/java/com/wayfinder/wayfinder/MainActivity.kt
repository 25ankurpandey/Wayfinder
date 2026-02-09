package com.wayfinder.wayfinder

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.libraries.places.api.Places
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.wayfinder.wayfinder.R
import com.wayfinder.wayfinder.core.ConnectionState
import com.wayfinder.wayfinder.core.Constants
import com.wayfinder.wayfinder.databinding.ActivityMainBinding
import com.wayfinder.wayfinder.data.network.tcp.TcpConnectionManager
import com.wayfinder.wayfinder.presentation.device.DeviceDiscoveryDialog
import com.wayfinder.wayfinder.presentation.map.MapFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main activity for Wayfinder app.
 * 
 * Uses Material Design 3 with:
 * - DrawerLayout for settings/help navigation
 * - BottomNavigationView for Map/Navigate/Connect switching
 * - ExtendedFloatingActionButton for starting navigation
 * - Glassmorphism-styled cards for route info and connection status
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    
    // Connection manager for Quest communication
    private val connectionManager by lazy { TcpConnectionManager.getInstance(this) }

    private val locationPermissionRequestCode = 1000
    
    // Debug mode state
    private var isDebugModeEnabled = false
    private val debugLogs = StringBuilder()
    private val debugTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val maxDebugLogLines = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Places API
        val apiKey = Constants.GOOGLE_MAPS_API_KEY
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        setupToolbar()
        setupDrawer()
        setupFab()
        setupConnectionStatusCard()
        observeConnectionState()
        
        checkLocationPermission()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.drawer_settings -> {
                    navigateToSettings()
                }
                R.id.drawer_help -> {
                    showHelpDialog()
                }
                R.id.drawer_disconnect -> {
                    disconnectQuest()
                }
                R.id.drawer_debug_mode -> {
                    toggleDebugMode(menuItem.isChecked.not())
                    menuItem.isChecked = isDebugModeEnabled
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }
    private fun setupFab() {
        binding.startNavigationFab.setOnClickListener {
            startNavigation()
        }
    }
    
    /**
     * Setup connection status card - clickable to toggle disconnect dropdown when connected.
     */
    private fun setupConnectionStatusCard() {
        binding.connectionStatusCard.setOnClickListener {
            if (connectionManager.isConnected()) {
                // Toggle dropdown visibility
                toggleDisconnectDropdown()
            } else {
                // Not connected - show device discovery
                showDeviceDiscovery()
            }
        }
        
        // Setup disconnect button in dropdown
        binding.disconnectButton.setOnClickListener {
            disconnectQuest()
            hideDisconnectDropdown()
        }
    }
    
    /**
     * Toggle the disconnect dropdown visibility.
     */
    private fun toggleDisconnectDropdown() {
        if (binding.disconnectDropdown.visibility == View.VISIBLE) {
            hideDisconnectDropdown()
        } else {
            binding.disconnectDropdown.visibility = View.VISIBLE
            // Rotate arrow up
            binding.connectionDropdownArrow.animate().rotation(180f).setDuration(200).start()
        }
    }
    
    /**
     * Hide the disconnect dropdown.
     */
    private fun hideDisconnectDropdown() {
        binding.disconnectDropdown.visibility = View.GONE
        // Rotate arrow down
        binding.connectionDropdownArrow.animate().rotation(0f).setDuration(200).start()
    }


    private fun observeConnectionState() {
        connectionManager.connectionState.observe(this) { state ->
            updateConnectionStatus(state)
            
            // Log connection state changes for debug mode
            val stateDesc = when (state) {
                is ConnectionState.Connected -> "Connected to ${state.deviceName}"
                is ConnectionState.Connecting -> "Connecting to ${state.deviceName}..."
                is ConnectionState.Disconnected -> "Disconnected"
                is ConnectionState.Reconnecting -> "Reconnecting (${state.attemptNumber})..."
                is ConnectionState.Failed -> "Failed: ${state.reason}"
            }
            debugLog("Connection: $stateDesc")
        }
    }

    private fun updateConnectionStatus(state: ConnectionState) {
        val indicatorDrawable = when (state) {
            is ConnectionState.Connected -> R.drawable.connection_indicator_connected
            else -> R.drawable.connection_indicator_disconnected
        }
        
        val statusText = when (state) {
            is ConnectionState.Disconnected -> getString(R.string.status_disconnected)
            is ConnectionState.Connecting -> getString(R.string.status_connecting)
            is ConnectionState.Connected -> getString(R.string.status_connected, state.deviceName)
            is ConnectionState.Reconnecting -> getString(R.string.status_connecting) + " (${state.attemptNumber})"
            is ConnectionState.Failed -> getString(R.string.status_disconnected) // Revert to disconnected
        }

        binding.connectionIndicator.setBackgroundResource(indicatorDrawable)
        binding.connectionStatusText.text = statusText
        
        // Show/hide dropdown arrow based on connection state
        val isConnected = state is ConnectionState.Connected
        binding.connectionDropdownArrow.visibility = if (isConnected) View.VISIBLE else View.GONE
        
        // Hide dropdown when disconnected
        if (!isConnected) {
            hideDisconnectDropdown()
        }
        
        // Show styled error snackbar on failure with dark glassmorphism style
        if (state is ConnectionState.Failed) {
            val snackbar = com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                "⚠️ ${state.reason}",
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            )
            // Apply dark glassmorphism styling like the disconnect option
            val snackbarView = snackbar.view
            snackbarView.setBackgroundResource(R.drawable.snackbar_error_background)
            snackbarView.elevation = 16f
            
            val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            textView.setTextColor(ContextCompat.getColor(this, R.color.error))
            textView.textSize = 14f
            textView.maxLines = 3
            textView.setTypeface(null, android.graphics.Typeface.BOLD)
            
            snackbar.show()
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionRequestCode)
        } else {
            loadMapFragment()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                loadMapFragment()
            } else {
                Toast.makeText(this, getString(R.string.error_location_unavailable), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadMapFragment() {
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) !is MapFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapFragment())
                .commit()
        }
    }

    private fun showDeviceDiscovery() {
        // Show device discovery dialog
        val dialog = DeviceDiscoveryDialog.newInstance()
        dialog.show(supportFragmentManager, "DeviceDiscovery")
    }

    private fun startNavigation() {
        // Get current route from MapFragment and send to Quest
        val mapFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? MapFragment
        mapFragment?.sendRouteToQuest()
    }

    private fun disconnectQuest() {
        connectionManager.disconnect()
        Toast.makeText(this, getString(R.string.status_disconnected), Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionManager.disconnect()
    }

    // Public methods for fragments to update UI
    fun showRouteInfo(distance: String, eta: String) {
        binding.routeBottomSheetContainer.visibility = View.VISIBLE
        binding.routeDistanceText.text = distance
        binding.routeEtaText.text = eta
        binding.startNavigationFab.visibility = View.VISIBLE
    }

    fun hideRouteInfo() {
        binding.routeBottomSheetContainer.visibility = View.GONE
        binding.startNavigationFab.visibility = View.GONE
    }
    
    /**
     * Navigate to Settings fragment.
     */
    private fun navigateToSettings() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, com.wayfinder.wayfinder.presentation.settings.SettingsFragment.newInstance())
            .addToBackStack("settings")
            .commit()
    }
    
    /**
     * Show help dialog with app information.
     */
    private fun showHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_Wayfinder_Dialog)
            .setTitle(getString(R.string.help_title))
            .setMessage(getString(R.string.help_message))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    // ==================== DEBUG MODE ====================
    
    /**
     * Toggle debug mode on/off.
     */
    private fun toggleDebugMode(enabled: Boolean) {
        isDebugModeEnabled = enabled
        
        if (enabled) {
            setupDebugOverlay()
            binding.debugLogCard.visibility = View.VISIBLE
            debugLog("Debug mode enabled")
        } else {
            binding.debugLogCard.visibility = View.GONE
        }
    }
    
    /**
     * Setup debug overlay button listeners.
     */
    private fun setupDebugOverlay() {
        binding.debugCopyButton.setOnClickListener {
            copyDebugLogsToClipboard()
        }
        
        binding.debugClearButton.setOnClickListener {
            clearDebugLogs()
        }
        
        binding.debugCloseButton.setOnClickListener {
            toggleDebugMode(false)
            // Update menu item state
            binding.navigationView.menu.findItem(R.id.drawer_debug_mode)?.isChecked = false
        }
    }
    
    /**
     * Add a log entry to the debug log.
     * Call this from anywhere in the app to log debug info.
     */
    fun debugLog(message: String) {
        if (!isDebugModeEnabled) return
        
        val timestamp = debugTimeFormat.format(Date())
        val logLine = "[$timestamp] $message\n"
        
        debugLogs.append(logLine)
        
        // Limit log size
        val lines = debugLogs.lines()
        if (lines.size > maxDebugLogLines) {
            debugLogs.clear()
            debugLogs.append(lines.takeLast(maxDebugLogLines).joinToString("\n"))
        }
        
        // Update UI
        runOnUiThread {
            binding.debugLogText.text = debugLogs.toString()
            binding.debugScrollView.post {
                binding.debugScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
    
    /**
     * Copy debug logs to clipboard.
     */
    private fun copyDebugLogsToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Wayfinder Debug Logs", debugLogs.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Clear all debug logs.
     */
    private fun clearDebugLogs() {
        debugLogs.clear()
        binding.debugLogText.text = ""
        debugLog("Logs cleared")
    }
}

