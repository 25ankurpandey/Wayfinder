package com.wayfinder.wayfinder.presentation.device

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.wayfinder.wayfinder.R
import com.wayfinder.wayfinder.core.ConnectionState
import com.wayfinder.wayfinder.core.DeviceInfo
import com.wayfinder.wayfinder.data.network.tcp.TcpConnectionManager
import com.wayfinder.wayfinder.data.network.udp.UdpDiscovery
import kotlinx.coroutines.launch

/**
 * Device discovery dialog with persistent TCP connection support.
 * 
 * Uses TcpConnectionManager for persistent connections instead of
 * the legacy one-shot TcpClient approach.
 * 
 * Supports a callback for when connection succeeds, allowing the parent
 * fragment to take action (e.g., auto-start navigation).
 */
class DeviceDiscoveryDialog : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "DeviceDiscoveryDialog"
        
        fun newInstance(): DeviceDiscoveryDialog {
            return DeviceDiscoveryDialog()
        }
    }

    private val devices: MutableList<DeviceInfo> = mutableListOf()
    private lateinit var adapter: DeviceListAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var devicesTextView: TextView
    private lateinit var disconnectButton: MaterialButton
    private val handler = Handler(Looper.getMainLooper())
    private var udpDiscovery: UdpDiscovery? = null
    
    // Callback for when connection succeeds
    private var onConnectedCallback: (() -> Unit)? = null
    
    // Use the singleton connection manager
    private val connectionManager by lazy { 
        TcpConnectionManager.getInstance(requireContext()) 
    }
    
    /**
     * Set callback to be invoked when connection succeeds.
     * Use this to auto-start navigation after device selection.
     */
    fun setOnConnectedListener(callback: () -> Unit) {
        onConnectedCallback = callback
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.device_discovery_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        startDeviceDiscovery()
        scheduleDeviceRemovalCheck()
        observeConnectionState()
        
        // Check if already connected and show disconnect button
        updateDisconnectButtonVisibility()

        view.findViewById<ImageButton>(R.id.closeButton).setOnClickListener {
            stopDeviceDiscovery()
            dismiss()
        }
    }

    private fun setupUI(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.deviceListRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        adapter = DeviceListAdapter(devices) { deviceInfo ->
            deviceInfo?.let { connectToDevice(it) }
        }
        recyclerView.adapter = adapter

        progressBar = view.findViewById(R.id.progressBar)
        devicesTextView = view.findViewById(R.id.devicesTextView)
        disconnectButton = view.findViewById(R.id.disconnectButton)
        
        // Setup disconnect button
        disconnectButton.setOnClickListener {
            disconnectFromQuest()
        }

        updateNoDevicesText()
        setupBottomSheetBehavior(view)
    }
    
    private fun updateDisconnectButtonVisibility() {
        val isConnected = connectionManager.isConnected()
        disconnectButton.visibility = if (isConnected) View.VISIBLE else View.GONE
        
        // Update button text with device name if connected
        val currentState = connectionManager.connectionState.value
        if (currentState is ConnectionState.Connected) {
            disconnectButton.text = "Disconnect from ${currentState.deviceName}"
        }
    }
    
    private fun disconnectFromQuest() {
        connectionManager.disconnect()
        Toast.makeText(context, "Disconnected from Quest", Toast.LENGTH_SHORT).show()
        updateDisconnectButtonVisibility()
        updateNoDevicesText()
    }
    
    private fun setupBottomSheetBehavior(view: View) {
        val parentLayout = view.parent as View
        parentLayout.post {
            val parentLayoutParams = parentLayout.layoutParams as CoordinatorLayout.LayoutParams
            val behavior = parentLayoutParams.behavior as BottomSheetBehavior

            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isHideable = false

            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {}
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        }
    }
    
    private fun startDeviceDiscovery() {
        Log.d(TAG, "Starting UDP device discovery")
        progressBar.visibility = View.VISIBLE
        
        udpDiscovery = UdpDiscovery { deviceInfo ->
            handler.post {
                addDevice(deviceInfo)
            }
        }
        udpDiscovery?.startDiscovery()
    }

    private fun updateNoDevicesText() {
        devicesTextView.text = when {
            connectionManager.isConnected() -> "Connected - tap a device to switch"
            adapter.itemCount == 0 -> "Searching for Quest devices..."
            else -> "Tap a device to connect"
        }
    }
    
    private fun observeConnectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            connectionManager.connectionState.observe(viewLifecycleOwner) { state ->
                handleConnectionState(state)
            }
        }
    }
    
    private fun handleConnectionState(state: ConnectionState) {
        when (state) {
            is ConnectionState.Connecting -> {
                progressBar.visibility = View.VISIBLE
                devicesTextView.text = "Connecting to ${state.deviceName}..."
                disconnectButton.visibility = View.GONE
            }
            is ConnectionState.Connected -> {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Connected to ${state.deviceName}", Toast.LENGTH_SHORT).show()
                stopDeviceDiscovery()
                
                // Invoke callback BEFORE dismissing so parent can handle it
                onConnectedCallback?.invoke()
                
                dismiss()
            }
            is ConnectionState.Failed -> {
                progressBar.visibility = View.GONE
                Toast.makeText(context, state.reason, Toast.LENGTH_LONG).show()
                updateNoDevicesText()
                updateDisconnectButtonVisibility()
            }
            is ConnectionState.Reconnecting -> {
                progressBar.visibility = View.VISIBLE
                devicesTextView.text = "Reconnecting to ${state.deviceName} (attempt ${state.attemptNumber})..."
            }
            is ConnectionState.Disconnected -> {
                progressBar.visibility = View.GONE
                updateNoDevicesText()
                updateDisconnectButtonVisibility()
            }
        }
    }

    /**
     * Connect to device using persistent TcpConnectionManager
     */
    private fun connectToDevice(deviceInfo: DeviceInfo) {
        Log.d(TAG, "Connecting to device: ${deviceInfo.deviceName} at ${deviceInfo.ipAddress}")
        
        // Use the new persistent connection manager
        connectionManager.connect(deviceInfo.ipAddress, deviceInfo.deviceName ?: "Unknown Device")
    }

    fun addDevice(deviceInfo: DeviceInfo) {
        adapter.addOrUpdateDevice(deviceInfo)
        updateNoDevicesText()
    }

    private fun scheduleDeviceRemovalCheck() {
        val checkInterval: Long = 2000
        val deviceTimeout: Long = 10000

        handler.postDelayed(object : Runnable {
            override fun run() {
                adapter.removeStaleDevices(deviceTimeout)
                updateNoDevicesText()
                handler.postDelayed(this, checkInterval)
            }
        }, checkInterval)
    }

    private fun stopDeviceDiscovery() {
        Log.d(TAG, "Stopping device discovery")
        udpDiscovery?.stopDiscovery()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopDeviceDiscovery()
    }
}
