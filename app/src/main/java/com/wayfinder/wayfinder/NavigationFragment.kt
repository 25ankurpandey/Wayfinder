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
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import com.wayfinder.wayfinder.MapConstants.SELECTED_POLYLINE_WIDTH
import com.wayfinder.wayfinder.databinding.FragmentNavigationBinding
import com.wayfinder.wayfinderar.LocationService
import kotlinx.coroutines.launch

class NavigationFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private lateinit var locationService: LocationService
    private var selectedRouteJson: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        locationService = LocationService(requireContext())
        selectedRouteJson = arguments?.getString("selectedRouteJson") // Changed to accept the whole route JSON
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
        fetchLocationAndDisplay()
        setupMapWithRoute()
    }

    private fun setupMapWithRoute() {
        lifecycleScope.launch {
            val lastLocation = locationService.getLastLocation()
            lastLocation?.let { location ->
                val currentLatLng = LatLng(location.latitude, location.longitude)
                // Add a custom marker at the user's current location
                mMap.addMarker(MarkerOptions()
                    .position(currentLatLng)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_user_location))) // Ensure you have this drawable in your resources

                selectedRouteJson?.let {
                    val route = Gson().fromJson(it, Route::class.java) // Assuming Route is your route class with a proper overview_polyline property
                    val decodedPath = PolyUtil.decode(route.overview_polyline.points)

                    // Prepend the current location to the decoded path
                    val fullPath = mutableListOf(currentLatLng).apply {
                        addAll(decodedPath)
                    }

                    val polylineOptions = PolylineOptions()
                        .addAll(fullPath)
                        .color(ContextCompat.getColor(requireContext(), R.color.selectedPolylineColor))
                        .width(SELECTED_POLYLINE_WIDTH.toFloat())
                    mMap.addPolyline(polylineOptions)

                    // Focus camera on the user's current location with appropriate zoom
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, getZoomLevelForOneKilometerCoverage(currentLatLng)))

                    // Add marker for the destination
                    mMap.addMarker(MarkerOptions().position(decodedPath.last()).title("Destination"))
                }
            } ?: Log.d("NavigationFragment", "Last location is null")
        }
    }

    private fun fetchLocationAndDisplay() {
        lifecycleScope.launch {
            val lastLocation = locationService.getLastLocation()
            lastLocation?.let { location ->
                val currentLatLng = LatLng(location.latitude, location.longitude)

                // Add a custom marker at the user's current location
                mMap.addMarker(MarkerOptions()
                    .position(currentLatLng)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_user_location))) // Make sure you have this drawable

                // Apply padding to the map view. Adjust only the bottom padding.
                val padding = getMapPaddingBottom()
                mMap.setPadding(0, 0, 0, padding)

                // Animate camera to the user's location with appropriate zoom
                // Zoom level 15f is arbitrary, adjust as necessary
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 19f))
            } ?: Log.d("NavigationFragment", "Last location is null")
        }
    }

    // This method is an approximation and may need adjustments based on testing
    private fun getZoomLevelForOneKilometerCoverage(center: LatLng): Float {
        // Rough approximation - you might need to adjust the base zoom level based on testing
        val baseZoomLevel = 18f // This zoom level is a starting point and might show approximately 1km in urban areas
        return baseZoomLevel
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun getMapPaddingBottom(): Int {
        // Adjust this value if you want to change the distance from the bottom
        val distanceFromBottomDp = 200f
        return dpToPx(requireContext(), distanceFromBottomDp)
    }
}
