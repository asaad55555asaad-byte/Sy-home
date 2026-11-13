package com.example.myhome

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

 data class Match(
    val startMs: Long, val league: String, val state: String,
    val homeAbbr: String, val homeLogo: String, val homeScore: String,
    val awayAbbr: String, val awayLogo: String, val awayScore: String,
    val link: String,
    val homeName: String = "",
    val awayName: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("t", startMs).put("l", league).put("s", state)
        .put("ha", homeAbbr).put("hl", homeLogo).put("hs", homeScore)
        .put("aa", awayAbbr).put("al", awayLogo).put("as", awayScore).put("u", link)
        .put("hn", homeName).put("an", awayName)

    companion object {
        fun fromJson(o: JSONObject) = Match(
            o.getLong("t"), o.getString("l"), o.getString("s"),
            o.getString("ha"), o.getString("hl"), o.getString("hs"),
            o.getString("aa"), o.getString("al"), o.getString("as"), o.getString("u"),
            o.optString("hn", o.getString("ha")), o.optString("an", o.getString("aa"))
        )
    }
}

/** ESPN public soccer scoreboard. Failed leagues never invalidate successful leagues or cached data. */
object Matches {
    // ESPN scoreboard slugs that currently work (hyphenated UEFA paths return HTTP 400).
    val leagueIds = listOf(
        "uefa.nations", "uefa.champions", "uefa.europa",
        "eng.1", "esp.1", "ger.1", "ita.1", "fra.1", "ksa.1"
    )
    const val DEFAULT = "uefa.nations,uefa.champions,uefa.europa,eng.1,esp.1,ger.1,ita.1,fra.1,ksa.1"

    private fun utc(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseDate(s: String): Long? {
        // ESPN may return UTC Z timestamps or ISO-8601 timestamps with an offset.
        val normalized = s.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mmZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm'Z'"
        )
        for (pattern in patterns) {
            try {
                val formatter = if (pattern.endsWith("'Z'")) utc(pattern)
                    else SimpleDateFormat(pattern, Locale.US)
                formatter.isLenient = false
                formatter.parse(normalized)?.let { return it.time }
            } catch (_: Exception) { }
        }
        return null
    }

    @Volatile var lastError: String = ""

    /** Normalize any saved slug (from older builds) to a working ESPN path. */
    fun normalizeSlug(requested: String): String = when (requested) {
        "uefa.nations-league", "uefa.nations_league" -> "uefa.nations"
        "uefa.champions-league", "uefa.champions_league" -> "uefa.champions"
        "uefa.europa-league", "uefa.europa_league" -> "uefa.europa"
        "saudi-arabia.1", "saudi.1" -> "ksa.1"
        else -> requested
    }

    /** null means every requested league failed; an empty list is valid only after a successful response. */
    fun fetch(leagues: List<String>): List<Match>? {
        val now = System.currentTimeMillis()
        val dayFmt = utc("yyyyMMdd")
        val out = ArrayList<Match>()
        val failures = ArrayList<String>()
        var anySuccess = false

        for (requestedSlug in leagues.distinct()) {
            val slug = normalizeSlug(requestedSlug)
            var leagueSuccess = false
            val bodies = ArrayList<String>()
            val leagueErrors = ArrayList<String>()

            // ESPN rejects date ranges; query individual days. Sample a spread of days
            // so we stay under TV network timeouts while still covering ~2.5 weeks ahead.
            val offsets = intArrayOf(0, 1, 2, 3, 4, 5, 7, 9, 11, 14, 17)
            for (offset in offsets) {
                val date = dayFmt.format(Date(now + offset * 86_400_000L))
                try {
                    bodies.add(Net.get("https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/scoreboard?dates=$date&limit=100"))
                    leagueSuccess = true
                } catch (e: Exception) {
                    leagueErrors.add(Net.reason(e))
                }
            }
            // Fallback: plain scoreboard if day queries all failed (network / temporary outage).
            if (!leagueSuccess) {
                try {
                    bodies.add(Net.get("https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/scoreboard"))
                    leagueSuccess = true
                } catch (e: Exception) {
                    leagueErrors.add(Net.reason(e))
                }
            }
            if (!leagueSuccess) failures.add("${leagueErrors.lastOrNull() ?: "Request failed"} · $slug")

            if (leagueSuccess) {
                anySuccess = true
                for (body in bodies) {
                    try {
                        val root = JSONObject(body)
                        val leagueName = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() } ?: slug
                        val events = root.optJSONArray("events") ?: continue
                        for (i in 0 until events.length()) {
                            try { parseEvent(events.getJSONObject(i), leagueName, now)?.let { out.add(it) } }
                            catch (_: Exception) { }
                        }
                    } catch (_: Exception) { failures.add("Invalid response · $slug") }
                }
            }
        }
        lastError = failures.distinct().take(5).joinToString(" / ")
        if (!anySuccess) return null
        fun rank(m: Match) = when (m.state) { "in" -> 0; "pre" -> 1; else -> 2 }
        return out.distinctBy { "${it.startMs}${it.homeAbbr}${it.awayAbbr}" }
            .sortedWith(compareBy<Match>({ rank(it) }, { it.startMs })).take(60)
    }

    private fun parseEvent(ev: JSONObject, leagueName: String, now: Long): Match? {
        val start = parseDate(ev.optString("date")) ?: return null
        val state = ev.optJSONObject("status")?.optJSONObject("type")?.optString("state") ?: "pre"
        val keep = when (state) {
            "in" -> true
            "pre" -> start >= now - 3_600_000L && start <= now + 21L * 86_400_000L
            "post" -> now - start < 4 * 3_600_000L
            else -> false
        }
        if (!keep) return null
        val comps = ev.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors")
        var home: JSONObject? = null; var away: JSONObject? = null
        for (j in 0 until comps.length()) {
            val c = comps.getJSONObject(j)
            if (c.optString("homeAway") == "home") home = c else away = c
        }
        home ?: return null; away ?: return null
        fun team(c: JSONObject) = Triple(
            c.optJSONObject("team")?.optString("abbreviation")?.ifBlank { null }
                ?: c.optJSONObject("team")?.optString("shortDisplayName")?.take(3)?.uppercase()
                ?: "?",
            c.optJSONObject("team")?.optString("logo") ?: "",
            c.optString("score").ifBlank { "-" }
        )
        val (ha, hl, hs) = team(home); val (aa, al, ascore) = team(away)
        val hn = home.optJSONObject("team")?.optString("displayName")
            ?: home.optJSONObject("team")?.optString("shortDisplayName") ?: ha
        val an = away.optJSONObject("team")?.optString("displayName")
            ?: away.optJSONObject("team")?.optString("shortDisplayName") ?: aa
        val link = ev.optString("link").ifBlank {
            ev.optJSONArray("links")?.optJSONObject(0)?.optString("href") ?: ""
        }
        return Match(start, leagueName, state, ha, hl, hs, aa, al, ascore, link, hn, an)
    }

    fun toJsonString(list: List<Match>): String {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        return a.toString()
    }

    fun fromJsonString(s: String): List<Match> {
        if (s.isBlank()) return emptyList()
        val a = JSONArray(s)
        return (0 until a.length()).map { Match.fromJson(a.getJSONObject(it)) }
    }
}
