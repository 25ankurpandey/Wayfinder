package com.wayfinder.wayfinder.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Unity coordinate for sending to Quest.
 * Uses scaled coordinates (1 unit = 100 real meters).
 */
data class UnityCoord(
    val x: Float,
    val z: Float
)

/**
 * Route metadata sent with route/reroute messages.
 */
data class RouteMetadata(
    @SerializedName("distance_remaining_m")
    val distanceRemainingMeters: Float,
    
    @SerializedName("eta_seconds")
    val etaSeconds: Int,
    
    @SerializedName("reason")
    val reason: String? = null
)

/**
 * Base interface for all navigation messages sent to Quest.
 */
interface NavigationMessage {
    val type: String
}

/**
 * Route message containing waypoints.
 * Sent for both initial route and reroutes.
 */
data class RouteMessage(
    override val type: String = "route",
    val waypoints: List<UnityCoord>,
    val metadata: RouteMetadata? = null
) : NavigationMessage

/**
 * Status message for navigation state changes.
 */
data class StatusMessage(
    override val type: String = "status",
    val status: String,
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : NavigationMessage

/**
 * Alert message for traffic, delays, etc.
 */
data class AlertMessage(
    override val type: String = "alert",
    
    @SerializedName("alert_type")
    val alertType: String,
    
    @SerializedName("delay_seconds")
    val delaySeconds: Int,
    
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : NavigationMessage

/**
 * Heartbeat message for connection keepalive.
 */
data class HeartbeatMessage(
    override val type: String = "heartbeat",
    val timestamp: Long = System.currentTimeMillis()
) : NavigationMessage

/**
 * End message when navigation session ends.
 */
data class EndMessage(
    override val type: String = "end",
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
) : NavigationMessage

/**
 * Navigation started event message.
 */
data class NavigationStartedMessage(
    override val type: String = "navigation_started",
    val waypoints: List<UnityCoord>,
    val metadata: RouteMetadata? = null,
    val timestamp: Long = System.currentTimeMillis()
) : NavigationMessage

/**
 * Navigation ended event message.
 */
data class NavigationEndedMessage(
    override val type: String = "navigation_ended",
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
) : NavigationMessage

/**
 * Reasons for navigation ending.
 * Matches Unity's NavigationEndReason enum.
 */
enum class NavigationEndReason(val value: String) {
    ARRIVED("arrived"),
    USER_CANCELLED("cancelled"),
    CONNECTION_LOST("connection_lost"),
    ERROR("error");
    
    companion object {
        fun fromString(value: String?): NavigationEndReason {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: ERROR
        }
    }
}
