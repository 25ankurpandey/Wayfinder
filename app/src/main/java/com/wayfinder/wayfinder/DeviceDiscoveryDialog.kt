package com.wayfinder.wayfinder

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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DeviceDiscoveryDialog(
    private val onDeviceClicked: (DeviceInfo) -> Unit
) : BottomSheetDialogFragment() {

    private val devices: MutableList<DeviceInfo> = mutableListOf()
    private lateinit var adapter: DeviceListAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var devicesTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var udpDiscovery: UdpDiscovery? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.device_discovery_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        scheduleDeviceRemovalCheck()

        view.findViewById<ImageButton>(R.id.closeButton).setOnClickListener {
            stopDeviceDiscovery()
            dismiss()
        }
    }

    private fun setupUI(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.deviceListRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = DeviceListAdapter(devices) { deviceInfo ->
            deviceInfo?.let { onDeviceClicked(it) }
            dismiss()
        }
        recyclerView.adapter = adapter

        progressBar = view.findViewById(R.id.progressBar)
        devicesTextView = view.findViewById(R.id.devicesTextView)

        // Initially, show "No devices found" if the list is empty
        updateNoDevicesText()

        val parentLayout = view.parent as View
        parentLayout.post {
            val parentLayoutParams = parentLayout.layoutParams as CoordinatorLayout.LayoutParams
            val behavior = parentLayoutParams.behavior as BottomSheetBehavior

            // Set initial state to expanded
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            // Optionally, you can set the behavior to not hide the dialog
            behavior.isHideable = false

            // Set a callback to adjust the behavior if needed
            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    // Handle state changes if needed
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Handle slide changes if needed
                }
            })
        }
    }

    private fun updateNoDevicesText() {
        devicesTextView.text = if (adapter.itemCount == 0) {
            "Searching for devices..."
        } else {
            "Tap a device to connect"
        }
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
                // Remove stale devices and check if the list is now empty
                adapter.removeStaleDevices(deviceTimeout)
                updateNoDevicesText() // Update the text based on the current list size

                handler.postDelayed(this, checkInterval)
            }
        }, checkInterval)
    }

    private fun stopDeviceDiscovery() {
        udpDiscovery?.stopDiscovery()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopDeviceDiscovery()
    }
}
