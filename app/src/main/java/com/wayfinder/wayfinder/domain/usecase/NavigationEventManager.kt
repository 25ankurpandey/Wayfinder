package com.wayfinder.wayfinder.domain.usecase

import android.util.Log
import com.wayfinder.wayfinder.core.ConnectionState
import com.wayfinder.wayfinder.core.NavigationState
import com.wayfinder.wayfinder.domain.model.*
import com.wayfinder.wayfinder.data.network.tcp.TcpConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages navigation lifecycle events and coordinates between
 * MapFragment (route selection) and Quest (AR display).
 * 
 * This replaces the one-shot data sending pattern with a proper
 * event-driven architecture:
 * 
 * 1. startNavigation() - Sends route data, triggers NavigationStarted
 * 2. updateProgress() - Sends status updates during navigation
 * 3. reroute() - Sends new route when user deviates
 * 4. endNavigation() - Sends end message, triggers NavigationEnded
 */
class NavigationEventManager(
    private val connectionManager: TcpConnectionManager
) {
    companion object {
        private const val TAG = "NavigationEventManager"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Navigation state observable
    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()
    
    // Current route data
    private var currentWaypoints: List<UnityCoord>? = null
    private var currentMetadata: RouteMetadata? = null
    
    /**
     * Event listeners for navigation lifecycle.
     */
    interface NavigationEventListener {
        fun onNavigationStarted(waypoints: List<UnityCoord>, metadata: RouteMetadata?)
        fun onNavigationUpdated(state: NavigationState)
        fun onRerouted(waypoints: List<UnityCoord>, reason: String)
        fun onNavigationEnded(reason: String)
    }
    
    private var eventListener: NavigationEventListener? = null
    
    fun setEventListener(listener: NavigationEventListener?) {
        eventListener = listener
    }
    
    /**
     * Start navigation with waypoints.
     * Sends route to Quest and triggers NavigationStarted event.
     */
    fun startNavigation(
        waypoints: List<UnityCoord>,
        metadata: RouteMetadata?,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!connectionManager.isConnected()) {
            Log.e(TAG, "Cannot start navigation: not connected")
            onResult(false, "Not connected to Quest")
            return
        }
        
        currentWaypoints = waypoints
        currentMetadata = metadata
        _navigationState.value = NavigationState.WaitingForRoute
        
        scope.launch {
            try {
                val result = connectionManager.sendRoute(waypoints, metadata, isReroute = false)
                
                when (result) {
                    is TcpConnectionManager.SendResult.Success -> {
                        _navigationState.value = NavigationState.Navigating(
                            distanceRemainingMeters = metadata?.distanceRemainingMeters?.toDouble() ?: 0.0,
                            etaSeconds = metadata?.etaSeconds ?: 0
                        )
                        eventListener?.onNavigationStarted(waypoints, metadata)
                        Log.d(TAG, "Navigation started with ${waypoints.size} waypoints")
                        onResult(true, "Navigation started")
                    }
                    is TcpConnectionManager.SendResult.Error -> {
                        _navigationState.value = NavigationState.Error(result.message)
                        Log.e(TAG, "Failed to start navigation: ${result.message}")
                        onResult(false, result.message)
                    }
                }
            } catch (e: Exception) {
                _navigationState.value = NavigationState.Error(e.message ?: "Unknown error")
                Log.e(TAG, "Exception starting navigation", e)
                onResult(false, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Update navigation progress.
     * Called periodically during active navigation.
     */
    fun updateProgress(distanceRemainingMeters: Double, etaSeconds: Int) {
        val currentState = _navigationState.value
        if (currentState !is NavigationState.Navigating && currentState !is NavigationState.OffRoute) {
            return // Not in active navigation
        }
        
        val newState = NavigationState.Navigating(distanceRemainingMeters, etaSeconds)
        _navigationState.value = newState
        eventListener?.onNavigationUpdated(newState)
        
        scope.launch {
            connectionManager.sendStatus("navigating")
        }
    }
    
    /**
     * Mark user as off-route.
     * Called when deviation is detected.
     */
    fun markOffRoute(deviationMeters: Double) {
        _navigationState.value = NavigationState.OffRoute(
            deviationMeters = deviationMeters,
            timeSinceDeviationMs = 0L
        )
        eventListener?.onNavigationUpdated(_navigationState.value)
        
        scope.launch {
            connectionManager.sendStatus("off_route")
        }
    }
    
    /**
     * Reroute with new waypoints.
     * Called when a new route is calculated after deviation.
     */
    fun reroute(
        waypoints: List<UnityCoord>,
        metadata: RouteMetadata?,
        reason: String,
        onResult: (Boolean, String) -> Unit
    ) {
        _navigationState.value = NavigationState.Rerouting
        currentWaypoints = waypoints
        currentMetadata = metadata
        
        scope.launch {
            try {
                val result = connectionManager.sendRoute(waypoints, metadata, isReroute = true)
                
                when (result) {
                    is TcpConnectionManager.SendResult.Success -> {
                        _navigationState.value = NavigationState.Navigating(
                            distanceRemainingMeters = metadata?.distanceRemainingMeters?.toDouble() ?: 0.0,
                            etaSeconds = metadata?.etaSeconds ?: 0
                        )
                        eventListener?.onRerouted(waypoints, reason)
                        Log.d(TAG, "Rerouted with ${waypoints.size} waypoints, reason: $reason")
                        onResult(true, "Rerouted successfully")
                    }
                    is TcpConnectionManager.SendResult.Error -> {
                        _navigationState.value = NavigationState.Error(result.message)
                        Log.e(TAG, "Failed to reroute: ${result.message}")
                        onResult(false, result.message)
                    }
                }
            } catch (e: Exception) {
                _navigationState.value = NavigationState.Error(e.message ?: "Unknown error")
                Log.e(TAG, "Exception during reroute", e)
                onResult(false, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * End navigation session.
     * Sends end message to Quest and resets state.
     */
    fun endNavigation(reason: String, onResult: ((Boolean, String) -> Unit)? = null) {
        scope.launch {
            try {
                val result = connectionManager.sendEnd(reason)
                
                when (result) {
                    is TcpConnectionManager.SendResult.Success -> {
                        val finalState = when (reason) {
                            "arrived" -> NavigationState.Arrived
                            "cancelled" -> NavigationState.Cancelled
                            else -> NavigationState.Idle
                        }
                        _navigationState.value = finalState
                        eventListener?.onNavigationEnded(reason)
                        
                        // Clear current route
                        currentWaypoints = null
                        currentMetadata = null
                        
                        Log.d(TAG, "Navigation ended: $reason")
                        onResult?.invoke(true, "Navigation ended")
                    }
                    is TcpConnectionManager.SendResult.Error -> {
                        Log.e(TAG, "Failed to send end message: ${result.message}")
                        // Still end locally even if send fails
                        _navigationState.value = NavigationState.Cancelled
                        currentWaypoints = null
                        currentMetadata = null
                        onResult?.invoke(false, result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception ending navigation", e)
                _navigationState.value = NavigationState.Idle
                currentWaypoints = null
                currentMetadata = null
                onResult?.invoke(false, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Check if navigation is active.
     */
    fun isNavigating(): Boolean = _navigationState.value.isActive()
    
    /**
     * Get current waypoints if in navigation.
     */
    fun getCurrentWaypoints(): List<UnityCoord>? = currentWaypoints
    
    /**
     * Reset to idle state.
     * Called when connection is lost or errors occur.
     */
    fun reset() {
        _navigationState.value = NavigationState.Idle
        currentWaypoints = null
        currentMetadata = null
    }
}
