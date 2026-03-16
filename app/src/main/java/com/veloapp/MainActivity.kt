package com.veloapp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var compassOverlay: CompassOverlay

    private lateinit var tvSpeed: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvMaxSpeed: TextView
    private lateinit var tvWeather: TextView
    private lateinit var tvSongTitle: TextView
    private lateinit var tvArtistName: TextView
    private lateinit var tvAlbumName: TextView
    private lateinit var ivAlbumArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPauseResume: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var musicVisualizerView: MusicVisualizerView
    private lateinit var btnDownloadZone: ImageButton
    private lateinit var tvDownloadStatus: TextView

    private var startTime: Long = 0
    private var pausedTime: Long = 0
    private var isPaused = false
    private var isTracking = false
    private var totalDistance = 0.0
    private var maxSpeedKmh = 0.0
    private var lastLocation: Location? = null
    private val routePoints = mutableListOf<GeoPoint>()
    private var routePolyline: Polyline? = null
    private var currentMarker: Marker? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    private lateinit var musicRunnable: Runnable
    private lateinit var weatherRunnable: Runnable

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1
        private const val CALORIES_PER_KM = 38.0
        private const val DOWNLOAD_RADIUS_DEG = 0.045
        private const val DOWNLOAD_MAX_ZOOM = 17
        private const val DOWNLOAD_MIN_ZOOM = 13
        private const val MAX_SPEED_FILTER_MS = 55f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Configuration.getInstance().load(this, getPreferences(Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvDistance = findViewById(R.id.tvDistance)
        tvDuration = findViewById(R.id.tvDuration)
        tvAvgSpeed = findViewById(R.id.tvAvgSpeed)
        tvCalories = findViewById(R.id.tvCalories)
        tvAltitude = findViewById(R.id.tvAltitude)
        tvMaxSpeed = findViewById(R.id.tvMaxSpeed)
        tvWeather = findViewById(R.id.tvWeather)
        tvSongTitle = findViewById(R.id.tvSongTitle)
        tvArtistName = findViewById(R.id.tvArtistName)
        tvAlbumName = findViewById(R.id.tvAlbumName)
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnPauseResume = findViewById(R.id.btnPauseResume)
        btnStop = findViewById(R.id.btnStop)
        btnHistory = findViewById(R.id.btnHistory)
        musicVisualizerView = findViewById(R.id.musicVisualizer)
        btnDownloadZone = findViewById(R.id.btnDownloadZone)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)

        val satelliteSource = object : XYTileSource(
            "ESRI_Satellite", 1, 19, 256, ".jpg",
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String =
                baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                        MapTileIndex.getY(pMapTileIndex) + "/" +
                        MapTileIndex.getX(pMapTileIndex)
        }
        mapView.setTileSource(satelliteSource)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(17.0)

        compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), mapView)
        compassOverlay.enableCompass()
        mapView.overlays.add(compassOverlay)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        MusicNotificationListener.onMusicUpdate = { runOnUiThread { updateMusicUI() } }

        btnDownloadZone.setOnClickListener { downloadZoneAroundMe() }
        btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        btnPrev.setOnClickListener { MusicNotificationListener.currentController?.transportControls?.skipToPrevious() }
        btnNext.setOnClickListener { MusicNotificationListener.currentController?.transportControls?.skipToNext() }
        btnPlayPause.setOnClickListener {
            MusicNotificationListener.currentController?.let {
                if (MusicNotificationListener.isPlaying) it.transportControls.pause()
                else it.transportControls.play()
            }
        }
        btnPauseResume.setOnClickListener {
            if (!isTracking) return@setOnClickListener
            if (!isPaused) {
                isPaused = true
                pausedTime = System.currentTimeMillis()
                locationManager.removeUpdates(this)
                btnPauseResume.setImageResource(android.R.drawable.ic_media_play)
            } else {
                isPaused = false
                startTime += System.currentTimeMillis() - pausedTime
                try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this) } catch (_: Exception) {}
                try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 2f, this) } catch (_: Exception) {}
                btnPauseResume.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
        btnStop.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Arrêter le trajet ?")
                .setMessage("Il sera sauvegardé dans l'historique.")
                .setPositiveButton("Oui") { _, _ ->
                    val elapsed = System.currentTimeMillis() - startTime
                    val km = totalDistance / 1000.0
                    val hrs = elapsed / 3600000.0
                    RideStorage.saveRide(this, RideRecord(
                        date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date()),
                        distanceKm = km, durationMs = elapsed,
                        avgSpeedKmh = if (hrs > 0.001) km / hrs else 0.0,
                        maxSpeedKmh = maxSpeedKmh,
                        calories = (km * CALORIES_PER_KM).toInt()
                    ))
                    Toast.makeText(this, "✅ Trajet sauvegardé !", Toast.LENGTH_SHORT).show()
                    totalDistance = 0.0; maxSpeedKmh = 0.0; routePoints.clear()
                    routePolyline?.setPoints(emptyList()); lastLocation = null
                    isPaused = false; startTime = System.currentTimeMillis()
                    btnPauseResume.setImageResource(android.R.drawable.ic_media_pause)
                }
                .setNegativeButton("Non", null).show()
        }

        timerRunnable = object : Runnable {
            override fun run() {
                if (isTracking && !isPaused) {
                    val elapsed = System.currentTimeMillis() - startTime
                    tvDuration.text = String.format("%02d:%02d:%02d",
                        TimeUnit.MILLISECONDS.toHours(elapsed),
                        TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60,
                        TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60)
                    val km = totalDistance / 1000.0
                    val hrs = elapsed / 3600000.0
                    tvAvgSpeed.text = String.format("%.1f km/h", if (hrs > 0.001) km / hrs else 0.0)
                    tvCalories.text = "${(km * CALORIES_PER_KM).toInt()} kcal"
                    tvDistance.text = String.format("%.2f km", km)
                    tvMaxSpeed.text = String.format("%.0f km/h max", maxSpeedKmh)
                }
                handler.postDelayed(this, 1000)
            }
        }
        musicRunnable = object : Runnable {
            override fun run() {
                if (isNotificationListenerEnabled()) {
                    MusicNotificationListener.refreshFromSessions(mediaSessionManager,
                        ComponentName(this@MainActivity, MusicNotificationListener::class.java))
                    musicVisualizerView.setPlaying(MusicNotificationListener.isPlaying)
                } else musicVisualizerView.setPlaying(audioManager.isMusicActive)
                handler.postDelayed(this, 1000)
            }
        }
        weatherRunnable = object : Runnable {
            override fun run() {
                fetchWeather()
                handler.postDelayed(this, 600000)
            }
        }

        if (!isNotificationListenerEnabled()) {
            Toast.makeText(this, "Autorise l'accès aux notifications pour la musique", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        else startTracking()
    }

    private fun fetchWeather() {
        val loc = lastLocation ?: return
        Thread {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}&current_weather=true"
                val json = JSONObject(URL(url).readText())
                val cw = json.getJSONObject("current_weather")
                val temp = cw.getDouble("temperature").toInt()
                val icon = when (val code = cw.getInt("weathercode")) {
                    0 -> "☀️"; in 1..3 -> "🌤️"; in 45..48 -> "🌫️"
                    in 51..67 -> "🌧️"; in 71..77 -> "🌨️"; in 80..82 -> "🌦️"
                    else -> if (code >= 95) "⛈️" else "🌡️"
                }
                handler.post { tvWeather.text = "$icon $temp°C" }
            } catch (_: Exception) {}
        }.start()
    }

    private fun isNotificationListenerEnabled(): Boolean =
        (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)

    private fun updateMusicUI() {
        tvSongTitle.text = MusicNotificationListener.songTitle.ifEmpty { "Aucune musique" }
        tvArtistName.text = MusicNotificationListener.artistName
        tvAlbumName.text = MusicNotificationListener.albumName
        val art = MusicNotificationListener.albumArt
        if (art != null) ivAlbumArt.setImageBitmap(art)
        else ivAlbumArt.setImageResource(android.R.drawable.ic_media_play)
        btnPlayPause.setImageResource(
            if (MusicNotificationListener.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startTracking()
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this) } catch (_: Exception) {}
        try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 2f, this) } catch (_: Exception) {}
        isTracking = true; startTime = System.currentTimeMillis()
        handler.post(timerRunnable); handler.post(musicRunnable); handler.post(weatherRunnable)
    }

    override fun onLocationChanged(location: Location) {
        val last = lastLocation
        if (last != null) {
            val timeSec = (location.time - last.time) / 1000f
            if (timeSec > 0 && last.distanceTo(location) / timeSec > MAX_SPEED_FILTER_MS) return
        }
        val geo = GeoPoint(location.latitude, location.longitude)
        mapView.controller.animateTo(geo)
        if (currentMarker == null) {
            currentMarker = Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
            mapView.overlays.add(currentMarker)
        }
        currentMarker?.position = geo
        if (!isPaused) {
            routePoints.add(geo)
            if (routePolyline == null) {
                routePolyline = Polyline().apply {
                    outlinePaint.color = android.graphics.Color.parseColor("#FF5722")
                    outlinePaint.strokeWidth = 10f; outlinePaint.isAntiAlias = true
                }
                mapView.overlays.add(0, routePolyline)
            }
            routePolyline?.setPoints(routePoints)
            last?.let { if (it.distanceTo(location) > 2f) totalDistance += it.distanceTo(location) }
        }
        lastLocation = location
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        tvSpeed.text = String.format("%.0f", speedKmh)
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh.toDouble()
        if (location.hasAltitude()) tvAltitude.text = "${location.altitude.toInt()}m"
        mapView.invalidate()
    }

    private fun downloadZoneAroundMe() {
        val center = lastLocation
        if (center == null) { Toast.makeText(this, "⏳ GPS pas encore fixé…", Toast.LENGTH_LONG).show(); return }
        tvDownloadStatus.text = "⬇️ Calcul…"; tvDownloadStatus.visibility = View.VISIBLE; btnDownloadZone.isEnabled = false
        val r = DOWNLOAD_RADIUS_DEG
        val bbox = BoundingBox(center.latitude + r, center.longitude + r, center.latitude - r, center.longitude - r)
        Thread {
            try {
                val tileProvider = mapView.tileProvider; val allTiles = mutableListOf<Long>()
                for (zoom in DOWNLOAD_MIN_ZOOM..DOWNLOAD_MAX_ZOOM) {
                    val xMin = floor((bbox.lonWest + 180.0) / 360.0 * (1 shl zoom)).toInt()
                    val xMax = floor((bbox.lonEast + 180.0) / 360.0 * (1 shl zoom)).toInt()
                    val yMin = floor((1.0 - ln(tan(Math.toRadians(bbox.latNorth)) + 1.0 / cos(Math.toRadians(bbox.latNorth))) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
                    val yMax = floor((1.0 - ln(tan(Math.toRadians(bbox.latSouth)) + 1.0 / cos(Math.toRadians(bbox.latSouth))) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
                    for (x in xMin..xMax) for (y in yMin..yMax) allTiles.add(MapTileIndex.getTileIndex(zoom, x, y))
                }
                val total = allTiles.size
                allTiles.forEachIndexed { i, t ->
                    tileProvider.getMapTile(t)
                    if (i % 10 == 0) handler.post { tvDownloadStatus.text = "⬇️ ${i + 1} / $total" }
                    Thread.sleep(15)
                }
                handler.post { tvDownloadStatus.text = "✅ OK !"; btnDownloadZone.isEnabled = true; handler.postDelayed({ tvDownloadStatus.visibility = View.GONE }, 5000) }
            } catch (_: Exception) { handler.post { tvDownloadStatus.text = "❌ Erreur"; btnDownloadZone.isEnabled = true } }
        }.start()
    }

    override fun onResume() { super.onResume(); mapView.onResume(); compassOverlay.enableCompass() }
    override fun onPause() { super.onPause(); mapView.onPause(); compassOverlay.disableCompass() }
    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        handler.removeCallbacks(timerRunnable)
        handler.removeCallbacks(musicRunnable)
        handler.removeCallbacks(weatherRunnable)
    }
}
