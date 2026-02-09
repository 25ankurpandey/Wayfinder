package com.wayfinder.wayfinder.data.api

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.wayfinder.wayfinder.core.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DirectionsResult(
    val geocoded_waypoints: List<GeocodedWaypoint>,
    val routes: List<Route>,
    val status: String
)

data class GeocodedWaypoint(
    val geocoder_status: String,
    val place_id: String,
    val types: List<String>,
    val partial_match: Boolean? = null // Optional field
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
    val traffic_speed_entry: List<Any>, // Placeholder for actual traffic speed entry model if required
    val via_waypoint: List<Any> // Placeholder for actual via waypoint model if required
)

data class Step(
    val distance: Distance,
    val duration: Duration,
    val end_location: Location,
    val html_instructions: String,
    val polyline: Polyline,
    val start_location: Location,
    val travel_mode: String,
    val maneuver: String? // Optional field
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
    val value: Int // Distance in meters
)

data class Duration(
    val text: String,
    val value: Int // Duration in seconds
)


class DirectionsService(private val context: Context) {
    private val client = OkHttpClient()

    suspend fun getDirections(start: LatLng, end: LatLng, mode: String): DirectionsResult? = withContext(Dispatchers.IO) {
        val apiKey = Constants.GOOGLE_MAPS_API_KEY
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${start.latitude},${start.longitude}&" +
                "destination=${end.latitude},${end.longitude}&" +
                "mode=$mode&" +
                "alternatives=true&" +
                "key=${apiKey}"
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            body?.let {
                return@withContext Gson().fromJson(it, DirectionsResult::class.java)
            }
        } catch (e: Exception) {
            Log.e("DirectionsService", "Error fetching directions", e)
        }
        return@withContext null
    }
}
