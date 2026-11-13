package com.example.myhome

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/** Lightweight offline solar prayer-time fallback. Angles follow the selected Aladhan method. */
object PrayerCalc {
    fun today(latValue: String, lonValue: String, method: Int): List<String> {
        val lat = latValue.toDouble(); val lon = lonValue.toDouble()
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_YEAR)
        val yearDays = if (now.getActualMaximum(Calendar.DAY_OF_YEAR) == 366) 366.0 else 365.0
        val gamma = 2.0 * Math.PI / yearDays * (day - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val tz = TimeZone.getDefault().getOffset(now.timeInMillis) / 3_600_000.0
        val noon = 12.0 + tz - lon / 15.0 - eqTime / 60.0
        val latitude = Math.toRadians(lat.coerceIn(-89.0, 89.0))
        fun hourAngle(altitudeDeg: Double): Double {
            val alt = Math.toRadians(altitudeDeg)
            val c = (sin(alt) - sin(latitude) * sin(decl)) / (cos(latitude) * cos(decl))
            return Math.toDegrees(acos(c.coerceIn(-1.0, 1.0)))
        }
        val (fajrAngle, ishaAngle) = when (method) {
            0 -> 16.0 to 14.0
            1 -> 15.0 to 15.0
            2 -> 18.5 to 0.0 // Umm al-Qura: Isha is a fixed interval after Maghrib
            4 -> 18.0 to 18.0
            5 -> 19.5 to 17.5
            13 -> 18.0 to 17.0
            else -> 18.0 to 17.0
        }
        val sunrise = noon - hourAngle(-0.833) / 15.0
        val sunset = noon + hourAngle(-0.833) / 15.0
        val fajr = noon - hourAngle(-fajrAngle) / 15.0
        val asrAltitude = Math.toDegrees(atan(1.0 / (1.0 + tan(abs(latitude - decl)))))
        val asr = noon + hourAngle(asrAltitude) / 15.0
        val maghrib = sunset
        val isha = if (method == 2) maghrib + 1.5 else noon + hourAngle(-ishaAngle) / 15.0
        fun fmt(hours: Double): String {
            var mins = (hours * 60.0).roundToInt()
            mins = ((mins % 1440) + 1440) % 1440
            return "%02d:%02d".format(mins / 60, mins % 60)
        }
        return listOf(fmt(fajr), fmt(sunrise), fmt(noon + 2.0 / 60.0), fmt(asr), fmt(maghrib), fmt(isha))
    }
}
