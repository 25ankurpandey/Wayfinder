package com.wayfinder.wayfinder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import com.wayfinder.wayfinder.MapConstants.SELECTED_POLYLINE_WIDTH
import com.wayfinder.wayfinder.databinding.FragmentNavigationBinding
import com.wayfinder.wayfinderar.LocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NavigationFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private lateinit var locationService: LocationService
    private var selectedRouteJson: String? = null
    private var userLocationMarker: Marker? = null
    private var currentPolyline: Polyline? = null
    private var remainingPath: MutableList<LatLng> = mutableListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        locationService = LocationService(requireContext())
        selectedRouteJson = arguments?.getString("selectedRouteJson")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMapFragment()
    }

    private fun setupMapFragment() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.navigation_map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        initializeLocationUpdates()
    }

    private fun initializeLocationUpdates() {
        locationService.startLocationUpdates(1000L, 500L) { locationResult ->
            val newLocation = locationResult.lastLocation?.let { LatLng(it.latitude, locationResult.lastLocation!!.longitude) }
            if (newLocation != null) {
                updateLocationOnMap(newLocation)
            }
        }
    }

    private fun initializeRemainingPath() {
        // This should be called after getting the route and user's initial location
        selectedRouteJson?.let {
            val route = Gson().fromJson(it, Route::class.java)
            remainingPath = PolyUtil.decode(route.overview_polyline.points).toMutableList()
        }
    }

    private fun findClosestPointOnPath(currentLocation: LatLng, path: List<LatLng>): Int {
        var minIndex = -1
        var minDistance = Double.MAX_VALUE
        for (i in path.indices) {
            val distance = SphericalUtil.computeDistanceBetween(currentLocation, path[i])
            if (distance < minDistance) {
                minDistance = distance
                minIndex = i
            }
        }
        return minIndex
    }


    private fun updateLocationOnMap(newLocation: LatLng) {
        // Ensure the remaining path is initialized
        if (remainingPath.isEmpty()) {
            initializeRemainingPath()
        }

        // Find the closest point on the remaining path to the new location
        val closestPointIndex = findClosestPointOnPath(newLocation, remainingPath)
        if (closestPointIndex > 0) {
            // Trim the traveled part of the path
            remainingPath = remainingPath.subList(closestPointIndex, remainingPath.size).toMutableList()
        }

        // Update user's location marker
        userLocationMarker?.remove() // Remove the old marker
        userLocationMarker = mMap.addMarker(MarkerOptions()
            .position(newLocation)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_user_location)))

        // Redraw the polyline
        currentPolyline?.remove()
        if (remainingPath.isNotEmpty()) {
            currentPolyline = mMap.addPolyline(PolylineOptions()
                .addAll(remainingPath)
                .color(ContextCompat.getColor(requireContext(), R.color.selectedPolylineColor))
                .width(SELECTED_POLYLINE_WIDTH.toFloat()))
        }

        // Adjust the camera
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, getZoomLevelForOneKilometerCoverage(newLocation)))
    }

    private suspend fun setupMapWithRoute(newLocation: LatLng) {
        selectedRouteJson?.let {
            val route = Gson().fromJson(it, Route::class.java)
            val decodedPath = PolyUtil.decode(route.overview_polyline.points)

            // If a polyline exists, remove it
            currentPolyline?.remove()

            // Create a new polyline that starts from the user's current location
            currentPolyline = mMap.addPolyline(PolylineOptions()
                .add(newLocation)
                .addAll(decodedPath)
                .color(ContextCompat.getColor(requireContext(), R.color.selectedPolylineColor))
                .width(SELECTED_POLYLINE_WIDTH.toFloat()))

            // Focus camera on the user's current location
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, getZoomLevelForOneKilometerCoverage(newLocation)))
        }
    }

    // This method calculates the appropriate zoom level to cover approximately 1 kilometer
    private fun getZoomLevelForOneKilometerCoverage(center: LatLng): Float {
        return 18f // Adjust based on testing and requirements
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationService.stopLocationUpdates() // Ensure to stop location updates
        _binding = null
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
