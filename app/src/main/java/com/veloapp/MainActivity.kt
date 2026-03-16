package com.veloapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.concurrent.TimeUnit
import kotlin.math.*

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var audioManager: AudioManager
    private lateinit var tvSpeed: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvSongInfo: TextView
    private lateinit var musicVisualizerView: MusicVisualizerView
    private lateinit var btnDownloadZone: ImageButton
    private lateinit var tvDownloadStatus: TextView
    private var startTime: Long = 0
    private var isTracking = false
    private var totalDistance = 0.0
    private var lastLocation: Location? = null
    private val routePoints = mutableListOf<GeoPoint>()
    private var routePolyline: Polyline? = null
    private var currentMarker: Marker? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    private lateinit var musicRunnable: Runnable

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1
        private const val CALORIES_PER_KM = 38.0
        private const val DOWNLOAD_RADIUS_DEG = 0.045
        private const val DOWNLOAD_MAX_ZOOM = 17
        private const val DOWNLOAD_MIN_ZOOM = 13
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
        tvSongInfo = findViewById(R.id.tvSongInfo)
        musicVisualizerView = findViewById(R.id.musicVisualizer)
        btnDownloadZone = findViewById(R.id.btnDownloadZone)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(17.0)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        btnDownloadZone.setOnClickListener { downloadZoneAroundMe() }
        timerRunnable = object : Runnable {
            override fun run() {
                if (isTracking) {
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
                }
                handler.postDelayed(this, 1000)
            }
        }
        musicRunnable = object : Runnable {
            override fun run() {
                val playing = audioManager.isMusicActive
                musicVisualizerView.setPlaying(playing)
                if (!playing) tvSongInfo.text = "🎵 Aucune musique"
                else if (tvSongInfo.text == "🎵 Aucune musique") tvSongInfo.text = "🎵 En cours de lecture…"
                handler.postDelayed(this, 500)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        else startTracking()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            startTracking()
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this) } catch (_: Exception) {}
        try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 2f, this) } catch (_: Exception) {}
        isTracking = true
        startTime = System.currentTimeMillis()
        handler.post(timerRunnable)
        handler.post(musicRunnable)
    }

    override fun onLocationChanged(location: Location) {
        val geo = GeoPoint(location.latitude, location.longitude)
        mapView.controller.animateTo(geo)
        if (currentMarker == null) {
            currentMarker = Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
            mapView.overlays.add(currentMarker)
        }
        currentMarker?.position = geo
        routePoints.add(geo)
        if (routePolyline == null) {
            routePolyline = Polyline().apply {
                outlinePaint.color = android.graphics.Color.parseColor("#FF5722")
                outlinePaint.strokeWidth = 10f
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(0, routePolyline)
        }
        routePolyline?.setPoints(routePoints)
        lastLocation?.let { if (it.distanceTo(location) > 2f) totalDistance += it.distanceTo(location) }
        lastLocation = location
        tvSpeed.text = String.format("%.0f", if (location.hasSpeed()) location.speed * 3.6f else 0f)
        mapView.invalidate()
    }

    private fun downloadZoneAroundMe() {
        val center = lastLocation
        if (center == null) {
            Toast.makeText(this, "⏳ GPS pas encore fixé…", Toast.LENGTH_LONG).show()
            return
        }
        tvDownloadStatus.text = "⬇️ Calcul des tuiles…"
        tvDownloadStatus.visibility = View.VISIBLE
        btnDownloadZone.isEnabled = false
        val r = DOWNLOAD_RADIUS_DEG
        val bbox = BoundingBox(center.latitude + r, center.longitude + r, center.latitude - r, center.longitude - r)
        Thread {
            try {
                val tileProvider = mapView.tileProvider
                val allTiles = mutableListOf<Long>()
                for (zoom in DOWNLOAD_MIN_ZOOM..DOWNLOAD_MAX_ZOOM) {
                    val xMin = floor((bbox.lonWest + 180.0) / 360.0 * (1 shl zoom)).toInt()
                    val xMax = floor((bbox.lonEast + 180.0) / 360.0 * (1 shl zoom)).toInt()
                    val yMin = floor((1.0 - ln(tan(Math.toRadians(bbox.latNorth)) + 1.0 / cos(Math.toRadians(bbox.latNorth))) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
                    val yMax = floor((1.0 - ln(tan(Math.toRadians(bbox.latSouth)) + 1.0 / cos(Math.toRadians(bbox.latSouth))) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
                    for (x in xMin..xMax) for (y in yMin..yMax)
                        allTiles.add(MapTileIndex.getTileIndex(zoom, x, y))
                }
                val total = allTiles.size
                handler.post { tvDownloadStatus.text = "⬇️ 0 / $total tuiles…" }
                allTiles.forEachIndexed { index, tileIndex ->
                    tileProvider.getMapTile(tileIndex)
                    if (index % 10 == 0) handler.post { tvDownloadStatus.text = "⬇️ ${index + 1} / $total tuiles…" }
                    Thread.sleep(15)
                }
                handler.post {
                    tvDownloadStatus.text = "✅ Zone enregistrée — hors ligne OK !"
                    btnDownloadZone.isEnabled = true
                    handler.postDelayed({ tvDownloadStatus.visibility = View.GONE }, 5000)
                }
            } catch (e: Exception) {
                handler.post {
                    tvDownloadStatus.text = "❌ Erreur — vérifie ta connexion"
                    btnDownloadZone.isEnabled = true
                }
            }
        }.start()
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        handler.removeCallbacks(timerRunnable)
        handler.removeCallbacks(musicRunnable)
    }
}
