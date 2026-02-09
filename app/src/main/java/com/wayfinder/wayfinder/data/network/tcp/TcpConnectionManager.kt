package com.wayfinder.wayfinder.data.network.tcp

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.wayfinder.wayfinder.core.ConnectionState
import com.wayfinder.wayfinder.core.NetworkConstants
import com.wayfinder.wayfinder.domain.model.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Manages persistent TCP connection with Quest device.
 * 
 * Singleton pattern for app-wide access. Handles:
 * - Connection lifecycle with automatic reconnection
 * - Message sending (routes, status, alerts)
 * - Heartbeat keepalive
 * - LiveData observation for UI binding
 */
class TcpConnectionManager private constructor(context: Context) {
    
    companion object {
        private const val TAG = "TcpConnectionManager"
        
        @Volatile
        private var INSTANCE: TcpConnectionManager? = null
        
        /**
         * Get singleton instance.
         */
        fun getInstance(context: Context): TcpConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TcpConnectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val gson = Gson()
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    
    private var connectionScope: CoroutineScope? = null
    private var heartbeatJob: Job? = null
    private var readerJob: Job? = null
    
    private var currentIpAddress: String? = null
    private var currentDeviceName: String? = null
    
    // LiveData for UI observation
    private val _connectionState = MutableLiveData<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private var reconnectAttempts = 0
    
    /**
     * Result of a send operation.
     */
    sealed class SendResult {
        data object Success : SendResult()
        data class Error(val message: String) : SendResult()
    }
    
    /**
     * Connect to Quest device by IP address and name.
     */
    fun connect(ipAddress: String, deviceName: String) {
        currentIpAddress = ipAddress
        currentDeviceName = deviceName
        reconnectAttempts = 0
        
        connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        connectionScope?.launch {
            performConnect(ipAddress, deviceName)
        }
    }
    
    /**
     * Perform the actual connection.
     */
    private suspend fun performConnect(ipAddress: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
        // Clean up any existing connection
        closeConnection()
        
        _connectionState.postValue(ConnectionState.Connecting(deviceName))
        
        try {
            socket = Socket().apply {
                soTimeout = NetworkConstants.READ_TIMEOUT_MS.toInt()
                connect(
                    InetSocketAddress(ipAddress, NetworkConstants.TCP_PORT),
                    NetworkConstants.CONNECTION_TIMEOUT_MS.toInt()
                )
            }
            
            writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            
            _connectionState.postValue(ConnectionState.Connected(
                deviceName = deviceName,
                ipAddress = ipAddress
            ))
            
            // Start heartbeat
            startHeartbeat()
            
            Log.d(TAG, "Connected to $deviceName at $ipAddress")
            true
        } catch (e: java.net.ConnectException) {
            // Connection refused - Quest app not running or port not open
            Log.e(TAG, "Connection refused: ${e.message}")
            _connectionState.postValue(ConnectionState.Failed("Connection refused - is Quest app running?"))
            false
        } catch (e: java.net.SocketTimeoutException) {
            // Timeout - Quest not reachable
            Log.e(TAG, "Connection timed out: ${e.message}")
            _connectionState.postValue(ConnectionState.Failed("Connection timed out - check Quest is on same network"))
            false
        } catch (e: java.net.NoRouteToHostException) {
            // Network unreachable
            Log.e(TAG, "No route to host: ${e.message}")
            _connectionState.postValue(ConnectionState.Failed("Device unreachable - check network connection"))
            false
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed: ${e.message}")
            _connectionState.postValue(ConnectionState.Failed("Unable to connect to device"))
            false
        }
    }
    
    /**
     * Disconnect from Quest device.
     */
    fun disconnect() {
        connectionScope?.cancel()
        connectionScope = null
        closeConnection()
        
        currentIpAddress = null
        currentDeviceName = null
        
        _connectionState.postValue(ConnectionState.Disconnected)
        Log.d(TAG, "Disconnected")
    }
    
    private fun closeConnection() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        readerJob?.cancel()
        readerJob = null
        
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing connection: ${e.message}")
        }
        
        writer = null
        reader = null
        socket = null
    }
    
    /**
     * Send raw JSON data to Quest.
     * @deprecated Use NavigationEventManager.startNavigation() instead for proper lifecycle events.
     */
    @Deprecated(
        message = "Use NavigationEventManager.startNavigation() for proper lifecycle events",
        level = DeprecationLevel.WARNING
    )
    fun sendData(data: String, callback: (Boolean, String) -> Unit) {
        connectionScope?.launch {
            val result = sendRawData(data)
            withContext(Dispatchers.Main) {
                when (result) {
                    is SendResult.Success -> callback(true, "Data sent successfully")
                    is SendResult.Error -> callback(false, result.message)
                }
            }
        }
    }
    
    /**
     * Send raw string data.
     */
    private suspend fun sendRawData(data: String): SendResult = withContext(Dispatchers.IO) {
        val currentSocket = socket
        val currentWriter = writer
        
        if (currentSocket == null || !currentSocket.isConnected || currentWriter == null) {
            return@withContext SendResult.Error("Not connected")
        }
        
        try {
            currentWriter.write(data)
            currentWriter.newLine()
            currentWriter.flush()
            
            Log.d(TAG, "Sent data: ${data.take(100)}...")
            SendResult.Success
        } catch (e: IOException) {
            Log.e(TAG, "Send failed: ${e.message}")
            handleConnectionError()
            SendResult.Error(e.message ?: "Send failed")
        }
    }
    
    /**
     * Send a navigation message to Quest.
     */
    suspend fun sendMessage(message: NavigationMessage): SendResult = withContext(Dispatchers.IO) {
        val currentSocket = socket
        val currentWriter = writer
        
        if (currentSocket == null || !currentSocket.isConnected || currentWriter == null) {
            return@withContext SendResult.Error("Not connected")
        }
        
        try {
            val json = gson.toJson(message)
            currentWriter.write(json)
            currentWriter.newLine()
            currentWriter.flush()
            
            Log.d(TAG, "Sent: ${message.type}")
            SendResult.Success
        } catch (e: IOException) {
            Log.e(TAG, "Send failed: ${e.message}")
            handleConnectionError()
            SendResult.Error(e.message ?: "Send failed")
        }
    }
    
    /**
     * Send a route to Quest.
     */
    suspend fun sendRoute(
        waypoints: List<UnityCoord>,
        metadata: RouteMetadata?,
        isReroute: Boolean = false
    ): SendResult {
        val message = RouteMessage(
            type = if (isReroute) "reroute" else "route",
            waypoints = waypoints,
            metadata = metadata
        )
        return sendMessage(message)
    }
    
    /**
     * Send a status update to Quest (simple string).
     */
    suspend fun sendStatus(status: String): SendResult {
        val message = StatusMessage(status = status)
        return sendMessage(message)
    }
    
    /**
     * Send a status message object to Quest.
     */
    suspend fun sendStatus(statusMessage: StatusMessage): SendResult {
        return sendMessage(statusMessage)
    }
    
    /**
     * Send an alert to Quest.
     */
    suspend fun sendAlert(alertType: String, delaySeconds: Int, message: String? = null): SendResult {
        val alertMessage = AlertMessage(
            alertType = alertType,
            delaySeconds = delaySeconds,
            message = message
        )
        return sendMessage(alertMessage)
    }
    
    /**
     * Send end navigation message to Quest.
     */
    suspend fun sendEnd(reason: String): SendResult {
        val message = EndMessage(reason = reason)
        return sendMessage(message)
    }
    
    /**
     * Start heartbeat timer.
     */
    private fun startHeartbeat() {
        heartbeatJob = connectionScope?.launch {
            while (isActive) {
                delay(NetworkConstants.HEARTBEAT_INTERVAL_MS)
                val currentWriter = writer
                if (currentWriter != null && socket?.isConnected == true) {
                    try {
                        val heartbeat = HeartbeatMessage()
                        currentWriter.write(gson.toJson(heartbeat))
                        currentWriter.newLine()
                        currentWriter.flush()
                        Log.v(TAG, "Heartbeat sent")
                    } catch (e: IOException) {
                        Log.w(TAG, "Heartbeat failed: ${e.message}")
                        handleConnectionError()
                        break
                    }
                }
            }
        }
    }
    
    /**
     * Handle connection errors and attempt reconnection.
     */
    private fun handleConnectionError() {
        val ip = currentIpAddress ?: return
        val name = currentDeviceName ?: return
        
        if (reconnectAttempts >= NetworkConstants.MAX_RECONNECT_ATTEMPTS) {
            _connectionState.postValue(ConnectionState.Failed("Connection lost"))
            closeConnection()
            return
        }
        
        reconnectAttempts++
        _connectionState.postValue(ConnectionState.Reconnecting(name, reconnectAttempts))
        
        connectionScope?.launch {
            delay(NetworkConstants.RECONNECT_DELAY_MS)
            performConnect(ip, name)
        }
    }
    
    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean {
        return socket?.isConnected == true && _connectionState.value is ConnectionState.Connected
    }
    
    /**
     * Get current device name if connected.
     */
    fun getConnectedDeviceName(): String? {
        return (_connectionState.value as? ConnectionState.Connected)?.deviceName
    }
    
    /**
     * Get current IP address if connected.
     */
    fun getConnectedIpAddress(): String? {
        return (_connectionState.value as? ConnectionState.Connected)?.ipAddress
    }
}
