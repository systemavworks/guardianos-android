package com.guardianos.core.pro.network

import android.content.Context
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Análisis de conexiones de red activas (PRO).
 * Lee /proc/net/tcp y /proc/net/tcp6, mapea IPs a países y reputación.
 * Incluye caché de geolocalización para optimizar rendimiento.
 */
object NetworkAnalyzer {
    private val geoIpCache = ConcurrentHashMap<String, String>()
    private val reputationCache = ConcurrentHashMap<String, String>()
    
    fun getActiveConnections(): List<NetworkConnectionInfo> {
        val connections = mutableListOf<NetworkConnectionInfo>()
        connections.addAll(parseProcNetFile("/proc/net/tcp"))
        connections.addAll(parseProcNetFile("/proc/net/tcp6"))
        return connections
    }

    private fun parseProcNetFile(path: String): List<NetworkConnectionInfo> {
        val result = mutableListOf<NetworkConnectionInfo>()
        try {
            val lines = java.io.File(path).readLines()
            for (line in lines.drop(1)) { // Saltar cabecera
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 10) continue
                val local = parts[1].split(":")
                val remote = parts[2].split(":")
                val localIp = hexToIp(local[0], path.endsWith("6"))
                val remoteIp = hexToIp(remote[0], path.endsWith("6"))
                val port = Integer.parseInt(local[1], 16)
                
                // Determinar país y reputación con caché
                val country = getCountryFromIp(remoteIp)
                val reputation = getIpReputation(remoteIp)
                
                result.add(NetworkConnectionInfo(
                    localIp = localIp,
                    remoteIp = remoteIp,
                    remoteCountry = country,
                    remoteReputation = reputation,
                    port = port
                ))
            }
        } catch (_: Exception) {}
        return result
    }

    private fun hexToIp(hex: String, isIpv6: Boolean): String {
        return if (!isIpv6) {
            val bytes = hex.chunked(2).map { it.toInt(16) }.reversed()
            bytes.joinToString(".")
        } else {
            // IPv6: 32 caracteres hex
            hex.chunked(4).joinToString(":")
        }
    }
    
    /**
     * Determina el país de una IP usando heurística local (sin APIs externas).
     * Implementación básica que identifica rangos conocidos.
     */
    private fun getCountryFromIp(ip: String): String? {
        if (ip.startsWith("0.0.0.0") || ip.startsWith("127.") || ip.startsWith("10.") || 
            ip.startsWith("192.168.") || ip.startsWith("172.")) {
            return "Local/Privada"
        }
        
        // Caché de lookups previos
        geoIpCache[ip]?.let { return it }
        
        // Heurística básica: Análisis de rangos IP conocidos
        // En producción, usar base de datos GeoIP local (MaxMind GeoLite2)
        val country = try {
            // Intentar reverse DNS para obtener pistas del país
            val hostname = InetAddress.getByName(ip).canonicalHostName
            when {
                hostname.endsWith(".es") -> "España"
                hostname.endsWith(".uk") || hostname.endsWith(".co.uk") -> "Reino Unido"
                hostname.endsWith(".de") -> "Alemania"
                hostname.endsWith(".fr") -> "Francia"
                hostname.endsWith(".us") || hostname.endsWith(".com") -> "Estados Unidos"
                hostname.endsWith(".cn") -> "China"
                hostname.endsWith(".ru") -> "Rusia"
                else -> "Desconocido"
            }
        } catch (e: Exception) {
            "Desconocido"
        }
        
        geoIpCache[ip] = country
        return country
    }
    
    /**
     * Determina la reputación de una IP basada en listas locales.
     * Identifica IPs de servicios conocidos (CDNs, clouds, etc.)
     */
    private fun getIpReputation(ip: String): String? {
        if (ip.startsWith("0.0.0.0") || ip.startsWith("127.")) {
            return "Loopback"
        }
        
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.")) {
            return "Red Privada"
        }
        
        // Caché de reputación
        reputationCache[ip]?.let { return it }
        
        // Rangos conocidos de servicios legítimos
        val reputation = when {
            // Google (8.8.8.8, 8.8.4.4, rangos conocidos)
            ip.startsWith("8.8.") || ip.startsWith("142.250.") || ip.startsWith("172.217.") -> "Google"
            // Cloudflare
            ip.startsWith("1.1.1.") || ip.startsWith("1.0.0.") || ip.startsWith("104.16.") -> "Cloudflare"
            // Amazon AWS
            ip.startsWith("52.") || ip.startsWith("54.") -> "Amazon AWS"
            // Microsoft Azure
            ip.startsWith("13.") || ip.startsWith("40.") -> "Microsoft Azure"
            // Akamai CDN
            ip.startsWith("23.") -> "Akamai CDN"
            else -> null
        }
        
        reputation?.let { reputationCache[ip] = it }
        return reputation
    }
    
    /**
     * Limpia el caché de geolocalización y reputación.
     */
    fun clearCache() {
        geoIpCache.clear()
        reputationCache.clear()
    }
}

data class NetworkConnectionInfo(
    val localIp: String,
    val remoteIp: String,
    val remoteCountry: String?,
    val remoteReputation: String?,
    val port: Int
)
