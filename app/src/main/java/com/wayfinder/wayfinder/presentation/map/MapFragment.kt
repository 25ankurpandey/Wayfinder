package com.wayfinder.wayfinder.presentation.map

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import com.wayfinder.wayfinder.R
import com.wayfinder.wayfinder.core.ConnectionState
import com.wayfinder.wayfinder.core.MapConstants
import com.wayfinder.wayfinder.core.MapConstants.SELECTED_POLYLINE_WIDTH
import com.wayfinder.wayfinder.core.MapConstants.UNSELECTED_POLYLINE_WIDTH
import com.wayfinder.wayfinder.core.NetworkConstants
import com.wayfinder.wayfinder.data.api.DirectionsService
import com.wayfinder.wayfinder.data.api.Route
import com.wayfinder.wayfinder.data.converter.RouteConverter
import com.wayfinder.wayfinder.data.location.LocationService
import com.wayfinder.wayfinder.data.network.tcp.TcpConnectionManager
import com.wayfinder.wayfinder.presentation.device.DeviceDiscoveryDialog
import com.wayfinder.wayfinder.presentation.navigation.NavigationFragment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main map fragment for route selection and navigation.
 * 
 * Flow:
 * 1. Shows user's current location on map
 * 2. User searches for destination via autocomplete
 * 3. User selects travel mode (walk/drive)
 * 4. Multiple route options displayed with time/distance labels
 * 5. User selects route and clicks "Start Navigation"
 * 6. Device discovery bottom sheet opens
 * 7. User selects Quest device
 * 8. Route data sent via persistent TCP connection
 */
class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var locationService: LocationService
    private val routeConverter = RouteConverter()
    private var currentLocationLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null
    private var destinationName: String? = null
    private lateinit var directionsButton: ImageButton
    private lateinit var startNavigationButton: ImageButton
    private lateinit var myLocationButton: ImageButton
    private lateinit var walkingButton: ImageButton
    private lateinit var drivingButton: ImageButton
    private lateinit var startServerButton: ImageButton
    private lateinit var navigatePhoneButton: ImageButton
    
    // Navigation header views
    private lateinit var autocompleteContainer: View
    private lateinit var navigationHeader: View
    private lateinit var navigationDestinationText: TextView
    private lateinit var navigationEtaText: TextView
    private lateinit var navigationConnectionDot: View
    private lateinit var exitNavigationButton: ImageButton
    
    // State
    private var isInNavigationMode: Boolean = false
    
    private var polylineToRouteMap: MutableMap<Polyline, Route> = mutableMapOf()
    private var selectedPolyline: Polyline? = null
    private var routeInfoMarkerToRouteMap: MutableMap<Marker, Route> = mutableMapOf()
    private var selectedRouteJson: String? = null
    private var routeConversionCompleted = false
    private var progressDialog: Dialog? = null
    
    // Use persistent connection manager instead of legacy TcpClient
    private val connectionManager by lazy { 
        TcpConnectionManager.getInstance(requireContext()) 
    }
    
    // Navigation event manager for proper started/ended lifecycle
    private val navigationEventManager by lazy {
        com.wayfinder.wayfinder.domain.usecase.NavigationEventManager(connectionManager)
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeMapFragment()
        setupButtons(view)
        setupNavigationHeader(view)
        setupAutocompleteFragment()
        observeConnectionState()
    }
    
    private fun setupDirectionsButton() {
        directionsButton.setOnClickListener {
            val areButtonsVisible = walkingButton.visibility == View.VISIBLE
            toggleWalkingDrivingButtons(!areButtonsVisible)
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
        navigatePhoneButton.setOnClickListener { startPhoneNavigation() }
        startServerButton.setOnClickListener { showDeviceDiscoveryAndConvertRoute() }

        myLocationButton.visibility = View.VISIBLE
        directionsButton.visibility = View.GONE
        walkingButton.visibility = View.GONE
        drivingButton.visibility = View.GONE
        startNavigationButton.visibility = View.GONE
        startServerButton.visibility = View.GONE
        navigatePhoneButton.visibility = View.GONE
    }
    
    /**
     * Initialize navigation header views and exit button.
     */
    private fun setupNavigationHeader(view: View) {
        autocompleteContainer = view.findViewById(R.id.autocomplete_fragment_container)
        navigationHeader = view.findViewById(R.id.navigation_header)
        navigationDestinationText = view.findViewById(R.id.navigation_destination_text)
        navigationEtaText = view.findViewById(R.id.navigation_eta_text)
        navigationConnectionDot = view.findViewById(R.id.navigation_connection_dot)
        exitNavigationButton = view.findViewById(R.id.exit_navigation_button)
        
        exitNavigationButton.setOnClickListener {
            exitNavigationMode()
        }
    }
    
    /**
     * Reset map to initial state - clears routes, markers, destination.
     * Called when user clears the search or exits navigation.
     */
    private fun resetMapToInitialState() {
        // Clear map
        if (::mMap.isInitialized) {
            mMap.clear()
        }
        
        // Clear route data
        polylineToRouteMap.clear()
        routeInfoMarkerToRouteMap.clear()
        selectedPolyline = null
        selectedRouteJson = null
        routeConversionCompleted = false
        destinationLatLng = null
        destinationName = null
        
        // Reset UI to initial state
        myLocationButton.visibility = View.VISIBLE
        directionsButton.visibility = View.GONE
        walkingButton.visibility = View.GONE
        drivingButton.visibility = View.GONE
        startNavigationButton.visibility = View.GONE
        startServerButton.visibility = View.GONE
        navigatePhoneButton.visibility = View.GONE
        
        // Show search bar, hide navigation header
        autocompleteContainer.visibility = View.VISIBLE
        navigationHeader.visibility = View.GONE
        isInNavigationMode = false
        
        // Move to current location
        moveToCurrentLocation()
        
        Log.d("MapFragment", "Map reset to initial state")
    }
    
    /**
     * Enter navigation mode - hide search bar, show navigation header.
     */
    private fun enterNavigationMode(destination: String, eta: String) {
        isInNavigationMode = true
        
        // Hide search bar, show navigation header
        autocompleteContainer.visibility = View.GONE
        navigationHeader.visibility = View.VISIBLE
        
        // Update navigation header
        navigationDestinationText.text = destination
        navigationEtaText.text = "ETA: $eta"
        
        // Hide all route selection buttons
        directionsButton.visibility = View.GONE
        walkingButton.visibility = View.GONE
        drivingButton.visibility = View.GONE
        startNavigationButton.visibility = View.GONE
        startServerButton.visibility = View.GONE
        navigatePhoneButton.visibility = View.GONE
        myLocationButton.visibility = View.GONE  // Hide during navigation
        
        Log.d("MapFragment", "Entered navigation mode for: $destination")
    }
    
    /**
     * Exit navigation mode - end navigation session but KEEP connection.
     * Returns to route selection state with destination preserved.
     */
    private fun exitNavigationMode() {
        // Send end navigation message to Quest (but stay connected!)
        if (connectionManager.isConnected()) {
            navigationEventManager.endNavigation(
                com.wayfinder.wayfinder.domain.model.NavigationEndReason.USER_CANCELLED.value
            ) { _, _ -> }
        }
        
        // Exit navigation mode state
        isInNavigationMode = false
        
        // Show search bar with destination preserved, hide navigation header
        autocompleteContainer.visibility = View.VISIBLE
        navigationHeader.visibility = View.GONE
        
        // Show route selection buttons (user can start new navigation or select different route)
        if (destinationLatLng != null) {
            // Destination still set - show directions button
            myLocationButton.visibility = View.VISIBLE
            directionsButton.visibility = View.VISIBLE
            startNavigationButton.visibility = View.GONE
            startServerButton.visibility = View.GONE
            navigatePhoneButton.visibility = View.GONE
            walkingButton.visibility = View.GONE
            drivingButton.visibility = View.GONE
        } else {
            // No destination - show only location button
            myLocationButton.visibility = View.VISIBLE
            directionsButton.visibility = View.GONE
        }
        
        Log.d("MapFragment", "Exited navigation mode - connection preserved")
        Toast.makeText(context, "Navigation ended", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Observe connection state for UI updates.
     */
    private fun observeConnectionState() {
        connectionManager.connectionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ConnectionState.Connected -> {
                    dismissDataTransmissionProgressDialog()
                    // Don't show toast - status card shows connection state
                    // Update navigation header connection dot
                    if (isInNavigationMode) {
                        navigationConnectionDot.setBackgroundResource(R.drawable.connection_indicator_connected)
                    }
                }
                is ConnectionState.Failed -> {
                    dismissDataTransmissionProgressDialog()
                    // Don't show toast - status card already shows the error
                    // Optionally update navigation header
                    if (isInNavigationMode) {
                        navigationConnectionDot.setBackgroundResource(R.drawable.connection_indicator_disconnected)
                    }
                }
                is ConnectionState.Disconnected -> {
                    // Update navigation header connection dot
                    if (isInNavigationMode) {
                        navigationConnectionDot.setBackgroundResource(R.drawable.connection_indicator_disconnected)
                    }
                }
                else -> { /* Ignore other states in MapFragment */ }
            }
        }
    }

    /**
     * Show device discovery dialog and convert route for transmission.
     */
    private fun showDeviceDiscoveryAndConvertRoute() {
        if (selectedRouteJson.isNullOrEmpty()) {
            Toast.makeText(context, "Please select a route first.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // Convert route in background
            val conversionTask = async { convertRoute() }
            
            // Skip device discovery if already connected - just transmit directly
            if (connectionManager.isConnected()) {
                conversionTask.await()
                transmitRouteData()
            } else {
                // Show device discovery dialog
                val deviceDiscoveryDialog = DeviceDiscoveryDialog.newInstance()
                
                // Set callback to auto-transmit route after connection succeeds
                deviceDiscoveryDialog.setOnConnectedListener {
                    // This is called just before dialog dismisses
                    // Transmit route data automatically
                    lifecycleScope.launch {
                        conversionTask.await()
                        transmitRouteData()
                    }
                }
                
                deviceDiscoveryDialog.show(childFragmentManager, "DeviceDiscovery")
                
                // Wait for route conversion in parallel
                conversionTask.await()
            }
        }
    }

    private suspend fun convertRoute() {
        selectedRouteJson?.let { json ->
            try {
                currentLocationLatLng?.let { currentLocation ->
                    val customLatLng = toCustomLatLng(currentLocation)
                    routeConverter.convertJsonRouteToUnityCoords(json, customLatLng)
                    routeConversionCompleted = true
                    Log.d("MapFragment", "Route conversion completed")
                }
            } catch (e: Exception) {
                Log.e("MapFragment", "Route conversion failed: ${e.message}")
                routeConversionCompleted = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Route conversion failed. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Log.e("MapFragment", "No route selected for conversion.")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No route selected. Please select a route and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Public method for MainActivity to trigger route sending.
     * Called when user clicks the FAB after selecting a route.
     */
    fun sendRouteToQuest() {
        if (!connectionManager.isConnected()) {
            Toast.makeText(context, "Not connected to Quest. Please connect first.", Toast.LENGTH_SHORT).show()
            showDeviceDiscoveryAndConvertRoute()
            return
        }
        
        // Ensure route is converted
        if (!routeConversionCompleted) {
            lifecycleScope.launch {
                convertRoute()
                transmitRouteData()
            }
        } else {
            transmitRouteData()
        }
    }
    
    /**
     * Transmit converted route data to connected Quest device.
     * Uses NavigationEventManager for proper event lifecycle.
     */
    private fun transmitRouteData() {
        // RouteConverter.lastConvertedRoute returns List<wayfinder.UnityCoord>
        routeConverter.lastConvertedRoute?.let { unityCoordList ->
            // Convert from wayfinder.UnityCoord to domain.model.UnityCoord
            val waypoints = unityCoordList.map { coord ->
                com.wayfinder.wayfinder.domain.model.UnityCoord(coord.x, coord.z)
            }
            
            // Create metadata from route info
            var etaString = "--"
            val metadata = selectedRouteJson?.let { json ->
                try {
                    val route = Gson().fromJson(json, Route::class.java)
                    val totalMeters = route.legs.sumOf { it.distance.value }.toFloat()
                    val totalSeconds = route.legs.sumOf { it.duration.value }
                    
                    // Calculate ETA string
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    etaString = if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"
                    
                    com.wayfinder.wayfinder.domain.model.RouteMetadata(
                        distanceRemainingMeters = totalMeters,
                        etaSeconds = totalSeconds
                    )
                } catch (e: Exception) { null }
            }
            
            showDataTransmissionProgressDialog()
            Toast.makeText(context, "Starting navigation...", Toast.LENGTH_SHORT).show()
            
            // Use NavigationEventManager for proper started/ended events
            navigationEventManager.startNavigation(waypoints, metadata) { isSuccess, message ->
                activity?.runOnUiThread {
                    dismissDataTransmissionProgressDialog()
                    if (isSuccess) {
                        Toast.makeText(context, "Navigation started!", Toast.LENGTH_SHORT).show()
                        // Enter navigation mode with destination and ETA
                        enterNavigationMode(destinationName ?: "Destination", etaString)
                    } else {
                        Toast.makeText(context, "Failed: $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } ?: run {
            Toast.makeText(context, "No route data available. Please select a route.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPhoneNavigation() {
        val navigationFragment = NavigationFragment().apply {
            arguments = Bundle().apply {
                putString("selectedRouteJson", selectedRouteJson)
            }
        }

        activity?.supportFragmentManager?.beginTransaction()
            ?.replace(R.id.fragment_container, navigationFragment)
            ?.addToBackStack(null)
            ?.commit()
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
        startServerButton.visibility = View.GONE
        navigatePhoneButton.visibility = View.GONE
        startNavigationButton.visibility = View.VISIBLE
    }


    private fun fetchLocationAndDisplay() {
        lifecycleScope.launch {
            val lastLocation = locationService.getLastLocation()
            lastLocation?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                currentLocationLatLng = currentLatLng
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
            } ?: Log.d("MapFragment", "Last location is null")
        }
    }


    private fun setupAutocompleteFragment() {
        val autocompleteFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
        
        // Set listener for clear button click (search bar X button)
        autocompleteFragment.view?.findViewById<View>(
            com.google.android.libraries.places.R.id.places_autocomplete_clear_button
        )?.setOnClickListener {
            autocompleteFragment.setText("")
            resetMapToInitialState()
        }
        
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                destinationLatLng = place.latLng
                destinationName = place.name // Save for navigation header
                mMap.clear()
                polylineToRouteMap.clear()
                routeInfoMarkerToRouteMap.clear()
                routeConversionCompleted = false
                
                place.latLng?.let {
                    mMap.addMarker(MarkerOptions().position(it).title(place.name))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))

                    myLocationButton.visibility = View.VISIBLE
                    directionsButton.visibility = View.VISIBLE
                    startNavigationButton.visibility = View.GONE
                    walkingButton.visibility = View.GONE
                    drivingButton.visibility = View.GONE
                    startServerButton.visibility = View.GONE
                    navigatePhoneButton.visibility = View.GONE
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

                var isFirstRoute = true

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
                        .zIndex(if (isFirstRoute) 1f else 0f)

                    mMap.addPolyline(shadowPolylineOptions)
                    val polyline = mMap.addPolyline(polylineOptions)
                    polyline.tag = route

                    if (isFirstRoute) {
                        Log.d("SelectedRoute", "Default-selected route: ${Gson().toJson(route)}")
                        selectedPolyline = polyline
                        selectedRouteJson = Gson().toJson(route)
                        isFirstRoute = false
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
        return decodedPath[decodedPath.size / 2]
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

        Log.d("SelectedRoute", "Selected route: ${Gson().toJson(selectedRoute)}")

        polylineToRouteMap[polyline]?.let { route ->
            selectedRouteJson = Gson().toJson(route)
            routeConversionCompleted = false // Reset conversion when route changes
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
                deferredCompletion.complete(Unit)
            }

            override fun onCancel() {
                deferredCompletion.complete(Unit)
            }
        })

        deferredCompletion.await()

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

    private fun showDataTransmissionProgressDialog() {
        progressDialog = Dialog(requireContext()).apply {
            setContentView(R.layout.data_transmission_progress)
            setCancelable(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }
    }

    private fun dismissDataTransmissionProgressDialog() {
        progressDialog?.dismiss()
    }

    private fun toCustomLatLng(googleLatLng: com.google.android.gms.maps.model.LatLng): com.wayfinder.wayfinder.data.converter.RouteConverter.LatLng {
        return com.wayfinder.wayfinder.data.converter.RouteConverter.LatLng(googleLatLng.latitude, googleLatLng.longitude)
    }

    private fun toGoogleLatLng(customLatLng: com.wayfinder.wayfinder.data.converter.RouteConverter.LatLng): com.google.android.gms.maps.model.LatLng {
        return com.google.android.gms.maps.model.LatLng(customLatLng.latitude, customLatLng.longitude)
    }
}