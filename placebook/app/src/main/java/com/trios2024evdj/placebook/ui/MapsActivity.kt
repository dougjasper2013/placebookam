package com.trios2024evdj.placebook.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.common.api.ApiException

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.trios2024evdj.placebook.databinding.ActivityMapsBinding

import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.trios2024evdj.placebook.R
import com.trios2024evdj.placebook.adapter.BookmarkInfoWindowAdapter
import com.trios2024evdj.placebook.viewmodel.MapsViewModel
import com.trios2024evdj.placebook.db.PlaceBookDatabase

import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    // private var locationRequest: LocationRequest? = null

    private lateinit var mMap: GoogleMap

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var placesClient: PlacesClient

    private lateinit var binding: ActivityMapsBinding

    private val mapsViewModel by viewModels<MapsViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityMapsBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        setupLocationClient()
        setupPlacesClient()
    }

    private fun displayPoi(pointOfInterest: PointOfInterest) {
        displayPoiGetPlaceStep(pointOfInterest)
    }

    private fun displayPoiGetPlaceStep(pointOfInterest: PointOfInterest) {
        val placeId = pointOfInterest.placeId

        val placeFields = listOf(Place.Field.ID,
            Place.Field.NAME,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG)

        val request = FetchPlaceRequest
            .builder(placeId, placeFields)
            .build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                displayPoiGetPhotoStep(place)
//                Toast.makeText(this,
//                    "${place.name}, " +
//                            "${place.phoneNumber}",
//                    Toast.LENGTH_LONG).show()
            }.addOnFailureListener {
                    exception ->
                if (exception is ApiException) {
                    val statusCode = exception.statusCode
                    Log.e(
                        TAG,
                        "Place not found: " +
                                exception.message + ", " +
                                "statusCode: " + statusCode)
                }
            }
    }

    private fun displayPoiGetPhotoStep(place: Place) {
        // 1
        val photoMetadata = place
            .getPhotoMetadatas()?.get(0)
        // 2
        if (photoMetadata == null) {
            // Next step here
            displayPoiDisplayStep(place, null)
            return
        }
        // 3
        val photoRequest = FetchPhotoRequest
            .builder(photoMetadata)
            .setMaxWidth(resources.getDimensionPixelSize(
                R.dimen.default_image_width
            ))
            .setMaxHeight(resources.getDimensionPixelSize(
                R.dimen.default_image_height
            ))
            .build()
        // 4
        placesClient.fetchPhoto(photoRequest)
            .addOnSuccessListener { fetchPhotoResponse ->
                val bitmap = fetchPhotoResponse.bitmap
                // Next step here
                displayPoiDisplayStep(place, bitmap)
            }.addOnFailureListener { exception ->
                if (exception is ApiException) {
                    val statusCode = exception.statusCode
                    Log.e(
                        TAG,
                        "Place not found: " +
                                exception.message + ", " +
                                "statusCode: " + statusCode)
                }
            }
    }

    private fun displayPoiDisplayStep(place: Place, photo: Bitmap?) {
//        val iconPhoto = if (photo == null) {
//            BitmapDescriptorFactory.defaultMarker()
//        } else {
//            BitmapDescriptorFactory.fromBitmap(photo)
//        }

        val marker = mMap.addMarker(MarkerOptions()
            .position(place.latLng as LatLng)
//            .icon(iconPhoto)
            .title(place.name)
            .snippet(place.phoneNumber)
        )
        marker?.tag = PlaceInfo(place, photo)
    }

    private fun handleInfoWindowClick(marker: Marker) {
        val placeInfo = (marker.tag as PlaceInfo)
        if( placeInfo.place != null) {
            GlobalScope.launch {
                mapsViewModel.addBookmarkFromPlace(placeInfo.place,
                    placeInfo.image)
            }
//            mapsViewModel.addBookmarkFromPlace(placeInfo.place,
//                placeInfo.image)
        }
        marker.remove()
    }



    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        //mMap.setInfoWindowAdapter(BookmarkInfoWindowAdapter(this))

        setupMapListeners()
        createBookmarkMarkerObserver()

        getCurrentLocation()

        //mMap.setOnPoiClickListener {
            // Toast.makeText(this, it.name, Toast.LENGTH_LONG).show()
        //    displayPoi(it)
        //}
    }

    private fun setupMapListeners() {
        mMap.setInfoWindowAdapter(BookmarkInfoWindowAdapter(this))
        mMap.setOnPoiClickListener {
            displayPoi(it)
        }
        mMap.setOnInfoWindowClickListener {
            handleInfoWindowClick(it)
        }
    }

    private fun setupPlacesClient() {
        Places.initialize(applicationContext,
            getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)
    }

    private fun setupLocationClient() {
        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION
        )
    }

    companion object {
        private const val REQUEST_LOCATION = 1
        private const val TAG = "MapsActivity"
    }

    private fun getCurrentLocation() {
        if ((ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) ||
            (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED))
        {
            requestLocationPermissions()
        }
        else
        {
//            if (locationRequest == null) {
//                locationRequest = LocationRequest.create()
//                locationRequest?.let { locationRequest ->
//                    locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
//                    locationRequest.interval = 5000
//                    locationRequest.fastestInterval = 1000
//                    val locationCallback = object: LocationCallback() {
//                        override fun onLocationResult(locationResult: LocationResult?) {
//                            getCurrentLocation()
//                        }
//                    }
//                    fusedLocationClient.requestLocationUpdates(locationRequest,
//                        locationCallback, null)
//                }
//            }

            mMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation.addOnCompleteListener {
                val location = it.result
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
//                    mMap.clear()
//                    mMap.addMarker(MarkerOptions().position(latLng)
//                        .title("You are here!")
//                    )
                    val update = CameraUpdateFactory.newLatLngZoom(latLng, 14.0f)
                    mMap.moveCamera(update)
                }
                else
                {
                    Log.e(TAG, "No location found")
                }
            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION){
            if (grantResults.size == 2 && grantResults[0] ==
                PackageManager.PERMISSION_GRANTED && grantResults[1] ==
                PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            }
            else {
                Log.e(TAG, "Location permission denied")
            }
        }
    }

    private fun addPlaceMarker(
        bookmark: MapsViewModel.BookmarkMarkerView): Marker? {

        val marker = mMap.addMarker(MarkerOptions()
            .position(bookmark.location)
            .icon(BitmapDescriptorFactory.defaultMarker(
                BitmapDescriptorFactory.HUE_AZURE))
            .alpha(0.8f))

        marker.tag = bookmark

        return marker
    }

    private fun displayAllBookmarks(
        bookmarks: List<MapsViewModel.BookmarkMarkerView>) {
        bookmarks.forEach { addPlaceMarker(it) }
    }

    private fun createBookmarkMarkerObserver() {
        // 1
        mapsViewModel.getBookmarkMarkerViews()?.observe(
            this, {
                // 2
                mMap.clear()
                // 3
                it?.let {
                    displayAllBookmarks(it)
                }
            })
    }


    class PlaceInfo(val place: Place? = null,
        val image: Bitmap? = null)

}