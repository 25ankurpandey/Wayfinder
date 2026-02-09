package com.wayfinder.wayfinder.data.api.model

/**
 * Google Directions API response models.
 * Extracted from DirectionsService for better separation.
 */

data class DirectionsResult(
    val geocoded_waypoints: List<GeocodedWaypoint>,
    val routes: List<Route>,
    val status: String
)

data class GeocodedWaypoint(
    val geocoder_status: String,
    val place_id: String,
    val types: List<String>,
    val partial_match: Boolean? = null
)

data class Route(
    val bounds: Bounds,
    val legs: List<Leg>,
    val overview_polyline: Polyline,
    val summary: String,
    val warnings: List<String>,
    val waypoint_order: List<Int>
)

data class Bounds(
    val northeast: Location,
    val southwest: Location
)

data class Leg(
    val distance: Distance,
    val duration: Duration,
    val end_address: String,
    val end_location: Location,
    val start_address: String,
    val start_location: Location,
    val steps: List<Step>,
    val traffic_speed_entry: List<Any>,
    val via_waypoint: List<Any>
)

data class Step(
    val distance: Distance,
    val duration: Duration,
    val end_location: Location,
    val html_instructions: String,
    val polyline: Polyline,
    val start_location: Location,
    val travel_mode: String,
    val maneuver: String? = null
)

data class Location(
    val lat: Double,
    val lng: Double
)

data class Polyline(
    val points: String
)

data class Distance(
    val text: String,
    val value: Int
)

data class Duration(
    val text: String,
    val value: Int
)
