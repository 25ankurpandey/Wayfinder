package com.wayfinder.wayfinder

import java.io.PrintWriter
import java.net.Socket

class TcpClient {
    companion object {
        const val CONNECTION_TIMEOUT = 5000
    }

    fun sendData(host: String, port: Int, data: String, deviceName: String?, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), CONNECTION_TIMEOUT)
                    PrintWriter(socket.getOutputStream(), true).use { out ->
                        out.println(data)
                        onResult(true, "Data sent successfully to $deviceName.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Failed to send data to $deviceName")
            }
        }.start()
    }
}
