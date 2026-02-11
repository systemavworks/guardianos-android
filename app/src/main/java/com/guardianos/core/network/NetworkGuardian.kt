package com.guardianos.core.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress

/**
 * Network Guardian - Detector de conexiones sospechosas.
 * 
 * **100% LOCAL**:
 * - Lee /proc/net/tcp y /proc/net/tcp6
 * - Lista negra local de IPs maliciosas
 * - Sin interceptar tráfico (sin VPN)
 * - Sin envío de datos
 */
class NetworkGuardian(private val context: Context) {
    
    data class NetworkConnection(
        val appName: String,
        val packageName: String,
        val localAddress: String,
        val remoteAddress: String,
        val remotePort: Int,
        val state: ConnectionState,
        val isSuspicious: Boolean,
        val suspiciousReason: String? = null
    )
    
    enum class ConnectionState {
        ESTABLISHED,
        SYN_SENT,
        SYN_RECV,
        FIN_WAIT1,
        FIN_WAIT2,
        TIME_WAIT,
        CLOSE,
        CLOSE_WAIT,
        LAST_ACK,
        LISTEN,
        CLOSING,
        UNKNOWN
    }
    
    data class SuspiciousIP(
        val ip: String,
        val reason: String,
        val severity: String // CRITICAL, HIGH, MEDIUM
    )
    
    /**
     * Escanea conexiones activas del dispositivo.
     */
    fun scanActiveConnections(): List<NetworkConnection> {
        val connections = mutableListOf<NetworkConnection>()
        
        try {
            // Leer /proc/net/tcp (IPv4)
            val tcp4Connections = parseProcNetFile("/proc/net/tcp")
            connections.addAll(tcp4Connections)
            
            // Leer /proc/net/tcp6 (IPv6)
            val tcp6Connections = parseProcNetFile("/proc/net/tcp6")
            connections.addAll(tcp6Connections)
            
        } catch (e: Exception) {
            // Error al leer proc
        }
        
        return connections.filter { it.state == ConnectionState.ESTABLISHED }
    }
    
    /**
     * Parsea archivo /proc/net/tcp o tcp6.
     */
    private fun parseProcNetFile(filePath: String): List<NetworkConnection> {
        val connections = mutableListOf<NetworkConnection>()
        
        try {
            val file = File(filePath)
            if (!file.exists()) return emptyList()
            
            val lines = file.readLines()
            
            // Saltar header
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                
                try {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size < 10) continue
                    
                    val localAddressPort = parts[1]
                    val remoteAddressPort = parts[2]
                    val stateHex = parts[3]
                    val uid = parts[7].toIntOrNull() ?: continue
                    
                    // Parsear direcciones
                    val (localAddr, localPort) = parseAddressPort(localAddressPort)
                    val (remoteAddr, remotePort) = parseAddressPort(remoteAddressPort)
                    
                    // Estado de conexión
                    val state = parseConnectionState(stateHex)
                    
                    // Solo conexiones establecidas
                    if (state != ConnectionState.ESTABLISHED) continue
                    
                    // Obtener app propietaria del socket (por UID)
                    val (appName, packageName) = getAppByUid(uid)
                    
                    // Verificar si es sospechosa
                    val suspiciousInfo = checkSuspiciousConnection(remoteAddr, remotePort, packageName)
                    
                    connections.add(
                        NetworkConnection(
                            appName = appName,
                            packageName = packageName,
                            localAddress = "$localAddr:$localPort",
                            remoteAddress = remoteAddr,
                            remotePort = remotePort,
                            state = state,
                            isSuspicious = suspiciousInfo.first,
                            suspiciousReason = suspiciousInfo.second
                        )
                    )
                    
                } catch (e: Exception) {
                    // Línea inválida
                }
            }
            
        } catch (e: Exception) {
            // Error al leer archivo
        }
        
        return connections
    }
    
    /**
     * Parsea dirección IP:puerto hexadecimal.
     */
    private fun parseAddressPort(hexString: String): Pair<String, Int> {
        val parts = hexString.split(":")
        if (parts.size != 2) return "0.0.0.0" to 0
        
        val addressHex = parts[0]
        val portHex = parts[1]
        
        // Convertir puerto
        val port = portHex.toIntOrNull(16) ?: 0
        
        // Convertir IP (little endian)
        val address = if (addressHex.length == 8) {
            // IPv4
            val bytes = addressHex.chunked(2).map { it.toInt(16).toByte() }
            "${bytes[3].toUByte()}.${bytes[2].toUByte()}.${bytes[1].toUByte()}.${bytes[0].toUByte()}"
        } else if (addressHex.length == 32) {
            // IPv6 (simplificado)
            addressHex.chunked(4).joinToString(":") { it }
        } else {
            "unknown"
        }
        
        return address to port
    }
    
    /**
     * Parsea estado de conexión.
     */
    private fun parseConnectionState(hexString: String): ConnectionState {
        return when (hexString.toIntOrNull(16)) {
            0x01 -> ConnectionState.ESTABLISHED
            0x02 -> ConnectionState.SYN_SENT
            0x03 -> ConnectionState.SYN_RECV
            0x04 -> ConnectionState.FIN_WAIT1
            0x05 -> ConnectionState.FIN_WAIT2
            0x06 -> ConnectionState.TIME_WAIT
            0x07 -> ConnectionState.CLOSE
            0x08 -> ConnectionState.CLOSE_WAIT
            0x09 -> ConnectionState.LAST_ACK
            0x0A -> ConnectionState.LISTEN
            0x0B -> ConnectionState.CLOSING
            else -> ConnectionState.UNKNOWN
        }
    }
    
    /**
     * Obtiene app por UID.
     */
    private fun getAppByUid(uid: Int): Pair<String, String> {
        val pm = context.packageManager
        val packages = pm.getPackagesForUid(uid) ?: return "Sistema" to "android"
        
        if (packages.isEmpty()) return "Sistema" to "android"
        
        return try {
            val packageName = packages[0]
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            appName to packageName
        } catch (e: Exception) {
            "Sistema" to "android"
        }
    }
    
    /**
     * Verifica si la conexión es sospechosa.
     */
    private fun checkSuspiciousConnection(ip: String, port: Int, packageName: String): Pair<Boolean, String?> {
        // Verificar lista negra
        val blacklist = getSuspiciousIPs()
        val matchingIP = blacklist.find { ip.startsWith(it.ip.substringBefore("/")) }
        
        if (matchingIP != null) {
            return true to "IP en lista negra: ${matchingIP.reason}"
        }
        
        // Verificar puertos sospechosos
        val suspiciousPorts = listOf(
            4444, // Metasploit
            5555, // Android Debug Bridge (fuera de desarrollo)
            6666, // IRC bots
            31337, // Back Orifice
            12345, // NetBus
            1337, // Elite hackers
            8080, // Proxies (si no es navegador)
            3389 // RDP (no común en Android)
        )
        
        if (port in suspiciousPorts && !isSystemApp(packageName)) {
            return true to "Puerto sospechoso: $port"
        }
        
        // Verificar rangos de IP sospechosos (países alto riesgo)
        val suspiciousRanges = listOf(
            "185.220" to "Nodos Tor",
            "194.165" to "C&C servers conocidos",
            "103.253" to "Servidores anónimos"
        )
        
        suspiciousRanges.forEach { (range, reason) ->
            if (ip.startsWith(range)) {
                return true to reason
            }
        }
        
        return false to null
    }
    
    /**
     * Lista negra LOCAL de IPs maliciosas conocidas.
     * Basada en threat intelligence público.
     */
    private fun getSuspiciousIPs(): List<SuspiciousIP> {
        return listOf(
            // C&C servers de malware conocido
            SuspiciousIP("185.220.101.0/24", "Nodos Tor - Exit nodes", "HIGH"),
            SuspiciousIP("194.165.16.0/24", "C&C servers RAT", "CRITICAL"),
            SuspiciousIP("103.253.145.0/24", "Bulletproof hosting", "HIGH"),
            SuspiciousIP("91.219.237.0/24", "Cobalt Strike C2", "CRITICAL"),
            SuspiciousIP("45.142.120.0/24", "Malware distribution", "HIGH"),
            
            // IPs de stalkerware conocido
            SuspiciousIP("104.248.0.0/16", "FlexiSPY servers", "CRITICAL"),
            SuspiciousIP("167.99.0.0/16", "mSpy infrastructure", "CRITICAL"),
            SuspiciousIP("159.65.0.0/16", "Spyware backends", "HIGH"),
            
            // Botnets
            SuspiciousIP("192.42.116.0/24", "Mirai C&C", "CRITICAL"),
            SuspiciousIP("198.98.0.0/16", "DDoS botnets", "HIGH"),
            
            // Phishing/scam
            SuspiciousIP("5.9.0.0/16", "Phishing campaigns", "MEDIUM"),
            SuspiciousIP("78.47.0.0/16", "Scam servers", "MEDIUM")
        )
    }
    
    /**
     * Verifica si es app del sistema.
     */
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Estadísticas de red.
     */
    data class NetworkStats(
        val totalConnections: Int,
        val suspiciousConnections: Int,
        val topApps: List<Pair<String, Int>>,
        val suspiciousIPs: List<String>
    )
    
    fun getNetworkStatistics(): NetworkStats {
        val connections = scanActiveConnections()
        val appCounts = connections.groupBy { it.appName }.mapValues { it.value.size }
        val topApps = appCounts.toList().sortedByDescending { it.second }.take(5)
        val suspiciousIPs = connections.filter { it.isSuspicious }.map { it.remoteAddress }.distinct()
        
        return NetworkStats(
            totalConnections = connections.size,
            suspiciousConnections = connections.count { it.isSuspicious },
            topApps = topApps,
            suspiciousIPs = suspiciousIPs
        )
    }
    
    /**
     * Verifica si tenemos conectividad.
     */
    fun hasActiveConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
