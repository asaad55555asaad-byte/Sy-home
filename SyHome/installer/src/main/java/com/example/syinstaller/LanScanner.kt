package com.example.syinstaller

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class Subnet(val prefix: String, val own: String)

object LanScanner {
    // Mobile-data interfaces are skipped; Wi-Fi and hotspot interfaces are scanned
    private val skip = listOf("rmnet", "ccmni", "pdp", "tun", "dummy", "v4-", "ppp")

    fun subnets(): List<Subnet> {
        val out = LinkedHashMap<String, Subnet>()
        try {
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp || ni.isLoopback) continue
                val n = ni.name.lowercase()
                if (skip.any { n.startsWith(it) }) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address
                    if (a is Inet4Address && a.isSiteLocalAddress) {
                        val ip = a.hostAddress ?: continue
                        val prefix = ip.substringBeforeLast(".") + "."
                        out[prefix] = Subnet(prefix, ip)
                    }
                }
            }
        } catch (e: Exception) {
        }
        return out.values.toList()
    }
}
