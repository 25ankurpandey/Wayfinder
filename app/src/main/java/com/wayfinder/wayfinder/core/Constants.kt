package com.wayfinder.wayfinder.core

import android.graphics.Color

/**
 * Legacy Constants - DEPRECATED.
 * Use core.NetworkConstants and core.NavigationConstants instead.
 * 
 * This file is kept for backward compatibility with:
 * - MapConstants for polyline styling
 * - GOOGLE_MAPS_API_KEY for Places API
 */
object Constants {
    const val GOOGLE_MAPS_API_KEY = "AIzaSyB1geXvFhJO6jg2tE698_iJRSOlRF0Tr_g"
    
    // TCP_PORT moved to core.NetworkConstants
    // Use NetworkConstants.TCP_PORT instead
    @Deprecated("Use NetworkConstants.TCP_PORT", ReplaceWith("NetworkConstants.TCP_PORT", "com.wayfinder.wayfinder.core.NetworkConstants"))
    const val TCP_PORT = 9876  // Updated to match NetworkConstants
}

/**
 * Map styling constants.
 */
object MapConstants {
    const val SELECTED_POLYLINE_WIDTH = 25f
    const val UNSELECTED_POLYLINE_WIDTH = 25f
}

/**
 * Network configuration constants for TCP/UDP communication.
 * IMPORTANT: Must stay in sync with Unity's NetworkConstants.cs
 */
object NetworkConstants {
    /** TCP port for Quest communication. Must match Unity TcpPort (9898). */
    const val TCP_PORT = 9898
    
    /** UDP broadcast port for device discovery. */
    const val UDP_BROADCAST_PORT = 9877
    
    /** Connection timeout in milliseconds. */
    const val CONNECTION_TIMEOUT_MS = 10000
    
    /** Socket read timeout in milliseconds. */
    const val READ_TIMEOUT_MS = 30000L
    
    /** Heartbeat interval in milliseconds. */
    const val HEARTBEAT_INTERVAL_MS = 5000L
    
    /** Maximum reconnection attempts. */
    const val MAX_RECONNECT_ATTEMPTS = 5
    
    /** Delay between reconnection attempts in milliseconds. */
    const val RECONNECT_DELAY_MS = 2000L
    
    /** Socket read/write timeout in milliseconds. */
    const val SOCKET_TIMEOUT_MS = 30000
}

/**
 * Navigation behavior constants.
 */
object NavigationConstants {
    /** Threshold for off-route detection in meters. */
    const val OFF_ROUTE_THRESHOLD_METERS = 30.0
    
    /** Tolerance for considering user on path in meters. */
    const val ON_PATH_TOLERANCE_METERS = 15.0
    
    /** Default tolerance for off-route detection in meters. */
    const val DEFAULT_OFF_ROUTE_TOLERANCE_METERS = 30.0
    
    /** Minimum distance to snap to route in meters. */
    const val SNAP_TO_ROUTE_THRESHOLD_METERS = 15.0
    
    /** ETA update frequency in milliseconds. */
    const val ETA_UPDATE_INTERVAL_MS = 30000L
    
    /** Minimum distance change to trigger ETA recalculation in meters. */
    const val ETA_RECALC_DISTANCE_THRESHOLD_METERS = 100.0
}

