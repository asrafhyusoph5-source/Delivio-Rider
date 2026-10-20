package com.example.data.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RiderLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastReportedLocation: Location? = null
    private var lastReportedTimeMillis: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                handleNewLocation(loc)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
        } catch (e: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000L)
            .setMinUpdateIntervalMillis(15000L)
            .setMinUpdateDistanceMeters(20f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission might have been revoked
        }
    }

    private fun handleNewLocation(loc: Location) {
        val now = System.currentTimeMillis()
        val prev = lastReportedLocation

        // Enforce: at most every 15 seconds and only if moved over 20 m
        if (prev != null) {
            val dist = prev.distanceTo(loc)
            val elapsed = now - lastReportedTimeMillis
            if (dist < 20f || elapsed < 14000L) {
                // Update in-memory tracker anyway for smooth UI
                RiderLocationTracker.updateLocation(loc.latitude, loc.longitude)
                return
            }
        }

        lastReportedLocation = loc
        lastReportedTimeMillis = now
        RiderLocationTracker.updateLocation(loc.latitude, loc.longitude)

        // Write to Firestore riders/{uid}.location
        val uid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        if (uid != null) {
            val db = FirebaseFirestore.getInstance()
            val locData = mapOf(
                "lat" to loc.latitude,
                "lng" to loc.longitude,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("riders").document(uid).update(
                "location", locData,
                "lastSeenAt", FieldValue.serverTimestamp()
            ).addOnFailureListener {
                // If document does not exist yet or merge is safer
                db.collection("riders").document(uid).set(
                    mapOf("location" to locData, "lastSeenAt" to FieldValue.serverTimestamp()),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }

            // While on an order also write orders.riderLocation
            val activeOrders = RiderLocationTracker.activeOrderIds.value
            for (orderId in activeOrders) {
                db.collection("orders").document(orderId).update(
                    "riderLocation", locData
                )
            }
        }
    }

    private fun buildNotification(): android.app.Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Delivio Rider is Online")
            .setContentText("Actively receiving and tracking live dispatch orders")
            .setSmallIcon(R.drawable.delivio_rider_icon_1791915489146)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Delivio Rider Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used to track rider location while online"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "delivio_rider_location_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_SERVICE = "ACTION_START_LOCATION_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_LOCATION_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, RiderLocationService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RiderLocationService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
