package com.example.myhome

import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Network refreshes shared by the home screen and the standby screen. */
object Refresh {
    private val prayerKeys = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

    @Volatile private var fetchingMatches = false
    @Volatile private var fetchingPrayers = false
    @Volatile private var fetchingWeather = false

    fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** Refreshes whatever is stale. [onDone] runs on a background thread when something changed. */
    fun ensure(prefs: SharedPreferences, onDone: () -> Unit) {
        val now = System.currentTimeMillis()
        if (prefs.getBoolean("showWeather", true) && now - prefs.getLong("weatherAt", 0) > 30 * 60_000L &&
            now - prefs.getLong("weatherTry", 0) > 3 * 60_000L
        ) weather(prefs, onDone)
        if (prefs.getBoolean("showPrayers", true) && prefs.getString("prayerDate", "") != today() &&
            now - prefs.getLong("prayerTry", 0) > 3 * 60_000L
        ) prayers(prefs, onDone)
        matchesIfStale(prefs, onDone)
    }

    // ---------- matches ----------

    fun matchesIfStale(prefs: SharedPreferences, onDone: () -> Unit) {
        // One-shot cache bust after the ESPN slug / day-query fix (v2.2).
        if (prefs.getInt("matchesFetchVer", 0) < 2) {
            prefs.edit().putInt("matchesFetchVer", 2).putLong("matchesAt", 0).putLong("matchesTry", 0).apply()
        }
        val rawIds = (prefs.getString("leagues", Matches.DEFAULT) ?: Matches.DEFAULT).split(",").filter { it.isNotEmpty() }
        val ids = rawIds.map { Matches.normalizeSlug(it) }.distinct()
        // Persist migrated slug names so future launches skip bad hyphenated paths.
        if (ids != rawIds) {
            prefs.edit().putString("leagues", ids.joinToString(",")).putLong("matchesAt", 0).apply()
        }
        if (!prefs.getBoolean("showMatches", true) || ids.isEmpty() || fetchingMatches) return
        val now = System.currentTimeMillis()
        // If cache has no upcoming fixtures, treat it as stale so a fixed fetcher can refill.
        val cached = prefs.getString("matches", "") ?: ""
        val hasUpcoming = try {
            Matches.fromJsonString(cached).any { it.state == "pre" || it.state == "in" }
        } catch (_: Exception) { false }
        val maxAge = if (hasUpcoming) 15 * 60_000L else 60_000L
        if (now - prefs.getLong("matchesAt", 0) < maxAge) return
        if (now - prefs.getLong("matchesTry", 0) < 3 * 60_000L) return
        fetchingMatches = true
        Thread {
            try {
                prefs.edit().putLong("matchesTry", System.currentTimeMillis()).apply()
                val list = Matches.fetch(ids)
                if (list != null) {
                    prefs.edit().putString("matches", Matches.toJsonString(list))
                        .putLong("matchesAt", System.currentTimeMillis()).putString("matchesErr", Matches.lastError).apply()
                } else {
                    prefs.edit().putString("matchesErr", Matches.lastError.ifEmpty { "no connection" }).apply()
                }
                onDone()
            } catch (e: Exception) {
                prefs.edit().putString("matchesErr", Net.reason(e)).apply()
            } finally {
                fetchingMatches = false
            }
        }.start()
    }

    // ---------- prayers ----------

    /** Mosque times from MAWAQIT when a mosque is chosen, otherwise (or if it fails) Aladhan. */
    fun prayers(prefs: SharedPreferences, onDone: () -> Unit) {
        if (fetchingPrayers) return
        fetchingPrayers = true
        Thread {
            try {
                prefs.edit().putLong("prayerTry", System.currentTimeMillis()).apply()
                var csv: String? = null
                var src = ""
                var err = ""
                val mUrl = prefs.getString("mosqueUrl", "") ?: ""
                val mUuid = prefs.getString("mosqueUuid", "") ?: ""
                if (mUrl.isNotEmpty() || mUuid.isNotEmpty()) {
                    try {
                        csv = Mawaqit.timesFor(mUrl, mUuid).joinToString(",")
                        src = "mawaqit"
                    } catch (e: Exception) {
                        err = "MAWAQIT: " + Net.reason(e)
                    }
                }
                if (csv == null) {
                    try {
                        val body = Net.get(
                            "https://api.aladhan.com/v1/timings?latitude=${Config.lat(prefs)}" +
                                "&longitude=${Config.lon(prefs)}&method=${Config.method(prefs)}"
                        )
                        val t = JSONObject(body).getJSONObject("data").getJSONObject("timings")
                        csv = prayerKeys.joinToString(",") { t.getString(it).take(5) }
                        src = "aladhan"
                    } catch (e: Exception) {
                        err = if (err.isEmpty()) Net.reason(e) else err + " / " + Net.reason(e)
                    }
                }
                if (csv == null) {
                    try {
                        csv = PrayerCalc.today(Config.lat(prefs), Config.lon(prefs), Config.method(prefs)).joinToString(",")
                        src = "offline"
                    } catch (e: Exception) {
                        err = if (err.isEmpty()) Net.reason(e) else err + " / " + Net.reason(e)
                    }
                }
                if (csv != null) {
                    prefs.edit().putString("prayers", csv).putString("prayerDate", today())
                        .putString("prayerSrc", src).putString("prayerErr", err).apply()
                } else {
                    prefs.edit().putString("prayerErr", err.ifEmpty { "no connection" }).apply()
                }
                onDone()
            } catch (e: Exception) {
                prefs.edit().putString("prayerErr", Net.reason(e)).apply()
            } finally {
                fetchingPrayers = false
            }
        }.start()
    }

    // ---------- weather ----------

    private fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌦️"
        85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun weather(prefs: SharedPreferences, onDone: () -> Unit) {
        if (fetchingWeather) return
        fetchingWeather = true
        Thread {
            try {
                prefs.edit().putLong("weatherTry", System.currentTimeMillis()).apply()
                val f = prefs.getBoolean("unitF", false)
                val unit = if (f) "fahrenheit" else "celsius"
                val body = Net.get(
                    "https://api.open-meteo.com/v1/forecast?latitude=${Config.lat(prefs)}" +
                        "&longitude=${Config.lon(prefs)}&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m" +
                        "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                        "&temperature_unit=$unit&timezone=auto"
                )
                val root = JSONObject(body)
                val cur = root.getJSONObject("current")
                val t = Math.round(cur.getDouble("temperature_2m")).toInt()
                val code = cur.getInt("weather_code")
                val humidity = cur.optInt("relative_humidity_2m", -1)
                val wind = cur.optInt("wind_speed_10m", -1)
                val daily = root.optJSONObject("daily")
                val hi = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)
                val lo = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)
                val txt = "${weatherIcon(code)} $t°${if (f) "F" else "C"}"
                val detail = buildString {
                    if (humidity >= 0) append(" · ").append(humidity).append("%")
                    if (wind >= 0) append(" · ").append(wind).append(" km/h")
                }
                val e = prefs.edit().putString("weather", txt + detail)
                    .putInt("whumidity", humidity).putInt("wwind", wind)
                    .putInt("wtemp", t).putInt("wcode", code)
                    .putLong("weatherAt", System.currentTimeMillis())
                if (hi != null && !hi.isNaN()) e.putInt("wmax", Math.round(hi).toInt())
                if (lo != null && !lo.isNaN()) e.putInt("wmin", Math.round(lo).toInt())
                if (daily != null) {
                    val highs = daily.optJSONArray("temperature_2m_max")
                    val lows = daily.optJSONArray("temperature_2m_min")
                    val days = daily.optJSONArray("time")
                    val forecast = org.json.JSONArray()
                    if (highs != null && lows != null && days != null) for (i in 0 until minOf(5, days.length(), highs.length(), lows.length())) {
                        forecast.put(JSONObject().put("date", days.optString(i)).put("high", highs.optInt(i)).put("low", lows.optInt(i)))
                    }
                    e.putString("weatherForecast", forecast.toString())
                }
                e.putString("weatherErr", "").apply()
                onDone()
            } catch (e: Exception) {
                prefs.edit().putString("weatherErr", Net.reason(e)).apply()
                onDone()
            } finally {
                fetchingWeather = false
            }
        }.start()
    }
}
