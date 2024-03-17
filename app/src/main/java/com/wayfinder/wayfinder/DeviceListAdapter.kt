package com.wayfinder.wayfinder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceListAdapter(
    private val devices: MutableList<DeviceInfo>, // Changed to a mutable list
    private val onDeviceClicked: (DeviceInfo) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceNameTextView: TextView = view.findViewById(R.id.deviceNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceNameTextView.visibility = View.VISIBLE
        holder.deviceNameTextView.text = device.deviceName ?: "Unknown Device"
        holder.itemView.setOnClickListener { onDeviceClicked(device) }
    }

    override fun getItemCount(): Int = devices.size

    fun addOrUpdateDevice(newDevice: DeviceInfo) {
        val index = devices.indexOfFirst { it.ipAddress == newDevice.ipAddress }
        if (index != -1) {
            devices[index] = newDevice.apply { lastBroadcastTime = System.currentTimeMillis() }
            notifyItemChanged(index)
        } else {
            devices.add(newDevice)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun removeStaleDevices(timeout: Long) {
        val currentTime = System.currentTimeMillis()
        devices.removeAll { currentTime - it.lastBroadcastTime > timeout }.also {
            notifyDataSetChanged() // Notify changes if any devices were removed
        }
    }
}
