package com.example.intern_project

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    // Notification Channel
    private val CHANNEL_ID =
        "waste_tracker_channel"

    // Notify only once
    private var notificationShown = false

    // Nearby Distance
    private val NEARBY_DISTANCE = 500.0

    // Firebase Database
    private val database = FirebaseDatabase.getInstance(
        "https://gramawastetracker-a717f-default-rtdb.asia-southeast1.firebasedatabase.app"
    )

    // Vehicle Markers
    private val vehicleMarkers =
        mutableMapOf<String, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Ask Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    100
                )
            }
        }

        // Create Notification Channel
        createNotificationChannel()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map)
                as SupportMapFragment

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {

        googleMap = map

        googleMap.mapType =
            GoogleMap.MAP_TYPE_NORMAL

        // Permission Check
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                1
            )

            return
        }

        // Enable User Location
        googleMap.isMyLocationEnabled = true

        // Firebase Vehicles Reference
        val vehiclesRef =
            database.getReference("vehicles")

        // Listen for Realtime Updates
        vehiclesRef.addValueEventListener(
            object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    for (vehicleSnapshot in snapshot.children) {

                        val vehicleId =
                            vehicleSnapshot.key ?: continue

                        val tractorLatitude =
                            vehicleSnapshot.child("latitude")
                                .getValue(Double::class.java)

                        val tractorLongitude =
                            vehicleSnapshot.child("longitude")
                                .getValue(Double::class.java)

                        if (tractorLatitude != null &&
                            tractorLongitude != null
                        ) {

                            val location =
                                LatLng(
                                    tractorLatitude,
                                    tractorLongitude
                                )

                            // Existing marker
                            val existingMarker =
                                vehicleMarkers[vehicleId]

                            if (existingMarker != null) {

                                // Move truck smoothly
                                existingMarker.position =
                                    location

                            } else {

                                // Truck Image
                                val bitmap =
                                    BitmapFactory.decodeResource(
                                        resources,
                                        R.drawable.img
                                    )

                                val bigMarker =
                                    Bitmap.createScaledBitmap(
                                        bitmap,
                                        140,
                                        140,
                                        false
                                    )

                                // Create Marker
                                val newMarker =
                                    googleMap.addMarker(
                                        MarkerOptions()
                                            .position(location)
                                            .title(vehicleId)
                                            .anchor(0.5f, 0.5f)
                                            .icon(
                                                BitmapDescriptorFactory
                                                    .fromBitmap(
                                                        bigMarker
                                                    )
                                            )
                                    )

                                if (newMarker != null) {

                                    vehicleMarkers[vehicleId] =
                                        newMarker
                                }
                            }

                            Log.d(
                                "FIREBASE",
                                "Updated: $vehicleId"
                            )

                            // User Location
                            val userLocation =
                                Location("User").apply {

                                    latitude =
                                        googleMap.myLocation?.latitude
                                            ?: 0.0

                                    longitude =
                                        googleMap.myLocation?.longitude
                                            ?: 0.0
                                }

                            // Tractor Location
                            val tractorLocation =
                                Location("Tractor").apply {

                                    latitude =
                                        tractorLatitude

                                    longitude =
                                        tractorLongitude
                                }

                            // Distance
                            val distance =
                                userLocation.distanceTo(
                                    tractorLocation
                                )

                            Log.d(
                                "DISTANCE",
                                "Distance: $distance"
                            )

                            // Notify Once
                            if (distance <
                                NEARBY_DISTANCE &&
                                !notificationShown
                            ) {

                                showNotification(
                                    "$vehicleId is nearby!"
                                )

                                notificationShown = true
                            }

                            // Reset Notification
                            if (distance >
                                NEARBY_DISTANCE
                            ) {

                                notificationShown = false
                            }
                        }
                    }
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {

                    Log.e(
                        "FIREBASE",
                        "Error: ${error.message}"
                    )
                }
            }
        )
    }

    // Create Notification Channel
    private fun createNotificationChannel() {

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Waste Tracker Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.createNotificationChannel(channel)
    }

    // Show Notification
    @SuppressLint("MissingPermission")
    private fun showNotification(message: String) {

        val builder =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_alert
                )
                .setContentTitle(
                    "Grama Waste Tracker"
                )
                .setContentText(message)
                .setPriority(
                    NotificationCompat.PRIORITY_MAX
                )
                .setAutoCancel(true)

        NotificationManagerCompat.from(this)
            .notify(
                System.currentTimeMillis().toInt(),
                builder.build()
            )
    }
}