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
    
    private val TAG = "NetworkGuardian"
    
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
     * Información detallada sobre la red WiFi actual.
     */
    data class WifiNetworkInfo(
        val ssid: String,
        val bssid: String,
        val securityType: String,        // WPA3, WPA2, WEP, OPEN
        val signalStrength: Int,         // dBm
        val frequency: Int,              // MHz (2.4GHz/5GHz)
        val channel: Int,
        val linkSpeed: Int,              // Mbps
        val ipAddress: String,
        val gateway: String,
        val dns: List<String>,
        val isSecure: Boolean,
        val securityLevel: String,       // SEGURA, ACEPTABLE, INSEGURA, PELIGROSA
        val vulnerabilities: List<String>,
        val recommendations: List<String>
    )
    
    /**
     * Obtiene información completa sobre la red WiFi actual.
     * Incluye análisis de seguridad, cifrado y recomendaciones.
     */
    fun getCurrentWifiInfo(): WifiNetworkInfo? {
        return try {
            // Verificar permisos primero
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w(TAG, "⚠️ Permiso ACCESS_WIFI_STATE no otorgado")
                return null
            }
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w(TAG, "⚠️ Permiso ACCESS_NETWORK_STATE no otorgado")
                return null
            }
            
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                ?: return null
            
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
            
            // Verificar que es WiFi
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                android.util.Log.d(TAG, "No hay conexión WiFi activa")
                return null
            }
            
            val connectionInfo = wifiManager.connectionInfo
        val dhcpInfo = wifiManager.dhcpInfo
        
        // SSID (remover comillas)
        val ssid = connectionInfo.ssid.replace("\"", "")
        if (ssid == "<unknown ssid>") {
            android.util.Log.w(TAG, "SSID desconocido - permisos insuficientes")
            return null
        }
        
        // BSSID (dirección MAC del router)
        val bssid = connectionInfo.bssid ?: "Desconocido"
        
        // Fuerza de señal (dBm)
        val rssi = connectionInfo.rssi
        
        // Velocidad de enlace (Mbps)
        val linkSpeed = connectionInfo.linkSpeed
        
        // Frecuencia (MHz) - 2.4GHz o 5GHz
        val frequency = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            connectionInfo.frequency
        } else {
            0
        }
        
        // Canal WiFi aproximado
        val channel = when {
            frequency in 2412..2484 -> (frequency - 2412) / 5 + 1  // Canales 1-13 (2.4GHz)
            frequency in 5170..5825 -> (frequency - 5170) / 5 + 34 // Canales 5GHz
            else -> 0
        }
        
        // IP local
        val ipAddress = android.text.format.Formatter.formatIpAddress(connectionInfo.ipAddress)
        
        // Gateway (router)
        val gateway = android.text.format.Formatter.formatIpAddress(dhcpInfo.gateway)
        
        // DNS
        val dns1 = android.text.format.Formatter.formatIpAddress(dhcpInfo.dns1)
        val dns2 = android.text.format.Formatter.formatIpAddress(dhcpInfo.dns2)
        val dnsList = listOf(dns1, dns2).filter { it != "0.0.0.0" }
        
        // ✅ ANÁLISIS DE SEGURIDAD
        val securityType = detectWifiSecurity(wifiManager, ssid)
        val isSecure = securityType in listOf("WPA3", "WPA2")
        
        val vulnerabilities = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Evaluar nivel de seguridad
        val securityLevel = when (securityType) {
            "WPA3" -> {
                "SEGURA"
            }
            "WPA2" -> {
                if (rssi > -70) {
                    "SEGURA"
                } else {
                    vulnerabilities.add("Señal débil ($rssi dBm)")
                    recommendations.add("Acércate al router para mejorar la velocidad")
                    "ACEPTABLE"
                }
            }
            "WPA" -> {
                vulnerabilities.add("WPA es antiguo y vulnerable")
                recommendations.add("Actualiza el router a WPA2 o WPA3")
                "INSEGURA"
            }
            "WEP" -> {
                vulnerabilities.add("⚠️ WEP es EXTREMADAMENTE inseguro (hack en 60 segundos)")
                recommendations.add("🚨 CAMBIAR INMEDIATAMENTE a WPA2 o WPA3")
                recommendations.add("No uses esta red para operaciones sensibles")
                "PELIGROSA"
            }
            "OPEN" -> {
                vulnerabilities.add("🚨 Red ABIERTA sin cifrado")
                vulnerabilities.add("Cualquiera puede interceptar tu tráfico")
                recommendations.add("🚨 NUNCA uses redes abiertas sin VPN")
                recommendations.add("No accedas a bancos, correos o redes sociales")
                "PELIGROSA"
            }
            else -> "DESCONOCIDA"
        }
        
        // Análisis de canal (interferencias 2.4GHz)
        if (frequency in 2412..2484) {
            if (channel in 1..11) {
                vulnerabilities.add("Canal $channel (2.4GHz) - posible interferencia con vecinos")
                recommendations.add("Considera usar 5GHz si tu dispositivo lo soporta")
            }
        }
        
        // Análisis de DNS (seguridad)
        if (dnsList.any { it.startsWith("8.8.") }) {
            android.util.Log.d(TAG, "DNS de Google detectado (seguro)")
        } else if (dnsList.any { it.startsWith("1.1.") }) {
            android.util.Log.d(TAG, "DNS de Cloudflare detectado (seguro)")
        } else {
            recommendations.add("Considera usar DNS seguros (1.1.1.1 o 8.8.8.8)")
        }
        
        // Recomendaciones generales
        if (securityLevel == "SEGURA" && vulnerabilities.isEmpty()) {
            recommendations.add("✅ Tu red es segura, continúa navegando con confianza")
        }
        
        android.util.Log.d(TAG, "📡 Red WiFi: $ssid")
        android.util.Log.d(TAG, "   Seguridad: $securityType ($securityLevel)")
        android.util.Log.d(TAG, "   Señal: $rssi dBm")
        android.util.Log.d(TAG, "   Frecuencia: ${frequency}MHz (Canal $channel)")
        android.util.Log.d(TAG, "   Velocidad: ${linkSpeed}Mbps")
        android.util.Log.d(TAG, "   IP: $ipAddress | Gateway: $gateway")
        
        WifiNetworkInfo(
            ssid = ssid,
            bssid = bssid,
            securityType = securityType,
            signalStrength = rssi,
            frequency = frequency,
            channel = channel,
            linkSpeed = linkSpeed,
            ipAddress = ipAddress,
            gateway = gateway,
            dns = dnsList,
            isSecure = isSecure,
            securityLevel = securityLevel,
            vulnerabilities = vulnerabilities,
            recommendations = recommendations
        )
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "❌ SecurityException al acceder a info WiFi: ${e.message}")
            null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error obteniendo info WiFi: ${e.message}", e)
            null
        }
    }
    
    /**
     * Detecta el tipo de seguridad WiFi actual.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun detectWifiSecurity(wifiManager: android.net.wifi.WifiManager, currentSsid: String): String {
        try {
            val scanResults = wifiManager.scanResults
            val currentNetwork = scanResults.find { 
                it.SSID == currentSsid || "\"${it.SSID}\"" == currentSsid 
            }
            
            if (currentNetwork != null) {
                val capabilities = currentNetwork.capabilities
                return when {
                    capabilities.contains("WPA3") -> "WPA3"
                    capabilities.contains("WPA2") -> "WPA2"
                    capabilities.contains("WPA") -> "WPA"
                    capabilities.contains("WEP") -> "WEP"
                    capabilities.contains("ESS") && !capabilities.contains("WPA") -> "OPEN"
                    else -> "Desconocido"
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Error detectando seguridad WiFi", e)
        }
        
        return "Desconocido"
    }
    
    /**
     * Escanea conexiones activas del dispositivo.
     */
    fun scanActiveConnections(): List<NetworkConnection> {
        val connections = mutableListOf<NetworkConnection>()
        
        android.util.Log.d(TAG, "🔍 Iniciando escaneo de red...")
        
        try {
            // Leer /proc/net/tcp (IPv4)
            val tcp4Connections = parseProcNetFile("/proc/net/tcp")
            connections.addAll(tcp4Connections)
            android.util.Log.d(TAG, "  IPv4: ${tcp4Connections.size} conexiones")
            
            // Leer /proc/net/tcp6 (IPv6)
            val tcp6Connections = parseProcNetFile("/proc/net/tcp6")
            connections.addAll(tcp6Connections)
            android.util.Log.d(TAG, "  IPv6: ${tcp6Connections.size} conexiones")
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error al leer proc/net: ${e.message}")
        }
        
        val established = connections.filter { it.state == ConnectionState.ESTABLISHED }
        android.util.Log.d(TAG, "✅ Conexiones ESTABLISHED: ${established.size}")
        android.util.Log.d(TAG, "  Apps únicas: ${established.map { it.packageName }.distinct().size}")
        android.util.Log.d(TAG, "  Sospechosas: ${established.count { it.isSuspicious }}")
        
        return established
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
            // ⚠️ NUNCA usar getApplicationLabel() - carga APK assets (muy lento)
            val appName = packageName.substringAfterLast('.', packageName)
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
        android.util.Log.d(TAG, "📊 Generando estadísticas de red...")
        val connections = scanActiveConnections()
        val appCounts = connections.groupBy { it.appName }.mapValues { it.value.size }
        val topApps = appCounts.toList().sortedByDescending { it.second }.take(5)
        val suspiciousIPs = connections.filter { it.isSuspicious }.map { it.remoteAddress }.distinct()
        
        android.util.Log.d(TAG, "  Top apps con conexiones:")
        topApps.forEach { (app, count) ->
            android.util.Log.d(TAG, "    - $app: $count conexiones")
        }
        
        if (suspiciousIPs.isNotEmpty()) {
            android.util.Log.w(TAG, "⚠️ IPs sospechosas detectadas: ${suspiciousIPs.size}")
            suspiciousIPs.forEach { ip ->
                android.util.Log.w(TAG, "    - $ip")
            }
        } else {
            android.util.Log.d(TAG, "✅ No se detectaron IPs sospechosas")
        }
        
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
    
    /**
     * Escanea redes WiFi cercanas disponibles.
     * Requiere permisos: ACCESS_FINE_LOCATION, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE
     */
    data class NearbyWifiNetwork(
        val ssid: String,
        val bssid: String,
        val securityType: String,
        val signalStrength: Int,        // dBm
        val signalLevel: Int,            // 0-4 (barras)
        val frequency: Int,
        val channel: Int,
        val isCurrentNetwork: Boolean,
        val capabilities: String,
        val riskLevel: String,           // SEGURO, PRECAUCIÓN, PELIGROSO
        val warnings: List<String>
    )
    
    suspend fun scanNearbyWifiNetworks(): List<NearbyWifiNetwork> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val networks = mutableListOf<NearbyWifiNetwork>()
        
        try {
            // Verificar permisos
            val hasLocationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            
            if (!hasLocationPermission) {
                android.util.Log.w(TAG, "⚠️ Permiso ACCESS_FINE_LOCATION no otorgado para escaneo WiFi")
                return@withContext emptyList()
            }
            
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                ?: return@withContext emptyList()
            
            if (!wifiManager.isWifiEnabled) {
                android.util.Log.w(TAG, "⚠️ WiFi desactivado")
                return@withContext emptyList()
            }
            
            // Iniciar escaneo
            android.util.Log.d(TAG, "🔍 Iniciando escaneo de redes WiFi cercanas...")
            val scanSuccess = wifiManager.startScan()
            
            if (!scanSuccess) {
                android.util.Log.w(TAG, "⚠️ Fallo al iniciar escaneo WiFi")
            }
            
            // Esperar un poco para que complete el escaneo
            kotlinx.coroutines.delay(1500)
            
            // Obtener resultados
            val scanResults = wifiManager.scanResults
            val currentNetwork = getCurrentWifiInfo()
            
            android.util.Log.d(TAG, "✅ Redes WiFi encontradas: ${scanResults.size}")
            
            scanResults.forEach { result ->
                val ssid = result.SSID
                if (ssid.isBlank()) return@forEach // Redes ocultas
                
                val bssid = result.BSSID
                val rssi = result.level
                val frequency = result.frequency
                val capabilities = result.capabilities
                
                // Detectar tipo de seguridad
                val securityType = when {
                    capabilities.contains("WPA3") -> "WPA3"
                    capabilities.contains("WPA2") -> "WPA2"
                    capabilities.contains("WPA") && !capabilities.contains("WPA2") -> "WPA"
                    capabilities.contains("WEP") -> "WEP"
                    capabilities.contains("OWE") -> "OWE" // Opportunistic Wireless Encryption
                    else -> "ABIERTA"
                }
                
                // Calcular canal
                val channel = when {
                    frequency in 2412..2484 -> (frequency - 2412) / 5 + 1
                    frequency in 5170..5825 -> (frequency - 5170) / 5 + 34
                    else -> 0
                }
                
                // Nivel de señal (0-4 barras)
                val signalLevel = android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5)
                
                // Es la red actual?
                val isCurrentNetwork = currentNetwork?.ssid == ssid
                
                // Evaluar riesgo
                val warnings = mutableListOf<String>()
                val riskLevel = when (securityType) {
                    "WPA3", "WPA2" -> {
                        if (rssi < -80) {
                            warnings.add("Señal muy débil ($rssi dBm)")
                        }
                        "SEGURO"
                    }
                    "WPA" -> {
                        warnings.add("WPA es vulnerable a ataques")
                        warnings.add("Recomendado: WPA2/WPA3")
                        "PRECAUCIÓN"
                    }
                    "WEP" -> {
                        warnings.add("⚠️ WEP es EXTREMADAMENTE inseguro")
                        warnings.add("Se puede hackear en menos de 1 minuto")
                        warnings.add("NO USAR para nada sensible")
                        "PELIGROSO"
                    }
                    "ABIERTA" -> {
                        warnings.add("🚨 Red sin cifrado")
                        warnings.add("TODO el tráfico es visible")
                        warnings.add("Usar SOLO con VPN activa")
                        "PELIGROSO"
                    }
                    else -> "DESCONOCIDO"
                }
                
                // Detectar redes sospechosas (SSID común de honeypots)
                val suspiciousSSIDs = listOf(
                    "Free WiFi", "Free_WiFi", "FREE-WIFI",
                    "Public WiFi", "Open WiFi",
                    "Airport WiFi", "Hotel WiFi",
                    "Starbucks", "McDonalds",
                    "AndroidAP", "iPhone"
                )
                
                if (suspiciousSSIDs.any { ssid.contains(it, ignoreCase = true) } && securityType == "ABIERTA") {
                    warnings.add("⚠️ Nombre sospechoso - posible honeypot")
                }
                
                // Detectar canales congestionados (2.4GHz)
                if (frequency in 2412..2484 && channel in 1..11) {
                    warnings.add("Canal $channel (2.4GHz) puede tener interferencias")
                }
                
                networks.add(
                    NearbyWifiNetwork(
                        ssid = ssid,
                        bssid = bssid,
                        securityType = securityType,
                        signalStrength = rssi,
                        signalLevel = signalLevel,
                        frequency = frequency,
                        channel = channel,
                        isCurrentNetwork = isCurrentNetwork,
                        capabilities = capabilities,
                        riskLevel = riskLevel,
                        warnings = warnings
                    )
                )
                
                android.util.Log.d(TAG, "  📡 $ssid | $securityType | $rssi dBm | Canal $channel | ${if (isCurrentNetwork) "[CONECTADA]" else ""}")
            }
            
            // Ordenar por fuerza de señal
            networks.sortByDescending { it.signalStrength }
            
            android.util.Log.d(TAG, "═══════════════════════════════════════════")
            android.util.Log.d(TAG, "Resumen de escaneo:")
            android.util.Log.d(TAG, "  Total redes: ${networks.size}")
            android.util.Log.d(TAG, "  Seguras (WPA2/WPA3): ${networks.count { it.securityType in listOf("WPA2", "WPA3") }}")
            android.util.Log.d(TAG, "  Inseguras (WPA/WEP): ${networks.count { it.securityType in listOf("WPA", "WEP") }}")
            android.util.Log.d(TAG, "  Abiertas: ${networks.count { it.securityType == "ABIERTA" }}")
            android.util.Log.d(TAG, "═══════════════════════════════════════════")
            
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "❌ SecurityException en escaneo WiFi: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error escaneando redes WiFi: ${e.message}", e)
        }
        
        return@withContext networks
    }
}
