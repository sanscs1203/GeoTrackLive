package com.tiagosolutions.proyect_1_electronic_design

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.os.Looper

class LocationService : Service() {

    companion object {
        const val CHANNEL_ID = "LocationServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_SERVICE = "START_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val ACTION_START_TRANSMISSION = "START_TRANSMISSION"
        const val ACTION_STOP_TRANSMISSION = "STOP_TRANSMISSION"
        const val EXTRA_IP1 = "IP1"
        const val EXTRA_IP2 = "IP2"

        // Broadcast actions to communicate with MainActivity
        const val ACTION_LOCATION_UPDATE = "com.tiagosolutions.proyect_1_electronic_design.LOCATION_UPDATE"
        const val ACTION_TRANSMISSION_STATE = "com.tiagosolutions.proyect_1_electronic_design.TRANSMISSION_STATE"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ALTITUDE = "altitude"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_IS_TRANSMITTING = "is_transmitting"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var currentLocation: Location? = null

    // Coroutine scope for the service
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transmissionJob: Job? = null

    // Transmission state
    private var isTransmitting = false
    private var ip1: String? = null
    private var ip2: String? = null
    private var lastTransmissionTime: String? = null

    override fun onCreate() {
        super.onCreate()

        // Create notification channel
        createNotificationChannel()

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Start location updates
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForegroundService()
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundService()
            }
            ACTION_START_TRANSMISSION -> {
                ip1 = intent.getStringExtra(EXTRA_IP1)
                ip2 = intent.getStringExtra(EXTRA_IP2)
                startTransmission()
            }
            ACTION_STOP_TRANSMISSION -> {
                stopTransmission()
            }
        }

        return START_STICKY // Service will be restarted if killed
    }

    private fun startForegroundService() {
        val notification = createNotification("Servicio de ubicación activo")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        stopTransmission()
        stopLocationUpdates()
        serviceScope.cancel()
        stopForeground(true)
        stopSelf()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 1000 // 1 second
            fastestInterval = 500 // 0.5 seconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    broadcastLocationUpdate(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    private fun startTransmission() {
        if (ip1.isNullOrEmpty() || ip2.isNullOrEmpty()) {
            Log.e("LocationService", "IPs not provided for transmission")
            return
        }

        isTransmitting = true
        updateNotification("Transmitiendo datos de ubicación")
        broadcastTransmissionState(true)

        transmissionJob = serviceScope.launch {
            while (isTransmitting) {
                val location = currentLocation
                if (location != null) {
                    val currentTime = formatDateTime(Date(location.time))

                    // Only send if time has changed (new location data)
                    if (currentTime != lastTransmissionTime) {
                        sendLocationUDP(location)
                        lastTransmissionTime = currentTime
                    }
                }
                delay(1000) // Check every second
            }
        }
    }

    private fun stopTransmission() {
        isTransmitting = false
        transmissionJob?.cancel()
        transmissionJob = null
        updateNotification("Servicio de ubicación activo")
        broadcastTransmissionState(false)
    }

    private fun sendLocationUDP(location: Location, port: Int = 4665) {
        val message = formatLocationMessage(location)

        serviceScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null

            try {
                socket = DatagramSocket()
                val buffer = message.toByteArray()

                // Send to IP1
                val address1 = InetAddress.getByName(ip1)
                val packet1 = DatagramPacket(buffer, buffer.size, address1, port)
                socket.send(packet1)

                // Send to IP2
                val address2 = InetAddress.getByName(ip2)
                val packet2 = DatagramPacket(buffer, buffer.size, address2, port)
                socket.send(packet2)

                Log.d("LocationService", "UDP packets sent successfully")

            } catch (e: Exception) {
                Log.e("LocationService", "Error sending UDP", e)
                // Stop transmission on error
                withContext(Dispatchers.Main) {
                    stopTransmission()
                }
            } finally {
                socket?.close()
            }
        }
    }

    private fun formatLocationMessage(location: Location): String {
        val formattedDateTime = formatDateTime(Date(location.time))

        return """
        {
            "latitude": ${String.format(Locale.US, "%.6f", location.latitude)},
            "longitude": ${String.format(Locale.US, "%.6f", location.longitude)},
            "altitude": ${String.format(Locale.US, "%.2f", location.altitude)},
            "timestamp": "$formattedDateTime"
        }
        """.trimIndent()
    }

    private fun formatDateTime(date: Date): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(date)
    }

    private fun broadcastLocationUpdate(location: Location) {
        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_ALTITUDE, location.altitude)
            putExtra(EXTRA_TIMESTAMP, formatDateTime(Date(location.time)))
        }
        sendBroadcast(intent)
    }

    private fun broadcastTransmissionState(isTransmitting: Boolean) {
        val intent = Intent(ACTION_TRANSMISSION_STATE).apply {
            putExtra(EXTRA_IS_TRANSMITTING, isTransmitting)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canal para el servicio de ubicación"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Servicio de Ubicación")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need binding for this service
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTransmission()
        stopLocationUpdates()
        serviceScope.cancel()
    }
}
