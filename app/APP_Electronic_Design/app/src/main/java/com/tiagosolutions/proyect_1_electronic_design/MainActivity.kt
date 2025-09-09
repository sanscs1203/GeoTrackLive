package com.tiagosolutions.proyect_1_electronic_design

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.Context
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
import android.content.Intent
import android.app.Service
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import androidx.core.app.NotificationCompat
import android.os.Build
import android.util.Log
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    // DNS constants predefined
    companion object{
        private const val DNS_HOST_1 = "geotracklive0.ddns.net"
        private const val DNS_HOST_2 = "geotracklive1.ddns.net"
        private const val DNS_HOST_3 = "geotracklive2.ddns.net"
        private const val DNS_HOST_4 = "geotracklive3.ddns.net"
    }

    // UI Elements
    private lateinit var btnSendUDP: Button
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvDateTime: TextView

    // Permission request codes
    private val PERMISSION_REQUEST_CODE = 100
    private val BACKGROUND_PERMISSION_REQUEST_CODE = 101

    // Location related variables
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var locationUpdateJob: Job? = null
    private var transmissionJob: Job? = null

    // Transmission related variables
    private var transmission_state: Boolean = false
    private var isAppInForeground: Boolean = true
    private var currentHost1: String = ""
    private var currentHost2: String = ""
    private var currentHost3: String = ""
    private var currentHost4: String = ""
    private var lastSentTime: Long = 0

    // Permission state tracking
    private var basicPermissionsGranted: Boolean = false
    private var backgroundPermissionGranted: Boolean = false

    // DNS cache to upgrade the performance
    private val dnsCache = mutableMapOf<String, String>()
    private val DNS_CACHE_TIMEOUT = 300000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        initializeViews()

        /// Initialize LocationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Set up button click listener
        setupClickListeners()

        // Check and request permissions when app starts
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (checkBasicPermissions()) {
            basicPermissionsGranted = true
            // Check background permission separately
            if (checkBackgroundPermission()) {
                backgroundPermissionGranted = true
                onAllPermissionsReady()
            } else {
                // We have basic permissions but need background permission
                // We'll request it when user tries to start transmission
                startLocationTracking()
            }
        } else {
            // Request basic permissions first
            requestBasicPermissions()
        }
    }

    private fun checkBasicPermissions(): Boolean {
        val locationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val internetPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.INTERNET
        )
        val networkStatePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        val foregroundServicePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.FOREGROUND_SERVICE
        )
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            PackageManager.PERMISSION_GRANTED
        }

        return locationPermission == PackageManager.PERMISSION_GRANTED &&
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED &&
                internetPermission == PackageManager.PERMISSION_GRANTED &&
                networkStatePermission == PackageManager.PERMISSION_GRANTED &&
                foregroundServicePermission == PackageManager.PERMISSION_GRANTED &&
                notificationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBackgroundPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed for older Android versions
        }
    }

    private fun requestBasicPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.FOREGROUND_SERVICE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!checkBackgroundPermission()) {
                // Show explanation to user before requesting
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permiso de ubicación en segundo plano")
                    .setMessage("Esta aplicación necesita acceso a la ubicación en segundo plano para continuar enviando datos cuando la aplicación no esté visible. Esto es necesario para mantener la transmisión activa.")
                    .setPositiveButton("Conceder") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            BACKGROUND_PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("Usar solo en primer plano") { _, _ ->
                        Toast.makeText(this, "La transmisión se pausará cuando la app esté en segundo plano", Toast.LENGTH_LONG).show()

                        if (!transmission_state) {
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Iniciar transmisión")
                                .setMessage("¿Deseas iniciar la transmisión? Solo funcionará cuando la app esté visible.")
                                .setPositiveButton("Iniciar") { _, _ ->
                                    changeTransmission()
                                    startTransmission()
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun isGPSEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun requestGPSActivation() {
        AlertDialog.Builder(this)
            .setTitle("GPS Desactivado")
            .setMessage("Para usar la aplicación, es necesario activar el GPS.")
            .setPositiveButton("Activar") { dialog, _ ->
                dialog.dismiss()
                // Take user to GPS Settings
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("No activar") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "La aplicación necesita GPS para funcionar", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun onAllPermissionsReady() {
        Toast.makeText(this, "Todos los permisos están listos", Toast.LENGTH_SHORT).show()
        startLocationTracking()
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

                        // If the transmission is activated, we stop it
                        if (transmission_state) {
                            changeTransmission()
                            stopTransmission()
                        }
                    }
                    break // Stop the location tracking loop
                }

                getCurrentLocation()
                delay(1000) // Each second
            }
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Check if GPS is enabled
            if (!isGPSEnabled()) {
                Toast.makeText(this, "GPS no disponible", Toast.LENGTH_SHORT).show()
                return
            }

            // Request the GPS location using LocationManager
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                { location ->
                    currentLocation = location
                    updateUIWithLocation(location)
                },
                mainLooper
            )
        }
    }

    private fun initializeViews() {
        btnSendUDP = findViewById(R.id.btnSendUDP)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvDateTime = findViewById(R.id.tvDateTime)
    }

    private fun setupClickListeners() {
        btnSendUDP.setOnClickListener {

            if (!transmission_state) {
                // Starting transmission
                if (!basicPermissionsGranted) {
                    Toast.makeText(this, "Es necesario otorgar los permisos básicos", Toast.LENGTH_SHORT).show()
                    requestBasicPermissions()
                    return@setOnClickListener
                }

                // Check if the GPS is activated before sending the data
                if (!isGPSEnabled()) {
                    requestGPSActivation()
                    return@setOnClickListener
                }

                currentHost1 = DNS_HOST_1
                currentHost2 = DNS_HOST_2
                currentHost3 = DNS_HOST_3
                currentHost4 = DNS_HOST_4

                // Check if background permission is needed and available
                if (!backgroundPermissionGranted) {
                    requestBackgroundLocationPermission()
                    return@setOnClickListener
                }

                changeTransmission()
                startTransmission()

            } else {
                // Stopping transmission
                changeTransmission()
                stopTransmission()
            }
        }
    }

    private fun startTransmission() {
        if (isAppInForeground) {
            // Use local transmission
            startForegroundTransmission()
        } else {
            // Use service only if we have background permission
            if (backgroundPermissionGranted) {
                startBackgroundService()
            } else {
                Toast.makeText(this, "Se requiere permiso de ubicación en segundo plano para transmisión continua", Toast.LENGTH_LONG).show()
                requestBackgroundLocationPermission()
            }
        }
    }

    private fun stopTransmission() {
        try {
            stopForegroundTransmission()
            stopBackgroundService()
            Log.d("TRANSMISSION", "All transmissions stopped")
        } catch (e: Exception) {
            Log.e("TRANSMISSION", "Error stopping transmission", e)
        }
    }

    private fun startForegroundTransmission() {
        // Cancel any existing transmission first
        transmissionJob?.cancel()
        transmissionJob = null

        val host1 = currentHost1
        val host2 = currentHost2
        val host3 = currentHost3  // Nuevo
        val host4 = currentHost4  // Nuevo

        if (!areCurrentHostsValid()) {
            Toast.makeText(this, "Hosts no válidos", Toast.LENGTH_SHORT).show()
            changeTransmission()
            return
        }

        transmissionJob = coroutineScope.launch {
            try {
                while (transmission_state && isAppInForeground && isActive) {
                    val prevTime = tvDateTime.text.toString()
                    delay(1000)
                    val currentTime = tvDateTime.text.toString()

                    // Check if we're still in foreground and transmission is active
                    if (transmission_state && isAppInForeground && prevTime != currentTime && currentLocation != null) {
                        sendLocationUDP(host1, host2, host3, host4)  // Actualizado
                    }
                }
            } catch (e: CancellationException) {
                Log.d("TRANSMISSION", "Foreground transmission cancelled")
            } catch (e: Exception) {
                Log.e("TRANSMISSION", "Error in foreground transmission", e)
                withContext(Dispatchers.Main) {
                    if (transmission_state) {
                        Toast.makeText(this@MainActivity, "Error en transmisión: ${e.message}", Toast.LENGTH_SHORT).show()
                        changeTransmission()
                        stopTransmission()
                    }
                }
            }
        }
    }

    private fun stopForegroundTransmission() {
        try {
            transmissionJob?.cancel()
            transmissionJob = null
            Log.d("TRANSMISSION", "Foreground transmission stopped")
        } catch (e: Exception) {
            Log.e("TRANSMISSION", "Error stopping foreground transmission", e)
        }
    }

    private fun startBackgroundService() {
        try {
            val serviceIntent = Intent(this, LocationBackgroundService::class.java).apply {
                action = "START_TRANSMISSION"
                putExtra("HOST1", currentHost1)
                putExtra("HOST2", currentHost2)
                putExtra("HOST3", currentHost3)
                putExtra("HOST4", currentHost4)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d("SERVICE_STATE", "Background service started")
        } catch (e: Exception) {
            Log.e("SERVICE", "Error starting background service", e)
        }
    }

    private fun stopBackgroundService() {
        try {
            val serviceIntent = Intent(this, LocationBackgroundService::class.java).apply {
                action = "STOP_TRANSMISSION"
            }
            stopService(serviceIntent)
            Log.d("SERVICE_STATE", "Background service stopped")
        } catch (e: Exception) {
            Log.e("SERVICE", "Error stopping background service", e)
        }
    }

    private suspend fun resolveHost(host: String): String? = withContext(Dispatchers.IO) {
        try {
            // Verificar si es una IP válida (no necesita resolución)
            if (isValidIP(host)) {
                return@withContext host
            }

            // Verificar caché DNS
            val cacheKey = "${host}_${System.currentTimeMillis() / DNS_CACHE_TIMEOUT}"
            dnsCache[cacheKey]?.let { cachedIP ->
                Log.d("DNS", "Using cached IP for $host: $cachedIP")
                return@withContext cachedIP
            }

            // Resolver DNS
            val address = InetAddress.getByName(host)
            val resolvedIP = address.hostAddress

            // Guardar en caché
            dnsCache.clear() // Limpiar caché antigua
            dnsCache[cacheKey] = resolvedIP ?: host

            Log.d("DNS", "Resolved $host to $resolvedIP")
            return@withContext resolvedIP
        } catch (e: UnknownHostException) {
            Log.e("DNS", "Failed to resolve host: $host", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e("DNS", "Error resolving host: $host", e)
            return@withContext null
        }
    }

    private fun isValidIP(ip: String): Boolean {
        val ipPattern = """^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""".toRegex()
        return ipPattern.matches(ip)
    }

    private fun sendLocationUDP(host1: String, host2: String, host3: String, host4: String, port: Int = 4665) {
        // First check if GPS is still enabled
        if (!isGPSEnabled()) {
            Toast.makeText(this, "GPS desactivado durante transmisión", Toast.LENGTH_SHORT).show()
            changeTransmission()
            stopTransmission()
            requestGPSActivation()
            return
        }

        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, "No hay ubicación disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val message = formatLocationMessage(location)

        coroutineScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                val buffer = message.toByteArray()

                // Lista de hosts para procesar
                val hosts = listOf(
                    Pair("host1", host1),
                    Pair("host2", host2),
                    Pair("host3", host3),
                    Pair("host4", host4)
                )

                // Resolver y enviar a cada host
                hosts.forEach { (hostName, hostAddress) ->
                    if (hostAddress.isNotEmpty()) {
                        val resolvedIP = resolveHost(hostAddress)
                        if (resolvedIP != null) {
                            val address = InetAddress.getByName(resolvedIP)
                            val packet = DatagramPacket(buffer, buffer.size, address, port)
                            socket.send(packet)
                            Log.d("UDP", "Location sent successfully to $hostName: $hostAddress ($resolvedIP)")
                        } else {
                            Log.e("UDP", "Failed to resolve $hostName: $hostAddress")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Error: No se pudo resolver $hostAddress", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("UDP", "Error sending UDP packets", e)
                withContext(Dispatchers.Main) {
                    if (transmission_state) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error UDP: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        changeTransmission()
                        stopTransmission()
                    }
                }
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    Log.e("UDP", "Error closing socket", e)
                }
            }
        }
    }

    private fun formatLocationMessage(location: Location): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val locationDateTime = Date(location.time)
        val formattedDateTime = dateFormat.format(locationDateTime)

        return """
        {
            "latitude": ${String.format(Locale.US, "%.6f", location.latitude)},
            "longitude": ${String.format(Locale.US, "%.6f", location.longitude)},
            "timestamp": "$formattedDateTime"
        }
        """.trimIndent()
    }

    private fun changeTransmission(): Boolean {
        transmission_state = !transmission_state

        if (transmission_state) {
            // Starting transmission
            btnSendUDP.text = "Detener transmisión de datos"

        } else {
            // Stopping transmission
            btnSendUDP.text = "Iniciar transmisión de datos"

        }

        return transmission_state
    }

    private fun areCurrentHostsValid(): Boolean {
        return true
    }

    private fun updateUIWithLocation(location: Location) {
        runOnUiThread {
            // Update Latitude with 6 decimal places
            tvLatitude.text = String.format("%.6f", location.latitude)

            // Update Longitude with 6 decimal places
            tvLongitude.text = String.format("%.6f", location.longitude)

            // Update date and hour
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val locationDateTime = Date(location.time)
            tvDateTime.text = dateFormat.format(locationDateTime)
        }
    }

    // App lifecycle methods for state detection
    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        Log.d("APP_STATE", "App resumed - moving to foreground")

        // Check if GPS is enabled
        if (isGPSEnabled()) {
            // Request permissions when app starts
            if (!checkBasicPermissions()) {
                requestBasicPermissions()
            } else {
                // Only start if not already running
                if (locationUpdateJob == null || !locationUpdateJob!!.isActive) {
                    startLocationTracking()
                }
            }
        } else {
            // GPS is OFF, stop tracking and ask user to turn it on
            locationUpdateJob?.cancel()
            locationUpdateJob = null
            requestGPSActivation()

            // Si la transmisión está activa, la detenemos
            if (transmission_state) {
                changeTransmission()
                stopTransmission()
            }
        }

        if (transmission_state) {
            coroutineScope.launch {
                stopBackgroundService()
                delay(500)
                withContext(Dispatchers.Main) {
                    startForegroundTransmission()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
        Log.d("APP_STATE", "App paused - moving to background")

        if (transmission_state) {
            if (backgroundPermissionGranted) {
                coroutineScope.launch {
                    stopForegroundTransmission()
                    delay(500)
                    withContext(Dispatchers.Main) {
                        startBackgroundService()
                    }
                }
            } else {
                // No background permission, stop transmission
                Toast.makeText(this, "Transmisión pausada: Sin permiso de ubicación en segundo plano", Toast.LENGTH_LONG).show()
                changeTransmission()
                stopTransmission()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationUpdateJob?.cancel()
        transmissionJob?.cancel()
        coroutineScope.cancel()

        if (transmission_state) {
            stopBackgroundService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    basicPermissionsGranted = true
                    Toast.makeText(this, "Permisos básicos concedidos", Toast.LENGTH_SHORT).show()

                    // Check if we need background permission
                    if (checkBackgroundPermission()) {
                        backgroundPermissionGranted = true
                        onAllPermissionsReady()
                    } else {
                        // Start location tracking even without background permission
                        startLocationTracking()
                        Toast.makeText(this, "Para transmisión continua en segundo plano, se requiere permiso adicional", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Permisos básicos requeridos para el funcionamiento de la aplicación", Toast.LENGTH_LONG).show()
                }
            }
            BACKGROUND_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    backgroundPermissionGranted = true
                    Toast.makeText(this, "Permiso de ubicación en segundo plano concedido", Toast.LENGTH_SHORT).show()

                    if (areCurrentHostsValid() && !transmission_state) {
                        changeTransmission()
                        startTransmission()
                    }
                } else {
                    Toast.makeText(this, "Sin permiso de segundo plano, la transmisión funcionará solo cuando la app esté visible", Toast.LENGTH_LONG).show()

                    if (areCurrentHostsValid() && !transmission_state) {
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Transmisión limitada")
                            .setMessage("¿Deseas iniciar la transmisión? Solo funcionará cuando la app esté visible.")
                            .setPositiveButton("Iniciar") { _, _ ->
                                changeTransmission()
                                startTransmission()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            }
        }
    }
}

class LocationBackgroundService : Service() {
    private lateinit var locationManager: LocationManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isTransmitting = false
    private var lastSentTime: Long = 0
    private val UDP_PORT = 4665
    private var currentHost1: String = ""
    private var currentHost2: String = ""
    private var currentHost3: String = ""  // Nuevo
    private var currentHost4: String = ""  // Nuevo

    // DNS cache para el servicio
    private val dnsCache = mutableMapOf<String, String>()
    private val DNS_CACHE_TIMEOUT = 300000L // 5 minutos

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        startForeground(1, createNotification())
        Log.d("SERVICE", "LocationBackgroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TRANSMISSION" -> {
                currentHost1 = intent.getStringExtra("HOST1") ?: ""
                currentHost2 = intent.getStringExtra("HOST2") ?: ""
                currentHost3 = intent.getStringExtra("HOST3") ?: ""  // Nuevo
                currentHost4 = intent.getStringExtra("HOST4") ?: ""  // Nuevo

                val nonEmptyHosts = listOf(currentHost1, currentHost2, currentHost3, currentHost4)
                    .filter { it.isNotEmpty() }

                if (nonEmptyHosts.size >= 2) {
                    isTransmitting = true
                    startLocationUpdates()
                    Log.d("SERVICE", "Background transmission started with ${nonEmptyHosts.size} hosts")
                } else {
                    Log.e("SERVICE", "Insufficient valid hosts provided")
                    stopSelf()
                }
            }
            "STOP_TRANSMISSION" -> {
                isTransmitting = false
                stopSelf()
                Log.d("SERVICE", "Background transmission stopped")
            }
        }
        return START_STICKY
    }

    private fun isGPSEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "location_background_service"
            val channel = NotificationChannel(
                channelId,
                "Location Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background location transmission service"
                setSound(null, null)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "location_background_service"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Transmisión GPS Activa")
            .setContentText("Enviando ubicación en segundo plano...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSound(null)
            .build()
    }

    private fun startLocationUpdates() {
        coroutineScope.launch {
            while (isTransmitting) {
                try {
                    // Verificar GPS antes de cada actualización
                    if (!isGPSEnabled()) {
                        Log.e("SERVICE", "GPS disabled, stopping service")
                        isTransmitting = false
                        stopSelf()
                        break
                    }

                    getCurrentLocation()
                    delay(1000)
                } catch (e: Exception) {
                    Log.e("SERVICE", "Error in location updates", e)
                    if (isTransmitting) {
                        delay(5000) // Wait 5 seconds before retrying
                    }
                }
            }
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Check if GPS is enabled
            if (!isGPSEnabled()) {
                Log.e("SERVICE", "GPS not available")
                return
            }

            // Request the GPS location using LocationManager
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                { location ->
                    if (location.time != lastSentTime) {
                        lastSentTime = location.time
                        sendLocationData(location)
                    }
                },
                mainLooper
            )
        }
    }

    private suspend fun resolveHost(host: String): String? = withContext(Dispatchers.IO) {
        try {
            // Check if the IP is valid
            if (isValidIP(host)) {
                return@withContext host
            }

            // Check DNS cache
            val cacheKey = "${host}_${System.currentTimeMillis() / DNS_CACHE_TIMEOUT}"
            dnsCache[cacheKey]?.let { cachedIP ->
                Log.d("SERVICE_DNS", "Using cached IP for $host: $cachedIP")
                return@withContext cachedIP
            }

            // Solve DNS
            val address = InetAddress.getByName(host)
            val resolvedIP = address.hostAddress

            // Save cache
            dnsCache.clear() // Clean old cache
            dnsCache[cacheKey] = resolvedIP ?: host

            Log.d("SERVICE_DNS", "Resolved $host to $resolvedIP")
            return@withContext resolvedIP
        } catch (e: UnknownHostException) {
            Log.e("SERVICE_DNS", "Failed to resolve host: $host", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e("SERVICE_DNS", "Error resolving host: $host", e)
            return@withContext null
        }
    }

    private fun isValidIP(ip: String): Boolean {
        val ipPattern = """^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""".toRegex()
        return ipPattern.matches(ip)
    }

    private fun sendLocationData(location: Location) {
        coroutineScope.launch {
            try {
                val message = formatLocationMessage(location)

                // Lista de hosts para procesar
                val hosts = listOf(
                    Pair("host1", currentHost1),
                    Pair("host2", currentHost2),
                    Pair("host3", currentHost3),
                    Pair("host4", currentHost4)
                )

                // Resolver y enviar a cada host no vacío
                hosts.forEach { (hostName, hostAddress) ->
                    if (hostAddress.isNotEmpty()) {
                        val resolvedIP = resolveHost(hostAddress)
                        if (resolvedIP != null) {
                            sendUDPMessage(message, resolvedIP)
                            Log.d("SERVICE", "Location data sent to $hostName: $hostAddress ($resolvedIP)")
                        } else {
                            Log.e("SERVICE", "Failed to resolve $hostName: $hostAddress")
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("SERVICE", "Error sending location data", e)
            }
        }
    }

    private fun formatLocationMessage(location: Location): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val locationDateTime = Date(location.time)
        val formattedDateTime = dateFormat.format(locationDateTime)

        return """
        {
            "latitude": ${String.format(Locale.US, "%.6f", location.latitude)},
            "longitude": ${String.format(Locale.US, "%.6f", location.longitude)},
            "timestamp": "$formattedDateTime"
        }
        """.trimIndent()
    }

    private suspend fun sendUDPMessage(message: String, ipAddress: String) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                val address = InetAddress.getByName(ipAddress)
                val messageBytes = message.toByteArray()
                val packet = DatagramPacket(messageBytes, messageBytes.size, address, UDP_PORT)
                socket.send(packet)
                Log.d("SERVICE", "UDP message sent to $ipAddress")
            } catch (e: Exception) {
                Log.e("SERVICE", "Error sending UDP to $ipAddress: ${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    Log.e("SERVICE", "Error closing socket", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isTransmitting = false
        coroutineScope.cancel()
        Log.d("SERVICE", "LocationBackgroundService destroyed")
    }
}