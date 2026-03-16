package com.veloapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class RideRecord(
    val date: String,
    val distanceKm: Double,
    val durationMs: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val calories: Int
)

object RideStorage {
    private const val PREF_KEY = "ride_history"
    private const val PREFS_NAME = "veloapp_prefs"

    fun saveRide(context: Context, record: RideRecord) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(PREF_KEY, "[]"))
        arr.put(JSONObject().apply {
            put("date", record.date)
            put("distanceKm", record.distanceKm)
            put("durationMs", record.durationMs)
            put("avgSpeedKmh", record.avgSpeedKmh)
            put("maxSpeedKmh", record.maxSpeedKmh)
            put("calories", record.calories)
        })
        prefs.edit().putString(PREF_KEY, arr.toString()).apply()
    }

    fun loadRides(context: Context): List<RideRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(PREF_KEY, "[]") ?: "[]")
        return (arr.length() - 1 downTo 0).map {
            val o = arr.getJSONObject(it)
            RideRecord(o.getString("date"), o.getDouble("distanceKm"),
                o.getLong("durationMs"), o.getDouble("avgSpeedKmh"),
                o.getDouble("maxSpeedKmh"), o.getInt("calories"))
        }
    }
}
