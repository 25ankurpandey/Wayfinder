package com.wayfinder.wayfinder.data.network.udp

import android.util.Log
import com.wayfinder.wayfinder.core.DeviceInfo
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class UdpDiscovery(private val onDeviceDiscovered: (DeviceInfo) -> Unit) {
    private val listenPort = 8888
    private var socket: DatagramSocket? = null

    fun startDiscovery() {
        Thread {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(listenPort))
                }
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (!socket!!.isClosed) {
                    socket!!.receive(packet)
                    val receivedString = String(packet.data, 0, packet.length)
                    Log.d("UDP Discovery", "Received: $receivedString")
                    if (receivedString.startsWith("Quest3_Presence:")) {
                        // Correctly split the string by ":" and expect three parts: prefix, IP, and Name
                        val parts = receivedString.split(":").map { it.trim() }
                        if (parts.size >= 3) {
                            val ipAddress = parts[1] // IP Address
                            val deviceName = parts[2] // Device Name
                            onDeviceDiscovered(DeviceInfo(ipAddress, deviceName, System.currentTimeMillis()))
                        }
                    }
                }
                // socket.close() // Removed to keep listening; close elsewhere as needed
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stopDiscovery() {
        socket?.close() // Close the socket to stop discovery
    }
}

