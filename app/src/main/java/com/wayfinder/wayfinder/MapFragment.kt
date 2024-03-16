package com.wayfinder.wayfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.wayfinder.wayfinderar.LocationService
import kotlinx.coroutines.launch
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var locationService: LocationService
    private var currentLocationLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null
    private lateinit var directionsButton: ImageButton
    private lateinit var startNavigationButton: ImageButton
    private lateinit var myLocationButton: ImageButton
    private lateinit var walkingButton: ImageButton
    private lateinit var drivingButton: ImageButton
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        locationService = LocationService(requireContext())

        walkingButton = view.findViewById(R.id.walking_button)
        drivingButton = view.findViewById(R.id.driving_button)
        directionsButton = view.findViewById(R.id.directions_button)
        startNavigationButton = view.findViewById(R.id.start_navigation_button)
        myLocationButton = view.findViewById(R.id.my_location_button)

        setupMyLocationButton()
        setupAutocompleteFragment()
        setupDirectionsButton()
        setupStartNavigationButton()
        setupWalkingButton()
        setupDrivingButton()

        myLocationButton.visibility = View.VISIBLE
        directionsButton.visibility = View.GONE
        walkingButton.visibility = View.GONE
        drivingButton.visibility = View.GONE
        startNavigationButton.visibility = View.GONE
    }

    private fun setupDirectionsButton() {
        directionsButton.setOnClickListener {
            // Toggle the visibility of walking and driving buttons
            val areButtonsVisible = walkingButton.visibility == View.VISIBLE
            toggleWalkingDrivingButtons(!areButtonsVisible) // Show if hidden, hide if shown
        }
    }


    private fun setupMyLocationButton() {
        myLocationButton.setOnClickListener {
            moveToCurrentLocation()
        }
    }

    private fun setupWalkingButton() {
        walkingButton.setOnClickListener {
            destinationLatLng?.let { destination ->
                // If current location is not known, fetch it first
                if (currentLocationLatLng == null) {
                    fetchCurrentLocationAndDisplayRoutes(destination, "walking")
                } else {
                    fetchAndDisplayRoutes(currentLocationLatLng!!, destination, "walking")
                }
            }
        }
    }

    private fun setupDrivingButton() {
        drivingButton.setOnClickListener {
            destinationLatLng?.let { destination ->
                // If current location is not known, fetch it first
                if (currentLocationLatLng == null) {
                    fetchCurrentLocationAndDisplayRoutes(destination, "driving")
                } else {
                    fetchAndDisplayRoutes(currentLocationLatLng!!, destination, "driving")
                }
            }
        }
    }



    private fun setupStartNavigationButton() {
        startNavigationButton.visibility = View.GONE // Initially hide the start navigation button
        startNavigationButton.setOnClickListener {
            startNavigationButton.visibility = View.GONE // Initially hide the start navigation button
            Log.d("MapFragment", "Starting navigation")
            // Implement the navigation start logic here
        }
    }

    private fun moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndDisplay()
        } else {
            // Reminder: Handle permission request as needed.
        }
    }

    private fun toggleWalkingDrivingButtons(show: Boolean) {
        walkingButton.visibility = if (show) View.VISIBLE else View.GONE
        drivingButton.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun hideAllButtonsAndShowStart() {
        directionsButton.visibility = View.GONE
        myLocationButton.visibility = View.GONE
        walkingButton.visibility = View.GONE
        drivingButton.visibility = View.GONE
        startNavigationButton.visibility = View.VISIBLE
    }


    private fun fetchLocationAndDisplay() {
        lifecycleScope.launch {
            val lastLocation = locationService.getLastLocation()
            lastLocation?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                currentLocationLatLng = currentLatLng // Store the current location
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
            } ?: Log.d("MapFragment", "Last location is null")
        }
    }


    private fun setupAutocompleteFragment() {
        val autocompleteFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                destinationLatLng = place.latLng
                mMap.clear()
                place.latLng?.let {
                    mMap.addMarker(MarkerOptions().position(it).title(place.name))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))

                    // Reset button visibilities for a new search
                    myLocationButton.visibility = View.VISIBLE // Show my location button
                    directionsButton.visibility = View.VISIBLE // Show directions button for the new search
                    startNavigationButton.visibility = View.GONE // Hide start navigation button until needed
                    walkingButton.visibility = View.GONE // Ensure walking button is hidden
                    drivingButton.visibility = View.GONE // Ensure driving button is hidden
                }
            }

            override fun onError(status: Status) {
                Log.i("Maps", "An error occurred: $status")
            }
        })
    }

    private fun fetchAndDisplayRoutes(start: LatLng, end: LatLng, mode: String) {
        lifecycleScope.launch {
            try {
                val directionsService = DirectionsService(requireContext())
                val result = directionsService.getDirections(start, end, mode) // Update the method to accept mode
                result?.routes?.firstOrNull()?.let { route ->
                    val polylineOptions = PolylineOptions().addAll(PolyUtil.decode(route.overview_polyline.points))
                    mMap.clear()
                    mMap.addPolyline(polylineOptions)
                    adjustCameraToRouteAndShowButton(PolyUtil.decode(route.overview_polyline.points))
                }
            } catch (e: Exception) {
                Log.e("MapFragment", "Error fetching routes: ${e.message}")
            }
        }
    }


    private suspend fun adjustCameraToRouteAndShowButton(routePoints: List<LatLng>) {
        val deferredCompletion = CompletableDeferred<Unit>()

        val boundsBuilder = LatLngBounds.Builder()
        routePoints.forEach { point ->
            boundsBuilder.include(point)
        }
        val bounds = boundsBuilder.build()

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                deferredCompletion.complete(Unit) // Mark the deferred as complete when animation finishes
            }

            override fun onCancel() {
                deferredCompletion.complete(Unit) // Also complete it in case of cancellation
            }
        })

        deferredCompletion.await() // Await the completion of the camera animation

        // Ensure UI updates happen on the main thread
        withContext(Dispatchers.Main) {
            hideAllButtonsAndShowStart()
        }
    }

    private fun fetchCurrentLocationAndDisplayRoutes(destination: LatLng, mode: String) {
        lifecycleScope.launch {
            if (currentLocationLatLng == null) {
                val lastLocation = locationService.getLastLocation()
                lastLocation?.let {
                    currentLocationLatLng = LatLng(it.latitude, it.longitude)
                } ?: Log.d("MapFragment", "Failed to fetch current location.")
            }

            currentLocationLatLng?.let { currentLocation ->
                fetchAndDisplayRoutes(currentLocation, destination, mode)
            } ?: Log.d("MapFragment", "Current location is null after fetch attempt.")
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = false
            fetchLocationAndDisplay()
        }
    }
}
