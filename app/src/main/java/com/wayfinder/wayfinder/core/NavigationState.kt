package com.wayfinder.wayfinder.core

/**
 * Navigation session states.
 * Uses sealed class for type-safe state handling.
 */
sealed class NavigationState {
    /** No active navigation session. */
    data object Idle : NavigationState()
    
    /** Waiting for route data from API. */
    data object WaitingForRoute : NavigationState()
    
    /** Actively navigating with valid route. */
    data class Navigating(
        val distanceRemainingMeters: Double = 0.0,
        val etaSeconds: Int = 0
    ) : NavigationState()
    
    /** Detected off-route, preparing to reroute. */
    data class OffRoute(
        val deviationMeters: Double,
        val timeSinceDeviationMs: Long
    ) : NavigationState()
    
    /** Requesting new route from API. */
    data object Rerouting : NavigationState()
    
    /** User has arrived at destination. */
    data object Arrived : NavigationState()
    
    /** Navigation paused by user. */
    data object Paused : NavigationState()
    
    /** Navigation cancelled by user. */
    data object Cancelled : NavigationState()
    
    /** Error state with message. */
    data class Error(val message: String) : NavigationState()
    
    /**
     * Get string representation for sending to Quest.
     */
    fun toStatusString(): String = when (this) {
        is Idle -> "idle"
        is WaitingForRoute -> "waiting_for_route"
        is Navigating -> "navigating"
        is OffRoute -> "off_route"
        is Rerouting -> "rerouting"
        is Arrived -> "arrived"
        is Paused -> "paused"
        is Cancelled -> "cancelled"
        is Error -> "error"
    }
    
    /**
     * Check if navigation is actively in progress.
     */
    fun isActive(): Boolean = this is Navigating || this is OffRoute || this is Rerouting
}

/**
 * Connection states for TCP connection with Quest.
 */
sealed class ConnectionState {
    /** Not connected to any device. */
    data object Disconnected : ConnectionState()
    
    /** Attempting to connect to device. */
    data class Connecting(val deviceName: String) : ConnectionState()
    
    /** Successfully connected to device. */
    data class Connected(
        val deviceName: String,
        val ipAddress: String
    ) : ConnectionState()
    
    /** Connection lost, attempting to reconnect. */
    data class Reconnecting(
        val deviceName: String,
        val attemptNumber: Int
    ) : ConnectionState()
    
    /** Connection failed permanently. */
    data class Failed(val reason: String) : ConnectionState()
    
    fun isConnected(): Boolean = this is Connected
}
