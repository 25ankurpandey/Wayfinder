package com.wayfinder.wayfinder.domain.usecase

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.wayfinder.wayfinder.core.RerouteReason
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages rerouting logic with debouncing and throttling.
 * 
 * Key features:
 * - Debounces rapid location updates to avoid excessive reroute triggers
 * - Throttles API calls to prevent rate limiting
 * - Provides state flow for UI observation
 * - Calculates optimal reroute timing based on movement patterns
 */
class RerouteManager(
    private val deviationCalculator: RouteDeviationCalculator,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "RerouteManager"
        
        // Debounce delay before triggering reroute (ms)
        private const val DEBOUNCE_DELAY_MS = 2000L
        
        // Minimum time between reroutes (ms)
        private const val MIN_REROUTE_INTERVAL_MS = 10000L
        
        // Number of consecutive off-route checks before triggering
        private const val CONSECUTIVE_CHECKS_THRESHOLD = 3
        
        // Default off-route threshold in meters
        private const val OFF_ROUTE_THRESHOLD_METERS = 30.0
    }
    
    // Reroute state sealed class
    sealed class RerouteState {
        object Idle : RerouteState()
        object CheckingDeviation : RerouteState()
        data class OffRoute(val distance: Double, val reason: RerouteReason) : RerouteState()
        object Rerouting : RerouteState()
        data class RerouteComplete(val newRoutePoints: Int) : RerouteState()
        data class Failed(val message: String) : RerouteState()
    }
    
    private val _rerouteState = MutableStateFlow<RerouteState>(RerouteState.Idle)
    val rerouteState: StateFlow<RerouteState> = _rerouteState.asStateFlow()
    
    private var currentRoute: List<LatLng> = emptyList()
    private var lastRerouteTime: Long = 0L
    private var consecutiveOffRouteChecks: Int = 0
    private var debounceJob: Job? = null
    
    // Callback for when reroute is needed
    var onRerouteNeeded: ((LatLng, RerouteReason) -> Unit)? = null
    
    /**
     * Set the current route polyline for deviation checking.
     */
    fun setRoute(routePoints: List<LatLng>) {
        currentRoute = routePoints
        consecutiveOffRouteChecks = 0
        _rerouteState.value = RerouteState.Idle
        Log.d(TAG, "Route set with ${routePoints.size} points")
    }
    
    /**
     * Check if user has deviated from route.
     * Uses debouncing to avoid rapid reroute triggers.
     */
    fun checkDeviation(userLocation: LatLng) {
        // Cancel previous debounce job
        debounceJob?.cancel()
        
        debounceJob = coroutineScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            performDeviationCheck(userLocation)
        }
    }
    
    /**
     * Immediately check deviation without debounce.
     * Use sparingly - mainly for testing.
     */
    fun checkDeviationImmediate(userLocation: LatLng) {
        coroutineScope.launch {
            performDeviationCheck(userLocation)
        }
    }
    
    private suspend fun performDeviationCheck(userLocation: LatLng) {
        if (currentRoute.isEmpty()) {
            Log.w(TAG, "No route set, skipping deviation check")
            return
        }
        
        _rerouteState.value = RerouteState.CheckingDeviation
        
        // Use RouteDeviationCalculator.calculateDeviation() with stored route
        val result = deviationCalculator.calculateDeviation(
            userLocation = userLocation,
            routePolyline = currentRoute,
            toleranceMeters = OFF_ROUTE_THRESHOLD_METERS
        )
        
        if (!result.isOffRoute) {
            consecutiveOffRouteChecks = 0
            _rerouteState.value = RerouteState.Idle
            Log.d(TAG, "User on route, distance: ${result.deviationMeters}m")
        } else {
            consecutiveOffRouteChecks++
            Log.d(TAG, "User off route (check $consecutiveOffRouteChecks/$CONSECUTIVE_CHECKS_THRESHOLD), distance: ${result.deviationMeters}m")
            
            if (consecutiveOffRouteChecks >= CONSECUTIVE_CHECKS_THRESHOLD) {
                handleOffRoute(userLocation, result.deviationMeters)
            } else {
                _rerouteState.value = RerouteState.OffRoute(
                    result.deviationMeters, 
                    RerouteReason.USER_DEVIATION
                )
            }
        }
    }
    
    private fun handleOffRoute(userLocation: LatLng, deviationMeters: Double) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastReroute = currentTime - lastRerouteTime
        
        // Check throttle
        if (timeSinceLastReroute < MIN_REROUTE_INTERVAL_MS) {
            Log.d(TAG, "Reroute throttled, ${MIN_REROUTE_INTERVAL_MS - timeSinceLastReroute}ms remaining")
            _rerouteState.value = RerouteState.OffRoute(deviationMeters, RerouteReason.USER_DEVIATION)
            return
        }
        
        // Trigger reroute
        _rerouteState.value = RerouteState.Rerouting
        lastRerouteTime = currentTime
        consecutiveOffRouteChecks = 0
        
        Log.i(TAG, "Triggering reroute from ${userLocation.latitude}, ${userLocation.longitude}")
        onRerouteNeeded?.invoke(userLocation, RerouteReason.USER_DEVIATION)
    }
    
    /**
     * Called when a new route has been received after rerouting.
     */
    fun onRerouteComplete(newRoutePoints: List<LatLng>) {
        setRoute(newRoutePoints)
        _rerouteState.value = RerouteState.RerouteComplete(newRoutePoints.size)
        
        // Return to idle after brief delay
        coroutineScope.launch {
            delay(3000)
            if (_rerouteState.value is RerouteState.RerouteComplete) {
                _rerouteState.value = RerouteState.Idle
            }
        }
    }
    
    /**
     * Force a reroute due to traffic incident or road closure.
     */
    fun forceReroute(userLocation: LatLng, reason: RerouteReason) {
        val currentTime = System.currentTimeMillis()
        
        // Still apply throttle for forced reroutes
        if (currentTime - lastRerouteTime < MIN_REROUTE_INTERVAL_MS / 2) {
            Log.d(TAG, "Forced reroute throttled")
            return
        }
        
        _rerouteState.value = RerouteState.Rerouting
        lastRerouteTime = currentTime
        
        Log.i(TAG, "Forced reroute triggered: $reason")
        onRerouteNeeded?.invoke(userLocation, reason)
    }
    
    /**
     * Reset manager state.
     */
    fun reset() {
        debounceJob?.cancel()
        currentRoute = emptyList()
        consecutiveOffRouteChecks = 0
        lastRerouteTime = 0L
        _rerouteState.value = RerouteState.Idle
    }
    
    /**
     * Clean up resources.
     */
    fun dispose() {
        debounceJob?.cancel()
        coroutineScope.cancel()
    }
}
