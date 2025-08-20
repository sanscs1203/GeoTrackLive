package com.tiagosolutions.proyect_1_electronic_design

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.*
import java.net.InetAddress
import java.net.Socket
import java.net.DatagramSocket
import java.net.DatagramPacket
import kotlinx.coroutines.*
import java.io.PrintWriter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var etIP1: EditText
    private lateinit var etIP2: EditText
    private lateinit var btnSendTCP: Button
    private lateinit var btnSendUDP: Button
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

        // Request permissions when app starts
        if (!checkPermissions()) {
            requestPermissions()
        } else {
            // If we already have permissions
            startLocationTracking()
        }
    }

    private fun checkPermissions(): Boolean {
        // Check if we have location permission
        val locationPermission = ContextCompat.checkSelfPermission(
            this,                                    // Context (our activity)
            Manifest.permission.ACCESS_FINE_LOCATION // The permission we're checking
        )


        // Return true only if permissions are granted
        return locationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,                               // Our activity
            arrayOf(                            // Array of permissions to request
                Manifest.permission.ACCESS_FINE_LOCATION,
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
            .setPositiveButton("Activar"){dialog, _ ->
                dialog.dismiss()
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

            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

            //Check if GPS is enable
            if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "GPS no disponible", Toast.LENGTH_SHORT).show()
                return
            }

            // Request the GPS location
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                {location ->
                    currentLocation = location
                    updateUIWithLocation(location)
                },
                mainLooper
            )

        }
    }

    private fun initializeViews() {
        etIP1 = findViewById(R.id.etIP1)
        etIP2 = findViewById(R.id.etIP2)
        btnSendTCP = findViewById(R.id.btnSendTCP)
        btnSendUDP = findViewById(R.id.btnSendUDP)
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
        btnSendTCP.setOnClickListener {

            val ip1 = etIP1.text.toString().trim()
            val ip2 = etIP2.text.toString().trim()

            // When button is clicked, check if we have permissions and validate IPs
            if (checkPermissions()) {
                // We have permissions, proceed with IP Validation
                if (validateIPs(ip1, ip2)){
                    // IP validation done, proceed with the sending
                    sendLocationTCP(ip1, ip2)
                }
            } else {
                // We don't have permissions, inform user and request again
                Toast.makeText(this, "Es necesario otorgar los permisos", Toast.LENGTH_SHORT).show()
                requestPermissions()
            }
        }

        btnSendUDP.setOnClickListener {

            val ip1 = etIP1.text.toString().trim()
            val ip2 = etIP2.text.toString().trim()

            // When button is clicked, check if we have permissions and validate IPs
            if (checkPermissions()) {
                // We have permissions, proceed with IP Validation
                if (validateIPs(ip1, ip2)){
                    // IP validation done, proceed with the sending
                    sendLocationUDP(ip1, ip2)
                }
            } else {
                // We don't have permissions, inform user and request again
                Toast.makeText(this, "Es necesario otorgar los permisos", Toast.LENGTH_SHORT).show()
                requestPermissions()
            }
        }
    }

    private fun validateIPs(ip1: String, ip2: String): Boolean {
        // Check if IPs are empyt
        if (ip1.isEmpty() || ip2.isEmpty()) {
            Toast.makeText(this, "Por favor, ingresar ambas direcciones IP", Toast.LENGTH_SHORT)
                .show()
            return false
        }

        // Check if IPs are equal
        if (ip1 == ip2) {
            Toast.makeText(this, "Las direcciones IP deben ser diferentes", Toast.LENGTH_SHORT)
                .show()
            return false
        }

        // Validate IP format using regex
        val ipPattern =
            """^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""".toRegex()

        if (!ipPattern.matches(ip1)) {
            Toast.makeText(this, "IP 1 formato inválido", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!ipPattern.matches(ip2)){
            Toast.makeText(this, "IP 2 formato inválido", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun sendLocationTCP(ip1: String, ip2: String, port: Int = 4665){

        // First check if GPS is still enabled
        if (!isGPSEnabled()) {
            requestGPSActivation()
            return
        }

        // Get current location
        val location = currentLocation
        if (location == null){
            Toast.makeText(this, "No hay ubicación disponible", Toast.LENGTH_SHORT).show()
            return
        }

        // Format the message
        val message = formatLocationMessage(location)

        // Launch coroutine for network operation
        GlobalScope.launch(Dispatchers.IO) {
            // Send to first IP
            var socket1: Socket? = null
            var writer1: PrintWriter? = null

            try {
                socket1 = Socket()
                socket1.connect(java.net.InetSocketAddress(ip1, port), 5000)
                writer1 = PrintWriter(socket1.getOutputStream(), true)
                writer1.println(message)
                writer1.flush()

                android.util.Log.d("TCP", "Sent to IP1: $ip1")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error TCP en el IP1", Toast.LENGTH_SHORT).show()
                    android.util.Log.d("TCP", "Error $ip1", e)
                }
            } finally {
                writer1?.close()
                socket1?.close()
            }

            // Send to second IP
            var socket2: Socket? = null
            var writer2: PrintWriter? = null

            try {
                socket2 = Socket()
                socket2.connect(java.net.InetSocketAddress(ip2, port), 5000)
                writer2 = PrintWriter(socket2.getOutputStream(), true)
                writer2.println(message)
                writer2.flush()

                android.util.Log.d("TCP", "Sent to IP2: $ip2")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ubicación enviada por TCP", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error TCP en el IP2", Toast.LENGTH_SHORT).show()
                    android.util.Log.d("TCP", "Error $ip2", e)
                }
            } finally {
                writer2?.close()
                socket2?.close()
            }
        }
    }

    private fun sendLocationUDP(ip1: String, ip2: String, port: Int = 4665){

        // First check if GPS is still enabled
        if (!isGPSEnabled()) {
            requestGPSActivation()
            return
        }

        // Get current location
        val location = currentLocation
        if (location == null){
            Toast.makeText(this, "No hay ubicación disponible", Toast.LENGTH_SHORT).show()
            return
        }

        // Format the message
        val message = formatLocationMessage(location)

        // Launch coroutine for network operation
        GlobalScope.launch(Dispatchers.IO){
            var socket: DatagramSocket? = null

            try {
                // Create UDP socket
                socket = DatagramSocket()

                // Convert message to bytes
                val buffer = message.toByteArray()

                // Create packet with destination
                val address_1 = InetAddress.getByName(ip1)
                val packet_1 = DatagramPacket(
                    buffer,
                    buffer.size,
                    address_1,
                    port
                )

                // Send packet
                socket.send(packet_1)

                // Create packet with destination
                val address_2 = InetAddress.getByName(ip2)
                val packet_2 = DatagramPacket(
                    buffer,
                    buffer.size,
                    address_2,
                    port
                )

                // Send packet
                socket.send(packet_2)

                //Show succes on main thread
                withContext(Dispatchers.Main){
                    Toast.makeText(
                        this@MainActivity,
                        "Ubicación enviada por UDP",
                        Toast.LENGTH_SHORT
                    ).show()

                }
            } catch (e: Exception){
                // Handle errors on main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error UDP",
                        Toast.LENGTH_SHORT
                    ).show()
                    android.util.Log.e("UDP", "Error sending", e)
                }
            } finally {
                // Clean up resources
                try {
                    socket?.close()
                } catch (e: Exception) {
                    android.util.Log.e("UDP", "Error closing socket", e)
                }
            }
        }

    }

    private fun formatLocationMessage(location: Location): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val locationDateTime = Date(location.time)
        val formattedDateTime = dateFormat.format(locationDateTime)

        // Format coordinates with specified decimal places
        val formattedLatitude = String.format(Locale.US, "%.6f", location.latitude)   // 6 decimales
        val formattedLongitude = String.format(Locale.US, "%.6f", location.longitude) // 6 decimales
        val formattedAltitude = String.format(Locale.US, "%.2f", location.altitude)   // 2 decimales

        // Format as JSON for easy parsing on server side
        return """
    {
        "latitude": $formattedLatitude,
        "longitude": $formattedLongitude,
        "altitude": $formattedAltitude,
        "timestamp": "$formattedDateTime"
    }
""".trimIndent()
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
                // Start tracking after permissions granted
                startLocationTracking()
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
