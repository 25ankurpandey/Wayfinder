package com.wayfinder.wayfinder

import java.io.PrintWriter
import java.net.Socket

class TcpClient {
    fun sendData(host: String, port: Int, data: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                Socket(host, port).use { socket ->
                    PrintWriter(socket.getOutputStream(), true).use { out ->
                        out.println(data)
                        // Assuming the transmission is always successful if no exceptions are thrown
                        onResult(true, "Data sent successfully.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Include a more descriptive message about the error
                onResult(false, "Failed to send data: ${e.localizedMessage}")
            }
        }.start()
    }
}
