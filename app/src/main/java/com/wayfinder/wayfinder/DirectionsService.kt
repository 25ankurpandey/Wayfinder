package com.wayfinder.wayfinder

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DirectionsResult(
    val routes: List<Route>
)

data class Route(
    val legs: List<Leg>,
    val overview_polyline: Polyline
)

data class Leg(
    val distance: Distance,
    val duration: Duration
)

data class Distance(
    val text: String,
    val value: Int // Distance in meters
)

data class Duration(
    val text: String,
    val value: Int // Duration in seconds
)


data class Polyline(
    val points: String
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
