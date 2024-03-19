package com.wayfinder.wayfinder

import java.io.PrintWriter
import java.net.Socket

class TcpClient {
    fun sendData(host: String, port: Int, data: String, onResult: (Boolean) -> Unit) {
        Thread {
            try {
                Socket(host, port).use { socket ->
                    PrintWriter(socket.getOutputStream(), true).use { out ->
                        out.println(data)
                        onResult(true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }.start()
    }
}
