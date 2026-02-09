package com.wayfinder.wayfinder.presentation.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import com.wayfinder.wayfinder.MainActivity
import com.wayfinder.wayfinder.R
import com.wayfinder.wayfinder.core.ConnectionState
import com.wayfinder.wayfinder.core.MessageType
import com.wayfinder.wayfinder.core.RerouteReason
import com.wayfinder.wayfinder.domain.model.RouteMessage
import com.wayfinder.wayfinder.domain.model.StatusMessage
import com.wayfinder.wayfinder.domain.model.UnityCoord
import com.wayfinder.wayfinder.domain.usecase.RerouteManager
import com.wayfinder.wayfinder.domain.usecase.RouteDeviationCalculator
import com.wayfinder.wayfinder.data.network.tcp.TcpConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for active navigation sessions.
 * 
 * Handles:
 * - Persistent notification during navigation
 * - Connection management with Quest device
 * - Route deviation monitoring
 * - Reroute coordination
 * - Keeps service alive during background navigation
 */
class NavigationSessionService : Service() {

    companion object {
        private const val TAG = "NavigationSessionService"
        private const val NOTIFICATION_CHANNEL_ID = "wayfinder_navigation"
        private const val NOTIFICATION_ID = 1001
        
        // Intent actions
        const val ACTION_START_NAVIGATION = "com.wayfinder.START_NAVIGATION"
        const val ACTION_STOP_NAVIGATION = "com.wayfinder.STOP_NAVIGATION"
        const val ACTION_UPDATE_ROUTE = "com.wayfinder.UPDATE_ROUTE"
        
        // Intent extras
        const val EXTRA_DESTINATION_NAME = "destination_name"
        const val EXTRA_ROUTE_POINTS = "route_points"
    }
    
    // Navigation state
    sealed class NavigationSessionState {
        object Idle : NavigationSessionState()
        object Connecting : NavigationSessionState()
        data class Active(val destinationName: String, val distanceRemaining: Float) : NavigationSessionState()
        object Rerouting : NavigationSessionState()
        object Arrived : NavigationSessionState()
        data class Failed(val message: String) : NavigationSessionState()
    }
    
    private val binder = NavigationBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _sessionState = MutableStateFlow<NavigationSessionState>(NavigationSessionState.Idle)
    val sessionState: StateFlow<NavigationSessionState> = _sessionState.asStateFlow()
    
    // Dependencies
    private lateinit var connectionManager: TcpConnectionManager
    private lateinit var rerouteManager: RerouteManager
    private lateinit var deviationCalculator: RouteDeviationCalculator
    
    // Current navigation data
    private var currentRoute: List<LatLng> = emptyList()
    private var destinationName: String = ""
    
    inner class NavigationBinder : Binder() {
        fun getService(): NavigationSessionService = this@NavigationSessionService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NavigationSessionService created")
        
        createNotificationChannel()
        initializeDependencies()
    }
    
    private fun initializeDependencies() {
        connectionManager = TcpConnectionManager.getInstance(this)
        deviationCalculator = RouteDeviationCalculator()
        rerouteManager = RerouteManager(deviationCalculator, serviceScope)
        
        // Set up reroute callback
        rerouteManager.onRerouteNeeded = { userLocation, reason ->
            handleRerouteNeeded(userLocation, reason)
        }
        
        // Observe connection state
        connectionManager.connectionState.observeForever { state ->
            handleConnectionStateChange(state)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_NAVIGATION -> {
                val destination = intent.getStringExtra(EXTRA_DESTINATION_NAME) ?: "Destination"
                startNavigation(destination)
            }
            ACTION_STOP_NAVIGATION -> {
                stopNavigation()
            }
            ACTION_UPDATE_ROUTE -> {
                // Route update via intent (for parcelable route data)
            }
        }
        
        return START_STICKY
    }
    
    private fun startNavigation(destination: String) {
        Log.i(TAG, "Starting navigation to: $destination")
        destinationName = destination
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification("Navigating to $destination"))
        
        _sessionState.value = NavigationSessionState.Connecting
        
        // Check if already connected
        val currentState = connectionManager.connectionState.value
        if (currentState is ConnectionState.Connected) {
            _sessionState.value = NavigationSessionState.Active(destination, 0f)
            sendNavigationStartStatus()
        }
    }
    
    /**
     * Send current route to Quest device.
     */
    fun sendRouteToQuest(routePoints: List<LatLng>) {
        currentRoute = routePoints
        rerouteManager.setRoute(routePoints)
        
        // Convert to UnityCoord list
        val waypoints = routePoints.map { latLng ->
            UnityCoord(
                x = latLng.latitude.toFloat(),
                z = latLng.longitude.toFloat()
            )
        }
        
        // Use coroutine for suspend function - use correct sendRoute signature
        serviceScope.launch {
            connectionManager.sendRoute(waypoints, null, false)
            Log.d(TAG, "Sent route with ${waypoints.size} points to Quest")
        }
    }
    
    /**
     * Update user location for deviation checking.
     */
    fun updateUserLocation(location: LatLng) {
        if (currentRoute.isNotEmpty()) {
            rerouteManager.checkDeviation(location)
        }
    }
    
    private fun handleRerouteNeeded(userLocation: LatLng, reason: RerouteReason) {
        Log.i(TAG, "Reroute needed: $reason at $userLocation")
        _sessionState.value = NavigationSessionState.Rerouting
        
        // Notify Quest that we're rerouting
        val statusMessage = StatusMessage(
            type = MessageType.STATUS.value,
            status = "rerouting",
            details = "Recalculating route..."
        )
        serviceScope.launch {
            connectionManager.sendStatus(statusMessage)
        }
        
        updateNotification("Recalculating route...")
        
        // TODO: Call Google Directions API for new route
        // For now, just emit event for activity to handle
    }
    
    /**
     * Called when a new route is received after rerouting.
     */
    fun onNewRouteReceived(newRoutePoints: List<LatLng>) {
        rerouteManager.onRerouteComplete(newRoutePoints)
        sendRouteToQuest(newRoutePoints)
        
        _sessionState.value = NavigationSessionState.Active(destinationName, 0f)
        updateNotification("Navigating to $destinationName")
    }
    
    private fun handleConnectionStateChange(state: ConnectionState) {
        when (state) {
            is ConnectionState.Connected -> {
                if (_sessionState.value is NavigationSessionState.Connecting) {
                    _sessionState.value = NavigationSessionState.Active(destinationName, 0f)
                }
            }
            is ConnectionState.Disconnected -> {
                if (_sessionState.value is NavigationSessionState.Active) {
                    _sessionState.value = NavigationSessionState.Failed("Connection lost")
                }
            }
            is ConnectionState.Failed -> {
                if (_sessionState.value is NavigationSessionState.Active) {
                    _sessionState.value = NavigationSessionState.Failed(state.reason)
                }
            }
            is ConnectionState.Reconnecting -> {
                // Keep active state during reconnection attempts
                Log.d(TAG, "Reconnecting attempt ${state.attemptNumber}")
            }
            is ConnectionState.Connecting -> {
                // Already connecting
            }
        }
    }
    
    private fun sendNavigationStartStatus() {
        val statusMessage = StatusMessage(
            type = MessageType.STATUS.value,
            status = "navigation_started",
            details = "Navigation to $destinationName started"
        )
        serviceScope.launch {
            connectionManager.sendStatus(statusMessage)
        }
    }
    
    fun stopNavigation() {
        Log.i(TAG, "Stopping navigation")
        
        // Notify Quest
        val statusMessage = StatusMessage(
            type = MessageType.STATUS.value,
            status = "navigation_ended",
            details = "Navigation stopped"
        )
        serviceScope.launch {
            connectionManager.sendStatus(statusMessage)
        }
        
        rerouteManager.reset()
        currentRoute = emptyList()
        _sessionState.value = NavigationSessionState.Idle
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    fun markArrived() {
        Log.i(TAG, "Destination reached!")
        _sessionState.value = NavigationSessionState.Arrived
        
        val statusMessage = StatusMessage(
            type = MessageType.STATUS.value,
            status = "arrived",
            details = "You have arrived at $destinationName"
        )
        serviceScope.launch {
            connectionManager.sendStatus(statusMessage)
        }
        
        updateNotification("Arrived at $destinationName")
        
        // Auto-stop after delay
        serviceScope.launch {
            delay(30000)
            stopNavigation()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Navigation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active navigation notifications"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NavigationSessionService::class.java).apply {
                action = ACTION_STOP_NAVIGATION
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Wayfinder Navigation")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_navigation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_disconnect, "Stop", stopIntent)
            .build()
    }
    
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NavigationSessionService destroyed")
        rerouteManager.dispose()
        serviceScope.cancel()
    }
}
