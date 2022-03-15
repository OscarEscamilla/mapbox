package com.escam.mapbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

class MainActivity : AppCompatActivity() {

    private var mapView: MapView? = null

    private var fabButton: FloatingActionButton? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val navigationLocationProvider = NavigationLocationProvider()

    /**
     * Mapbox Maps entry point obtained from the [MapView].
     * You need to get a new reference to this object whenever the [MapView] is recreated.
     */
    private lateinit var mapboxMap: MapboxMap

    /**
     * Mapbox Navigation entry point. There should only be one instance of this object for the app.
     * You can use [MapboxNavigationProvider] to help create and obtain that instance.
     */
    private lateinit var mapboxNavigation: MapboxNavigation

    val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    // Precise location access granted.
                    //toast("Fine location access granted")

                    initNavigation()
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // Only approximate location access granted.
                    toast("Coarse location access granted")
                }
                else -> {
                    // No location access granted.
                    toast("Permision denied")
                }
            }
        }
    }

    private val locationObserver = object : LocationObserver {
        /**
         * Invoked as soon as the [Location] is available.
         */
        override fun onNewRawLocation(rawLocation: Location) {
            // use this callback to get location updates, but as the name suggests
            // these are raw location updates which are usually noisy.
        }

        /**
         * Provides the best possible location update, snapped to the route or
         * map-matched to the road if possible.
         */
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(
                enhancedLocation,
                locationMatcherResult.keyPoints,
            )
            // Invoke this method to move the camera to your current location.
            updateCamera(enhancedLocation)
        }
    }

    private fun initLocationProvider() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun setupView() {
        fabButton = findViewById(R.id.floating_action_button)
        fabButton?.setOnClickListener {

        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        mapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS)

        mapView?.location?.apply {
            this?.setLocationProvider(navigationLocationProvider)
            // When true, the blue circular puck is shown on the map. If set to false, user
            // location in the form of puck will not be shown on the map.
            enabled = true
        }

        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            toast("Permission not granted, you need allow access to you location")
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            initNavigation()
        }

        initLocationProvider()
        setupView()
    }


    @SuppressLint("MissingPermission")
    private fun initNavigation() {
        mapboxNavigation = MapboxNavigation(
            NavigationOptions.Builder(this)
                .accessToken(getString(R.string.mapbox_access_token))
                .build()
        ).apply {
            // This is important to call as the [LocationProvider] will only start sending
            // location updates when the trip session has started.
            startTripSession()
            // Register the location observer to listen to location updates received from the
            // location provider
            registerLocationObserver(locationObserver)
        }
    }

    private fun updateCamera(location: Location) {
        val mapAnimationOptions = MapAnimationOptions.Builder().duration(1500L).build()
        mapView?.camera?.easeTo(
            CameraOptions.Builder()
                // Centers the camera to the lng/lat specified.
                .center(Point.fromLngLat(location.longitude, location.latitude))
                // specifies the zoom value. Increase or decrease to zoom in or zoom out
                .zoom(14.0)
                // specify frame of reference from the center.
                .padding(EdgeInsets(500.0, 0.0, 0.0, 0.0))
                .build(),
            mapAnimationOptions
        )

    }


    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
        // make sure to stop the trip session. In this case it is being called inside `onDestroy`.
        mapboxNavigation.stopTripSession()
        // make sure to unregister the observer you have registered.
        mapboxNavigation.unregisterLocationObserver(locationObserver)
    }
}