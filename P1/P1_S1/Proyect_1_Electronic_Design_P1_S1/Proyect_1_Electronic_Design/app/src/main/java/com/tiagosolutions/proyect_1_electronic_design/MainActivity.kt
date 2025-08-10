package com.tiagosolutions.proyect_1_electronic_design

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.location.Location
import android.location.LocationManager
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.*
import android.telephony.SmsManager
import kotlinx.coroutines.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var etPhoneNumber: EditText
    private lateinit var btnSendSMS: Button
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvDateTime: TextView

    // Permission request codes
    private val PERMISSION_REQUEST_CODE = 100

    // Location related variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var locationUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        initializeViews()

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up button click listener
        setupClickListeners()

        // Check if GPS is enabled
        if (isGPSEnabled()){

            // Request permissions when app starts
            if (!checkPermissions()) {
                requestPermissions()
            } else {
                // If we already have permissions
                startLocationTracking()
            }
        } else {
            // GPS is OFF, ask user to turn it on
            requestGPSActivation()
        }
    }

    private fun checkPermissions(): Boolean {
        // Check if we have location permission
        val locationPermission = ContextCompat.checkSelfPermission(
            this,                                    // Context (our activity)
            Manifest.permission.ACCESS_FINE_LOCATION // The permission we're checking
        )

        // Check if we have SMS permission
        val smsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        )

        // Return true only if BOTH permissions are granted
        return locationPermission == PackageManager.PERMISSION_GRANTED &&
                smsPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,                               // Our activity
            arrayOf(                            // Array of permissions to request
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PERMISSION_REQUEST_CODE             // Code to identify this request (100)
        )
    }

    private fun isGPSEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun requestGPSActivation(){
        AlertDialog.Builder(this)
            .setTitle("GPS Desactivado")
            .setMessage("Para usar la aplicación, es necesario activar el GPS.")
            .setPositiveButton("Activar"){_, _ ->
                // Take user to GPS Settings
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("No activar"){ dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "La aplicación necesita GPS para funcionar", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun startLocationTracking() {
        // Begin location updates each second
        locationUpdateJob = coroutineScope.launch {
            while (isActive) {
                // Check GPS status every iteration
                if (!isGPSEnabled()) {
                    // GPS was turned off while app is running!
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "GPS desactivado", Toast.LENGTH_SHORT).show()
                        requestGPSActivation()
                    }
                    break // Stop the location tracking loop
                }

                getCurrentLocation()
                delay(1000) // Each second
            }
        }
    }

    private fun stopLocationTracking() {
        locationUpdateJob?.cancel()
        locationUpdateJob = null
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // FusedLocationProviderClient with high precission
            fusedLocationClient.getCurrentLocation(
                LocationRequest.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    updateUIWithLocation(it)
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Error al obtener ubicación: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun initializeViews() {
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnSendSMS = findViewById(R.id.btnSendSMS)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvAltitude = findViewById(R.id.tvAltitude)
        tvDateTime = findViewById(R.id.tvDateTime)
    }

    private fun updateUIWithLocation(location: Location) {
        // Update Latitude with 6 decimal places
        tvLatitude.text = String.format("%.6f", location.latitude)

        // Update Longitude with 6 decimal places
        tvLongitude.text = String.format("%.6f", location.longitude)

        // Update altitude in meters above sea level
        tvAltitude.text = String.format("%.2f m.s.n.m", location.altitude)

        // Update date and hour
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val locationDateTime = Date(location.time)  // Convert milliseconds to Date
        tvDateTime.text = dateFormat.format(locationDateTime)
    }

    private fun setupClickListeners() {
        btnSendSMS.setOnClickListener {
            // When button is clicked, check if we have permissions
            if (checkPermissions()) {
                // We have permissions, proceed with getting location
                getLocationAndSendSMS()
            } else {
                // We don't have permissions, inform user and request again
                Toast.makeText(this, "Es necesario otorgar los permisos", Toast.LENGTH_SHORT).show()
                requestPermissions()
            }
        }
    }

    private fun getLocationAndSendSMS() {

        // First check if GPS is still enabled
        if (!isGPSEnabled()) {
            requestGPSActivation()
            return
        }

        // Get phone number from input
        val phoneNumber = etPhoneNumber.text.toString().trim()

        // Validate phone number is not empty
        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "Ingresa un número telefónico", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if we have a location
        if (currentLocation == null) {
            Toast.makeText(this, "Esperando ubicación GPS...", Toast.LENGTH_SHORT).show()
            return
        }

        // We have location, send SMS
        sendSMS()
    }

    private fun sendSMS() {

        // Get the phone number from input and location
        val phoneNumber = etPhoneNumber.text.toString().trim()

        // Double-check we have a location (defensive programming)
        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, "No hay ubicación disponible", Toast.LENGTH_SHORT).show()
            return
        }

        // Format the date and time from location
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val locationDateTime = Date(location.time)
        val formattedDateTime = dateFormat.format(locationDateTime)

        // Create the SMS message with all required info
        val message = buildString {
            appendLine("Ubicación GPS:")
            appendLine("Latitud: ${String.format("%.6f", location.latitude)}")
            appendLine("Longitud: ${String.format("%.6f", location.longitude)}")
            appendLine("Altitud: ${String.format("%.2f", location.altitude)} m.s.n.m")
            appendLine("Fecha y Hora: $formattedDateTime")
        }

        try {
            // Get the SMS manager
            val smsManager = SmsManager.getDefault()

            // Split message into parts if too long
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                phoneNumber,    // destination
                null,          // service center address (null for default)
                parts,         // message parts
                null,          // sent intents
                null           // delivery intents
            )

            // Show success message
            Toast.makeText(this, "SMS enviado a $phoneNumber", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            // Handle any errors
            Toast.makeText(this, "Error enviando SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()

        // Check if GPS is enabled
        if (isGPSEnabled()) {
            // Request permissions when app starts
            if (!checkPermissions()) {
                requestPermissions()
            } else {
                // Only start if not already running
                if (locationUpdateJob == null || !locationUpdateJob!!.isActive) {
                    startLocationTracking()
                }
            }
        } else {
            // GPS is OFF, stop tracking and ask user to turn it on
            stopLocationTracking()
            requestGPSActivation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,              // The code we sent (100)
        permissions: Array<out String>, // Which permissions were requested
        grantResults: IntArray         // Results: GRANTED or DENIED for each
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if this is our request (code 100)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if array has results AND all are GRANTED
            if (grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            ) {
                // User said yes to all permissions
                // Also check if GPS is enabled before starting
                if (isGPSEnabled()) {
                    startLocationTracking()
                } else {
                    requestGPSActivation()
                }
            } else {
                // User denied at least one permission
                Toast.makeText(
                    this,
                    "Permisos necesarios para enviar la ubicación",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

}
