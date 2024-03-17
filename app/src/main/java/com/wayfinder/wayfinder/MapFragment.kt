package com.wayfinder.wayfinder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.Polyline
import com.wayfinder.wayfinderar.LocationService
import kotlinx.coroutines.launch
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import com.wayfinder.wayfinder.MapConstants.SELECTED_POLYLINE_WIDTH
import com.wayfinder.wayfinder.MapConstants.UNSELECTED_POLYLINE_WIDTH
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
    private lateinit var startServerButton: ImageButton
    private lateinit var navigatePhoneButton: ImageButton
    private var polylineToRouteMap: MutableMap<Polyline, Route> = mutableMapOf()
    private var selectedPolyline: Polyline? = null
    private var routeInfoMarkerToRouteMap: MutableMap<Marker, Route> = mutableMapOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeMapFragment()
        setupButtons(view)
        setupAutocompleteFragment()
    }
    private fun setupDirectionsButton() {
        directionsButton.setOnClickListener {
            // Toggle the visibility of walking and driving buttons
            val areButtonsVisible = walkingButton.visibility == View.VISIBLE
            toggleWalkingDrivingButtons(!areButtonsVisible) // Show if hidden, hide if shown
        }
    }

    private fun initializeMapFragment() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        locationService = LocationService(requireContext())
    }

    private fun setupButtons(view: View) {
        directionsButton = view.findViewById(R.id.directions_button)
        startNavigationButton = view.findViewById(R.id.start_navigation_button)
        myLocationButton = view.findViewById(R.id.my_location_button)
        walkingButton = view.findViewById(R.id.walking_button)
        drivingButton = view.findViewById(R.id.driving_button)
        startServerButton = view.findViewById(R.id.start_server_button)
        navigatePhoneButton = view.findViewById(R.id.navigate_phone_button)

        myLocationButton.setOnClickListener { moveToCurrentLocation() }
        directionsButton.setOnClickListener { toggleRouteOptionsVisibility() }
        walkingButton.setOnClickListener { fetchRoute("walking") }
        drivingButton.setOnClickListener { fetchRoute("driving") }
        startNavigationButton.setOnClickListener { startNavigation() }
        startServerButton.setOnClickListener { startNavigation() }
        navigatePhoneButton.setOnClickListener { startPhoneNavigation() }
        startServerButton.setOnClickListener { startDeviceDiscovery() }

        myLocationButton.visibility = View.VISIBLE
        directionsButton.visibility = View.GONE
        walkingButton.visibility = View.GONE
        drivingButton.visibility = View.GONE
        startNavigationButton.visibility = View.GONE
        startServerButton.visibility = View.GONE
        navigatePhoneButton.visibility = View.GONE
    }

    private fun startDeviceDiscovery() {
        TODO("Not yet implemented")
    }

    private fun startPhoneNavigation() {
        TODO("Not yet implemented")
    }

    private fun toggleRouteOptionsVisibility() {
        val visibility = if (walkingButton.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        walkingButton.visibility = visibility
        drivingButton.visibility = visibility
    }

    private fun fetchRoute(mode: String) {
        destinationLatLng?.let { destination ->
            if (currentLocationLatLng == null) {
                fetchCurrentLocationAndDisplayRoutes(destination, mode)
            } else {
                fetchAndDisplayRoutes(currentLocationLatLng!!, destination, mode)
            }
        }
    }

    private fun startNavigation() {
        Log.d("MapFragment", "Starting navigation")
        val visibility = if (startServerButton.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        startServerButton.visibility = visibility
        navigatePhoneButton.visibility = visibility
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setupMap()
    }

    private fun setupMap() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = false
            fetchLocationAndDisplay()
        }
        mMap.setOnMarkerClickListener { marker ->
            routeInfoMarkerToRouteMap[marker]?.let { route ->
                selectRoute(route)
            }
            true
        }

        mMap.setOnPolylineClickListener { polyline ->
            selectPolyline(polyline)
        }
    }

    private fun selectRoute(route: Route) {
        val polyline = polylineToRouteMap.entries.find { it.value == route }?.key
        polyline?.let { selectPolyline(it) }
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
                val result = directionsService.getDirections(start, end, mode)
                var isFirstRoute = true // Flag to identify the first (default-selected) route

                result?.routes?.forEachIndexed { index, route ->
                    val (totalDistance, totalTime) = calculateTotalDistanceAndTime(route)
                    Log.d("RouteInfo", "Route $index: Distance = $totalDistance, Time = $totalTime")
                    showRouteInfoMarker(totalDistance, totalTime, route, isFirstRoute)

                    val shadowPolylineOptions = PolylineOptions()
                        .addAll(PolyUtil.decode(route.overview_polyline.points))
                        .width(SELECTED_POLYLINE_WIDTH + 5)
                        .color(Color.argb(80, 0, 0, 0))
                        .zIndex(0f)

                    val polylineOptions = PolylineOptions()
                        .addAll(PolyUtil.decode(route.overview_polyline.points))
                        .color(ContextCompat.getColor(requireContext(), if (isFirstRoute) R.color.selectedPolylineColor else R.color.unselectedPolylineColor))
                        .width(if (isFirstRoute) SELECTED_POLYLINE_WIDTH else UNSELECTED_POLYLINE_WIDTH)
                        .clickable(true)
                        .zIndex(if (isFirstRoute) 1f else 0f) // Set zIndex to 1 for the first route to render it on top

                    mMap.addPolyline(shadowPolylineOptions)
                    val polyline = mMap.addPolyline(polylineOptions)
                    polyline.tag = route

                    if (isFirstRoute) {
                        // Log the default-selected route
                        Log.d("SelectedRoute", "Default-selected route: ${Gson().toJson(route)}")
                        selectedPolyline = polyline
                        isFirstRoute = false // Reset the flag after the first route
                    }

                    polylineToRouteMap[polyline] = route
                }

                if (result?.routes?.isNotEmpty() == true) {
                    adjustCameraToRouteAndShowButton(PolyUtil.decode(result.routes.first().overview_polyline.points))
                }
            } catch (e: Exception) {
                Log.e("MapFragment", "Error fetching routes: ${e.message}")
            }
        }
    }

    private fun calculateTotalDistanceAndTime(route: Route): Pair<String, String> {
        var totalDistanceMeters = 0
        var totalDurationSeconds = 0

        route.legs.forEach { leg ->
            totalDistanceMeters += leg.distance.value
            totalDurationSeconds += leg.duration.value
        }

        val totalDistanceKm = totalDistanceMeters / 1000.0
        val totalDurationHours = totalDurationSeconds / 3600
        val totalDurationMinutes = (totalDurationSeconds % 3600) / 60

        val distanceText = String.format("%.2f km", totalDistanceKm)
        val durationText = if (totalDurationHours > 0) {
            "${totalDurationHours}h ${totalDurationMinutes}min"
        } else {
            "${totalDurationMinutes}min"
        }

        return Pair(distanceText, durationText)
    }

    private fun calculateMidpoint(route: Route): LatLng {
        val decodedPath = PolyUtil.decode(route.overview_polyline.points)
        return decodedPath[decodedPath.size / 2] // Get the midpoint for simplicity
    }

    private fun generateCustomMarkerView(distance: String, duration: String, isSelected: Boolean): Bitmap {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_route_info_window, null)
        val routeInfoTextView = view.findViewById<TextView>(R.id.route_info)
        routeInfoTextView.text = "$distance, $duration"
        if (isSelected) {
            view.setBackgroundResource(R.drawable.route_info_box_selected)
        }

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        return bitmap
    }

    private fun showRouteInfoMarker(distance: String, duration: String, route: Route, isSelected: Boolean) {
        val icon = BitmapDescriptorFactory.fromBitmap(generateCustomMarkerView(distance, duration, isSelected))
        val location = calculateMidpoint(route)
        val markerOptions = MarkerOptions().position(location).icon(icon)
        val marker: Marker? = mMap.addMarker(markerOptions)
        marker?.let {
            routeInfoMarkerToRouteMap[it] = route
        }
    }


    private fun selectPolyline(polyline: Polyline) {
        // Deselect all polylines and markers first
        for ((poly, _) in polylineToRouteMap) {
            poly.color = ContextCompat.getColor(requireContext(), R.color.unselectedPolylineColor)
            poly.width = UNSELECTED_POLYLINE_WIDTH
            poly.zIndex = 0f
        }
        // Reset marker icons to unselected state
        for ((marker, _) in routeInfoMarkerToRouteMap) {
            val route = routeInfoMarkerToRouteMap[marker]
            val iconBitmap = generateCustomMarkerView(
                calculateTotalDistanceAndTime(route!!).first,
                calculateTotalDistanceAndTime(route).second,
                isSelected = false
            )
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(iconBitmap))
        }

        // Select the current polyline
        polyline.color = ContextCompat.getColor(requireContext(), R.color.selectedPolylineColor)
        polyline.width = SELECTED_POLYLINE_WIDTH
        polyline.zIndex = 1f

        // Find and update the marker icon for the selected route
        val selectedRoute = polylineToRouteMap[polyline]
        val selectedMarker = routeInfoMarkerToRouteMap.entries.find { it.value == selectedRoute }?.key
        selectedMarker?.let { marker ->
            val iconBitmap = generateCustomMarkerView(
                calculateTotalDistanceAndTime(selectedRoute!!).first,
                calculateTotalDistanceAndTime(selectedRoute).second,
                isSelected = true
            )
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(iconBitmap))
        }

        // Keep track of the selected polyline
        selectedPolyline = polyline

        // Log the selected route for debugging
        Log.d("SelectedRoute", "Selected route: ${Gson().toJson(selectedRoute)}")
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
}