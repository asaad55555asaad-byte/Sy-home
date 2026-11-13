package com.example.myhome

import android.content.SharedPreferences

/** Defaults. The city and prayer method can be changed from the in-app settings. */
object Config {
    const val LAT = "51.4556"
    const val LON = "7.0116"
    const val CITY = "Essen"
    const val PRAYER_METHOD = 3

    fun lat(p: SharedPreferences): String = p.getString("lat", LAT) ?: LAT
    fun lon(p: SharedPreferences): String = p.getString("lon", LON) ?: LON
    fun city(p: SharedPreferences): String = p.getString("city", CITY) ?: CITY
    fun method(p: SharedPreferences): Int = p.getInt("method", PRAYER_METHOD)
}
