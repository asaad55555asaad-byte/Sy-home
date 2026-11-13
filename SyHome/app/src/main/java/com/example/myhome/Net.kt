package com.example.myhome

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Net {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** Plain GET. Throws an Exception whose message says what went wrong (HTTP code, timeout...). */
    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 15000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
        c.setRequestProperty("Accept-Encoding", "gzip")
        c.setRequestProperty("Accept-Language", "en")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        return try {
            val code = c.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")
            val raw = c.inputStream
            val stream = if ((c.contentEncoding ?: "").contains("gzip", ignoreCase = true)) {
                GZIPInputStream(raw)
            } else raw
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Short reason text for the on-screen status line. */
    fun reason(e: Throwable): String {
        val m = e.message ?: e.javaClass.simpleName
        return if (m.length > 40) m.take(40) else m
    }
}
