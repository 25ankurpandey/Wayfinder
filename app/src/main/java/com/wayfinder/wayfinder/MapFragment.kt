package com.wayfinder.wayfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.wayfinder.wayfinderar.LocationService
import kotlinx.coroutines.launch
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var locationService: LocationService

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

        setupMyLocationButton(view)
        setupAutocompleteFragment()
    }

    private fun setupAutocompleteFragment() {
        val autocompleteFragment = AutocompleteSupportFragment.newInstance().apply {
            setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
            setHint("Search location")
//            setCountries("US") // Optional: set country filter
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.search_autocomplete_container, autocompleteFragment)
            .commit()

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                place.latLng?.let {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
                }
            }

            override fun onError(status: Status) {
                Log.i("Autocomplete", "An error occurred: $status")
            }
        })
    }

    private fun setupMyLocationButton(view: View) {
        view.findViewById<Button>(R.id.my_location_button).setOnClickListener {
            moveToCurrentLocation()
        }
    }

    private fun moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndDisplay()
        } else {
            // Reminder: Handle permission request as needed.
        }
    }

    private fun fetchLocationAndDisplay() {
        lifecycleScope.launch {
            val lastLocation = locationService.getLastLocation() // Ensure this method is coroutine-friendly
            lastLocation?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
            } ?: Log.d("MapFragment", "Last location is null")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = false
            fetchLocationAndDisplay() // Call here to ensure location is set when map is ready
        }
    }

}
