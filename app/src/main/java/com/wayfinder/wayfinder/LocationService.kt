package com.wayfinder.wayfinderar

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationService(private val context: Context) {
    private val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            Tasks.await(fusedLocationProviderClient.lastLocation)
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(locationUpdateInterval: Long, fastestLocationInterval: Long, locationUpdateFunction: (LocationResult) -> Unit) {
        val locationRequest = LocationRequest.Builder(locationUpdateInterval)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationUpdateFunction(locationResult)
            }
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback!!, null /* Looper */)
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationProviderClient.removeLocationUpdates(it) }
    }
}