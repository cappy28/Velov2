package com.veloapp

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.TimeUnit

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#0A0A12"))
        }
        container.addView(TextView(this).apply {
            text = "📋 Historique des trajets"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 32)
        })
        val rides = RideStorage.loadRides(this)
        if (rides.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Aucun trajet enregistré pour l'instant.\nPremier trajet à venir ! 🚴"
                textSize = 15f
                setTextColor(android.graphics.Color.GRAY)
            })
        } else {
            rides.forEach { ride ->
                val h = TimeUnit.MILLISECONDS.toHours(ride.durationMs)
                val m = TimeUnit.MILLISECONDS.toMinutes(ride.durationMs) % 60
                val s = TimeUnit.MILLISECONDS.toSeconds(ride.durationMs) % 60
                container.addView(TextView(this).apply {
                    text = "🗓  ${ride.date}\n" +
                           "📏  ${String.format("%.2f", ride.distanceKm)} km   ⏱  ${String.format("%02d:%02d:%02d", h, m, s)}\n" +
                           "⚡  Moy: ${String.format("%.1f", ride.avgSpeedKmh)} km/h   🏆  Max: ${String.format("%.1f", ride.maxSpeedKmh)} km/h\n" +
                           "🔥  ${ride.calories} kcal"
                    textSize = 14f
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(20, 20, 20, 20)
                    setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
                })
                container.addView(android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 12)
                })
            }
        }
        scroll.addView(container)
        setContentView(scroll)
    }
}
