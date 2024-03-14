package com.wayfinder.wayfinderar

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationService(private val context: Context) {
    private val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            Tasks.await(fusedLocationProviderClient.lastLocation)
        } catch (e: Exception) {
            // Handle exception (e.g., no location available)
            null
        }
    }
}
