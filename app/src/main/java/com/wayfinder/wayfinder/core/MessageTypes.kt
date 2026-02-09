package com.wayfinder.wayfinder.core

/**
 * Message types for TCP communication with Quest.
 * Matches Unity's MessageType enum.
 */
enum class MessageType(val value: String) {
    /** Initial route with waypoints. */
    ROUTE("route"),
    
    /** Rerouted path with new waypoints. */
    REROUTE("reroute"),
    
    /** Navigation status update. */
    STATUS("status"),
    
    /** Traffic/delay alert. */
    ALERT("alert"),
    
    /** Connection keepalive. */
    HEARTBEAT("heartbeat"),
    
    /** Navigation session ended. */
    END("end");
    
    companion object {
        fun fromString(value: String?): MessageType {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: ROUTE
        }
    }
}

/**
 * Reasons for rerouting the navigation path.
 * Matches Unity's RerouteReason enum.
 */
enum class RerouteReason(val value: String) {
    USER_DEVIATION("user_deviation"),
    TRAFFIC("traffic"),
    CONSTRUCTION("construction"),
    ROAD_CLOSURE("road_closure"),
    BETTER_ROUTE("better_route"),
    USER_REQUEST("user_request");
    
    companion object {
        fun fromString(value: String?): RerouteReason {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: USER_DEVIATION
        }
    }
}

/**
 * Reasons for navigation ending.
 * Matches Unity's NavigationEndReason enum.
 */
enum class NavigationEndReason(val value: String) {
    ARRIVED("arrived"),
    CANCELLED("cancelled"),
    CONNECTION_LOST("connection_lost"),
    ERROR("error");
    
    companion object {
        fun fromString(value: String?): NavigationEndReason {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: ERROR
        }
    }
}
