package com.example.myhome

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Prayer times of a chosen mosque from MAWAQIT (mawaqit.net).
 * MAWAQIT has no officially documented public API, so this reads the same data the
 * mosque page / search uses. Every call can fail, so callers must fall back.
 */
object Mawaqit {
    data class Mosque(val uuid: String, val name: String, val place: String, val url: String)

    private val timeRe = Regex("(\\d{1,2})\\s*[:h.]\\s*(\\d{2})")

    /** Normalises a list of 6 time strings to "HH:mm", or null when it does not look right. */
    private fun normalize(arr: JSONArray?): List<String>? {
        if (arr == null || arr.length() < 6) return null
        val out = ArrayList<String>()
        for (i in 0 until 6) {
            val m = timeRe.find(arr.optString(i)) ?: return null
            val h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].toInt()
            if (h > 23 || mi > 59) return null
            out.add(String.format("%02d:%02d", h, mi))
        }
        return out
    }

    // ---------- search ----------

    fun search(query: String): List<Mosque> {
        val q = query.trim()
        if (q.matches(Regex("[0-9a-fA-F-]{24,40}"))) {
            return listOf(Mosque(q, "MAWAQIT", "", ""))
        }
        if (q.contains("mawaqit.net")) {
            val url = if (q.startsWith("http")) q else "https://$q"
            val conf = pageConf(url)
            val name = conf.optString("name").ifEmpty { url.substringAfterLast('/') }
            val place = conf.optString("localisation").ifEmpty { conf.optString("label") }
            return listOf(Mosque(conf.optString("uuid"), name, place, url))
        }
        val body = Net.get("https://mawaqit.net/api/2.0/mosque/search?word=" + URLEncoder.encode(q, "UTF-8"))
        val arr = toArray(body) ?: throw Exception("bad reply")
        val out = ArrayList<Mosque>()
        for (i in 0 until minOf(arr.length(), 20)) {
            val o = arr.optJSONObject(i) ?: continue
            val uuid = o.optString("uuid")
            val slug = o.optString("slug")
            var url = o.optString("url")
            if (url.isEmpty() && slug.isNotEmpty()) url = "https://mawaqit.net/en/$slug"
            if (uuid.isEmpty() && url.isEmpty()) continue
            val place = o.optString("localisation").ifEmpty { o.optString("label") }
            out.add(Mosque(uuid, o.optString("name").ifEmpty { slug }, place, url))
        }
        return out
    }

    private fun toArray(body: String): JSONArray? {
        val t = body.trim()
        if (t.startsWith("[")) return JSONArray(t)
        val o = JSONObject(t)
        val keys = o.keys()
        while (keys.hasNext()) {
            val v = o.opt(keys.next())
            if (v is JSONArray) return v
        }
        return null
    }

    // ---------- times ----------

    /** Today's six times (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) for the saved mosque. */
    fun timesFor(url: String, uuid: String): List<String> {
        var err = "no mosque"
        if (url.isNotEmpty()) {
            try {
                val conf = pageConf(url)
                normalize(conf.optJSONArray("times"))?.let { return it }
                todayFromCalendar(conf)?.let { return it }
                err = "no times in page"
            } catch (e: Exception) {
                err = Net.reason(e)
            }
        }
        if (uuid.isNotEmpty()) {
            try {
                val body = Net.get("https://mawaqit.net/api/2.0/mosque/$uuid/prayer-times")
                normalize(toArray(body))?.let { return it }
                err = "no times in api"
            } catch (e: Exception) {
                err = Net.reason(e)
            }
        }
        throw Exception(err)
    }

    private fun todayFromCalendar(conf: JSONObject): List<String>? {
        val cal = conf.optJSONArray("calendar") ?: return null
        val now = java.util.Calendar.getInstance()
        val month = cal.opt(now.get(java.util.Calendar.MONTH)) ?: return null
        val day = now.get(java.util.Calendar.DAY_OF_MONTH)
        val arr = when (month) {
            is JSONObject -> month.optJSONArray(day.toString())
            is JSONArray -> month.optJSONArray(day - 1) ?: month.optJSONArray(day)
            else -> null
        }
        return normalize(arr)
    }

    /** Loads the mosque page and returns the "confData" JSON object embedded in it. */
    private fun pageConf(url: String): JSONObject {
        val html = Net.get(url)
        val k = html.indexOf("confData")
        if (k < 0) throw Exception("not a mosque page")
        val start = html.indexOf('{', k)
        if (start < 0) throw Exception("no data")
        var depth = 0
        var inStr = false
        var esc = false
        var i = start
        while (i < html.length) {
            val c = html[i]
            if (inStr) {
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return JSONObject(html.substring(start, i + 1))
                    }
                }
            }
            i++
        }
        throw Exception("bad data")
    }
}
