package com.wayfinder.wayfinderar

import android.util.Log
import org.json.JSONObject
import kotlin.math.*

class RouteConverter(private val scaleFactor: Double = 0.01) {
    var lastConvertedRoute: List<UnityCoord>? = null
        private set

    fun convertJsonRouteToUnityCoords(jsonResponse: String, currentLocation: LatLng) {
        Log.d("88888888888888888888888888888888888",jsonResponse)
        val jsonObject = JSONObject(jsonResponse)
        val (decodedPolyline, stepsTotalDistance) = decodeStepsPolyline(jsonObject)
        Log.d("Total distance", stepsTotalDistance.toString())
//        val decodedOverviewPolyline = decodeOverviewPolyline(jsonObject)
        val unityCoords = convertToUnityCoords(decodedPolyline, currentLocation)
        Log.d("Unity coords",unityCoords.toString())
        Log.d("Unity coords array size",unityCoords.size.toString())

        lastConvertedRoute = unityCoords
    }

    private fun decodeStepsPolyline(routeJson: JSONObject): Pair<List<LatLng>, Int> {
        val allPolylinePoints = mutableListOf<LatLng>()
        var totalDistance = 0

        val legs = routeJson.getJSONArray("legs")
        for (j in 0 until legs.length()) {
            val steps = legs.getJSONObject(j).getJSONArray("steps")
            for (k in 0 until steps.length()) {
                val step = steps.getJSONObject(k)
                val polyline = step.getJSONObject("polyline").getString("points")
                allPolylinePoints.addAll(decodePolyline(polyline))
                totalDistance += step.getJSONObject("distance").getInt("value")
            }
        }
        return Pair(allPolylinePoints, totalDistance)
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val pLat = lat.toDouble() / 1E5
            val pLng = lng.toDouble() / 1E5
            poly.add(LatLng(pLat, pLng))
        }

        return poly
    }

    private fun decodeOverviewPolyline(routeJson: JSONObject): List<LatLng> {
        val overviewPolyline = routeJson.getJSONObject("overview_polyline")
        val points = overviewPolyline.getString("points")
        val decodedPolyline = decodePolyline(points)
        Log.d("Overview polyline", decodedPolyline.toString())
        return decodedPolyline
    }

    private fun convertToUnityCoords(polyline: List<LatLng>, currentLocation: LatLng): List<UnityCoord> {
        val unityCoords = mutableListOf<UnityCoord>()
        unityCoords.add(UnityCoord(0f, 0f)) // Origin in Unity space

        polyline.forEach { point ->
            val distanceNorth = haversineDistance(currentLocation, LatLng(point.latitude, currentLocation.longitude))
            val distanceEast = haversineDistance(currentLocation, LatLng(currentLocation.latitude, point.longitude))

            val northSouthMultiplier = if (point.latitude > currentLocation.latitude) 1 else -1
            val eastWestMultiplier = if (point.longitude > currentLocation.longitude) 1 else -1

            val x = distanceEast * eastWestMultiplier * scaleFactor
            val z = distanceNorth * northSouthMultiplier * scaleFactor

            unityCoords.add(UnityCoord(x.toFloat(), z.toFloat()))
        }
        return unityCoords
    }
    companion object {
        fun haversineDistance(start: LatLng, end: LatLng): Double {
            // Implementation of the Haversine formula to calculate the distance in meters
            val earthRadius = 6371000.0 // Radius of the earth in meters

            val latDistance = Math.toRadians(end.latitude - start.latitude)
            val lonDistance = Math.toRadians(end.longitude - start.longitude)
            val a = sin(latDistance / 2).pow(2) + cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) * sin(lonDistance / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return earthRadius * c
        }
    }

    data class UnityCoord(val x: Float, val z: Float)
    data class LatLng(val latitude: Double, val longitude: Double)
}
