package com.guardianos.core.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Network Scanner Avanzado - Estilo Fing
 * 
 * Capacidades sin root:
 * - Escaneo ARP de dispositivos en red local (/proc/net/arp)
 * - Identificación de fabricantes por MAC (OUI database)
 * - Ping a dispositivos (verificar conectividad)
 * - Escaneo de puertos comunes
 * - Detección de dispositivos nuevos/desconocidos
 * - Categorización inteligente (smartphone, IoT, router, etc.)
 * - Wake on LAN (envío magic packet)
 * 
 * **100% LOCAL - Sin envío de datos**
 */
object NetworkScanner {
    private const val TAG = "NetworkScanner"
    private val knownDevices = ConcurrentHashMap<String, NetworkDevice>()
    
    data class NetworkDevice(
        val ipAddress: String,
        val macAddress: String,
        val hostname: String?,
        val manufacturer: String,
        val deviceType: DeviceType,
        val isActive: Boolean,
        val lastSeen: Long,
        val openPorts: List<Int>,
        val isNewDevice: Boolean,
        val riskLevel: RiskLevel
    )
    
    enum class DeviceType {
        ROUTER,
        SMARTPHONE,
        TABLET,
        COMPUTER,
        SMART_TV,
        IOT_DEVICE,
        CAMERA,
        PRINTER,
        GAME_CONSOLE,
        UNKNOWN
    }
    
    enum class RiskLevel {
        SAFE,       // Dispositivo conocido sin puertos sospechosos
        LOW,        // Dispositivo conocido con puertos estándar
        MEDIUM,     // Dispositivo nuevo o puertos no comunes
        HIGH,       // Puertos peligrosos abiertos (445, 3389, etc.)
        CRITICAL    // Dispositivo desconocido con puertos peligrosos
    }
    
    /**
     * Escanea la red local completa y devuelve todos los dispositivos encontrados
     */
    suspend fun scanLocalNetwork(context: Context): List<NetworkDevice> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Iniciando scanLocalNetwork()...")
        val devices = mutableListOf<NetworkDevice>()
        
        try {
            // 1. Obtener rango de red local
            val networkPrefix = getLocalNetworkPrefix(context)
            Log.d(TAG, "📡 Prefijo de red detectado: $networkPrefix.0/24")
            
            // 2. Intentar leer tabla ARP primero
            Log.d(TAG, "📂 Intentando leer tabla ARP...")
            var arpDevices = readArpTable()
            Log.d(TAG, "✅ Tabla ARP leída: ${arpDevices.size} entradas")
            
            // 3. Si ARP está vacío (Android 10+), usar escaneo por Ping
            if (arpDevices.isEmpty()) {
                Log.w(TAG, "⚠️ Tabla ARP vacía o inaccesible (Android 10+ restricción)")
                Log.d(TAG, "🔄 Iniciando escaneo alternativo por PING...")
                arpDevices = scanNetworkByPing(networkPrefix)
                Log.d(TAG, "✅ Escaneo por Ping completado: ${arpDevices.size} dispositivos")
            }
            
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "Iniciando escaneo de red local: $networkPrefix.0/24")
            Log.d(TAG, "Dispositivos detectados: ${arpDevices.size}")
        
        // 3. Para cada dispositivo detectado, obtener información detallada
        arpDevices.forEachIndexed { index, (ip, mac) ->
            try {
                Log.d(TAG, "🔍 Analizando ${index + 1}/${arpDevices.size}: $ip")
                
                // Verificar si está activo (ping) - solo si viene de ARP, si viene de ping scan ya está activo
                val isActive = if (mac.startsWith("02:00:00:00")) true else pingDevice(ip)
                
                if (!isActive) {
                    Log.d(TAG, "⚠️ $ip no responde (inactivo)")
                    return@forEachIndexed
                }
                
                // Obtener hostname
                val hostname = try {
                    withTimeout(2000) {
                        InetAddress.getByName(ip).canonicalHostName
                    }
                } catch (e: Exception) {
                    null
                }
                
                // Identificar fabricante por MAC
                val manufacturer = getManufacturerFromMac(mac)
                
                // Escanear solo puertos críticos para no demorar (reducido de 14 a 6 puertos)
                val criticalPorts = listOf(22, 23, 80, 443, 445, 3389)
                val openPorts = if (isActive) {
                    scanSpecificPorts(ip, criticalPorts)
                } else {
                    emptyList()
                }
                
                // Categorizar tipo de dispositivo
                val deviceType = categorizeDevice(hostname, manufacturer, openPorts)
                
                // Verificar si es nuevo (no en historial conocido)
                val isNew = !knownDevices.containsKey(mac)
                
                // Calcular nivel de riesgo
                val riskLevel = calculateRiskLevel(openPorts, isNew, deviceType)
                
                val device = NetworkDevice(
                    ipAddress = ip,
                    macAddress = mac,
                    hostname = hostname?.takeIf { it != ip },
                    manufacturer = manufacturer,
                    deviceType = deviceType,
                    isActive = isActive,
                    lastSeen = System.currentTimeMillis(),
                    openPorts = openPorts,
                    isNewDevice = isNew,
                    riskLevel = riskLevel
                )
                
                devices.add(device)
                
                // Guardar en historial
                knownDevices[mac] = device
                
                Log.d(TAG, "✓ Dispositivo: $ip ($manufacturer) - ${deviceType.name} - Riesgo: ${riskLevel.name}")
                
            } catch (e: Exception) {
                Log.w(TAG, "Error analizando $ip: ${e.message}")
            }
        }
        
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "Escaneo completado: ${devices.size} dispositivos encontrados")
            Log.d(TAG, "  - Activos: ${devices.count { it.isActive }}")
            Log.d(TAG, "  - Nuevos: ${devices.count { it.isNewDevice }}")
            Log.d(TAG, "  - Riesgo Alto/Crítico: ${devices.count { it.riskLevel >= RiskLevel.HIGH }}")
            
            // Guardar historial en SharedPreferences
            saveKnownDevices(context)
            
            return@withContext devices.sortedByDescending { it.riskLevel }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fatal en scanLocalNetwork: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Escanea la red local mediante Ping (alternativa a ARP para Android 10+)
     * Escaneo optimizado: primero IPs comunes (1, 254, 100-150), luego el resto
     */
    private suspend fun scanNetworkByPing(networkPrefix: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<Pair<String, String>>()
        
        // Priorizar IPs comunes (routers y dispositivos típicos)
        val priorityIPs = listOf(1, 254, 100, 101, 102, 103, 104, 105, 110, 120, 150, 200, 253)
        val remainingIPs = (2..253).filterNot { it in priorityIPs }
        
        val allIPs = priorityIPs + remainingIPs
        
        Log.d(TAG, "🔍 Escaneando rango: $networkPrefix.1-254")
        Log.d(TAG, "⚡ Modo optimizado: primero IPs prioritarias")
        Log.d(TAG, "⏱️ Tiempo estimado: 10-20 segundos...")
        
        // Escanear en bloques de 40 IPs en paralelo
        val chunks = allIPs.chunked(40)
        for (chunkIndex in chunks.indices) {
            val chunk = chunks[chunkIndex]
            
            val jobs = chunk.map { lastOctet ->
                async {
                    try {
                        val ip = "$networkPrefix.$lastOctet"
                        val address = InetAddress.getByName(ip)
                        
                        // Ping con timeout reducido a 400ms
                        if (address.isReachable(400)) {
                            // MAC sintética basada en IP
                            val syntheticMac = String.format(
                                "02:00:00:00:%02X:%02X",
                                lastOctet / 256,
                                lastOctet % 256
                            )
                            Pair(ip, syntheticMac)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            // Recopilar resultados del chunk
            jobs.awaitAll().filterNotNull().forEach { devices.add(it) }
            
            // Log de progreso cada 2 chunks
            if (chunkIndex % 2 == 0) {
                val progress = ((chunkIndex + 1) * 40 * 100) / allIPs.size
                Log.d(TAG, "📊 Progreso: $progress% - ${devices.size} dispositivos encontrados")
            }
            
            // Detener si ya encontramos suficientes dispositivos (optimización)
            if (devices.size >= 30) {
                Log.d(TAG, "✅ Límite de 30 dispositivos alcanzado, finalizando escaneo temprano")
                break
            }
        }
        
        return@withContext devices
    }
    
    /**
     * Lee la tabla ARP del sistema (/proc/net/arp)
     */
    private fun readArpTable(): List<Pair<String, String>> {
        val devices = mutableListOf<Pair<String, String>>()
        
        try {
            val arpFile = File("/proc/net/arp")
            Log.d(TAG, "📂 Verificando archivo ARP: ${arpFile.absolutePath}")
            Log.d(TAG, "   Existe: ${arpFile.exists()}")
            Log.d(TAG, "   Legible: ${arpFile.canRead()}")
            
            if (!arpFile.exists()) {
                Log.d(TAG, "ℹ️ Archivo /proc/net/arp no existe")
                return emptyList()
            }
            
            if (!arpFile.canRead()) {
                Log.d(TAG, "ℹ️ Sin permisos para leer /proc/net/arp (Android 10+)")
                return emptyList()
            }
            
            val lines = arpFile.readLines()
            
            // Saltar header (primera línea)
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 4) continue
                
                val ip = parts[0]
                val mac = parts[3]
                
                // Validar MAC (no 00:00:00:00:00:00)
                if (mac == "00:00:00:00:00:00" || mac.length < 17) continue
                
                // Ignorar localhost
                if (ip == "127.0.0.1" || ip.startsWith("0.")) continue
                
                devices.add(ip to mac.uppercase())
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo tabla ARP", e)
        }
        
        return devices
    }
    
    /**
     * Obtiene el prefijo de red local (ej: 192.168.1)
     */
    private fun getLocalNetworkPrefix(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            
            val ip = String.format(
                "%d.%d.%d",
                (ipInt and 0xff),
                (ipInt shr 8 and 0xff),
                (ipInt shr 16 and 0xff)
            )
            
            ip
        } catch (e: Exception) {
            "192.168.1" // Fallback
        }
    }
    
    /**
     * Ping a dispositivo (verificar conectividad)
     */
    private suspend fun pingDevice(ip: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val address = InetAddress.getByName(ip)
            address.isReachable(1000) // 1 segundo timeout
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Escanea puertos comunes (TCP)
     */
    private suspend fun scanCommonPorts(ip: String): List<Int> {
        val commonPorts = listOf(
            22,    // SSH
            23,    // Telnet
            80,    // HTTP
            443,   // HTTPS
            445,   // SMB (peligroso)
            554,   // RTSP (cámaras)
            1883,  // MQTT (IoT)
            3306,  // MySQL
            3389,  // RDP (peligroso)
            5000,  // UPnP
            5353,  // mDNS
            8080,  // HTTP alternativo
            8443,  // HTTPS alternativo
            9000   // PHP-FPM
        )
        
        return scanSpecificPorts(ip, commonPorts)
    }
    
    /**
     * Escanea una lista específica de puertos (optimizado)
     */
    private suspend fun scanSpecificPorts(ip: String, ports: List<Int>): List<Int> = withContext(Dispatchers.IO) {
        val openPorts = mutableListOf<Int>()
        
        // Escanear en paralelo con timeout corto
        val jobs = ports.map { port ->
            async {
                try {
                    withTimeout(400) { // 400ms timeout por puerto
                        val socket = Socket()
                        socket.connect(java.net.InetSocketAddress(ip, port), 400)
                        socket.close()
                        port
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
        
        jobs.awaitAll().filterNotNull().forEach { openPorts.add(it) }
        
        return@withContext openPorts.sorted()
    }
    
    /**
     * Obtiene fabricante del dispositivo desde MAC (OUI database)
     */
    private fun getManufacturerFromMac(mac: String): String {
        if (mac.length < 8) return "Desconocido"
        
        // Detectar MACs sintéticas (generadas por ping scan)
        if (mac.startsWith("02:00:00:00")) {
            return "Detectado por Ping"
        }
        
        // Obtener OUI (primeros 3 bytes: 6 caracteres hex)
        val oui = mac.substring(0, 8).replace(":", "").uppercase()
        
        return ouiDatabase[oui] ?: "Desconocido"
    }
    
    /**
     * Categoriza el tipo de dispositivo basándose en múltiples señales
     */
    private fun categorizeDevice(hostname: String?, manufacturer: String, openPorts: List<Int>): DeviceType {
        val hostLower = hostname?.lowercase() ?: ""
        val manuLower = manufacturer.lowercase()
        
        // Detección por hostname
        when {
            hostLower.contains("router") || hostLower.contains("gateway") -> return DeviceType.ROUTER
            hostLower.contains("android") || hostLower.contains("iphone") || manuLower.contains("samsung") || 
            manuLower.contains("apple") || manuLower.contains("xiaomi") || manuLower.contains("huawei") -> 
                return DeviceType.SMARTPHONE
            hostLower.contains("tv") || hostLower.contains("smarttv") || manuLower.contains("lg") || 
            manuLower.contains("sony") -> return DeviceType.SMART_TV
            hostLower.contains("camera") || hostLower.contains("ipc") || 554 in openPorts -> 
                return DeviceType.CAMERA
            hostLower.contains("printer") || 9100 in openPorts -> return DeviceType.PRINTER
            hostLower.contains("console") || hostLower.contains("playstation") || hostLower.contains("xbox") -> 
                return DeviceType.GAME_CONSOLE
        }
        
        // Detección por puertos
        when {
            445 in openPorts || 3389 in openPorts -> return DeviceType.COMPUTER
            554 in openPorts -> return DeviceType.CAMERA
            1883 in openPorts || 5353 in openPorts -> return DeviceType.IOT_DEVICE
        }
        
        // Detección por fabricante
        when {
            manuLower.contains("tp-link") || manuLower.contains("netgear") || manuLower.contains("linksys") -> 
                return DeviceType.ROUTER
            manuLower.contains("sonos") || manuLower.contains("alexa") || manuLower.contains("google") -> 
                return DeviceType.IOT_DEVICE
        }
        
        return DeviceType.UNKNOWN
    }
    
    /**
     * Calcula nivel de riesgo del dispositivo
     */
    private fun calculateRiskLevel(openPorts: List<Int>, isNew: Boolean, deviceType: DeviceType): RiskLevel {
        var score = 0
        
        // Puertos peligrosos
        if (445 in openPorts) score += 3  // SMB
        if (3389 in openPorts) score += 3 // RDP
        if (23 in openPorts) score += 2   // Telnet
        if (22 in openPorts && deviceType != DeviceType.ROUTER) score += 1 // SSH
        
        // Dispositivo nuevo
        if (isNew) score += 2
        
        // Tipo de dispositivo desconocido
        if (deviceType == DeviceType.UNKNOWN) score += 1
        
        // Muchos puertos abiertos
        if (openPorts.size >= 5) score += 1
        
        return when {
            score >= 6 -> RiskLevel.CRITICAL
            score >= 4 -> RiskLevel.HIGH
            score >= 2 -> RiskLevel.MEDIUM
            isNew -> RiskLevel.LOW
            else -> RiskLevel.SAFE
        }
    }
    
    /**
     * Envía magic packet para Wake on LAN
     */
    suspend fun sendWakeOnLan(macAddress: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val macBytes = macAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
            
            // Construir magic packet: 6 bytes FF + 16 repeticiones de MAC
            val packet = ByteArray(102)
            for (i in 0..5) packet[i] = 0xFF.toByte()
            for (i in 1..16) {
                System.arraycopy(macBytes, 0, packet, i * 6, 6)
            }
            
            // Enviar por UDP broadcast
            val socket = java.net.DatagramSocket()
            val address = InetAddress.getByName("255.255.255.255")
            val dgram = java.net.DatagramPacket(packet, packet.size, address, 9)
            socket.send(dgram)
            socket.close()
            
            Result.success("Magic packet enviado correctamente")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Guarda dispositivos conocidos en SharedPreferences
     */
    private fun saveKnownDevices(context: Context) {
        try {
            val prefs = context.getSharedPreferences("network_scanner", Context.MODE_PRIVATE)
            val knownMacs = knownDevices.keys.joinToString(",")
            prefs.edit().putString("known_devices", knownMacs).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando dispositivos conocidos", e)
        }
    }
    
    /**
     * Carga dispositivos conocidos desde SharedPreferences
     */
    fun loadKnownDevices(context: Context) {
        try {
            val prefs = context.getSharedPreferences("network_scanner", Context.MODE_PRIVATE)
            val knownMacs = prefs.getString("known_devices", "") ?: ""
            // Solo marcar como conocidos (sin restaurar objetos completos)
            knownMacs.split(",").filter { it.isNotBlank() }.forEach { mac ->
                // Placeholder para dispositivos conocidos
                knownDevices.putIfAbsent(mac, NetworkDevice(
                    "", mac, null, "", DeviceType.UNKNOWN, false, 
                    0, emptyList(), false, RiskLevel.SAFE
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando dispositivos conocidos", e)
        }
    }
    
    /**
     * Base de datos OUI (Organizationally Unique Identifier)
     * Mapea primeros 6 caracteres de MAC a fabricante
     */
    private val ouiDatabase = mapOf(
        // Routers principales
        "00:03:7F" to "Atheros (TP-Link)",
        "00:1B:2F" to "Netgear",
        "00:1E:58" to "Linksys/Cisco",
        "00:23:69" to "Cisco",
        "00:24:01" to "D-Link",
        "00:26:5A" to "ASUS",
        "14:DD:A9" to "TP-Link",
        "20:4E:7F" to "TP-Link",
        "50:C7:BF" to "TP-Link",
        "A0:F3:C1" to "TP-Link",
        
        // Smartphones y tablets
        "00:03:93" to "Apple",
        "00:0A:95" to "Apple",
        "00:17:F2" to "Apple",
        "00:1E:52" to "Apple",
        "00:23:12" to "Apple",
        "28:E1:4C" to "Apple",
        "40:A6:D9" to "Apple",
        "50:EA:D6" to "Apple",
        "5C:95:AE" to "Apple",
        "8C:85:90" to "Apple",
        "A4:5E:60" to "Apple",
        "BC:D0:74" to "Apple",
        "F0:DB:E2" to "Apple",
        
        "00:08:22" to "Samsung",
        "00:13:77" to "Samsung",
        "00:16:32" to "Samsung",
        "00:1A:8A" to "Samsung",
        "00:21:19" to "Samsung",
        "34:23:BA" to "Samsung",
        "38:AA:3C" to "Samsung",
        "44:A7:CF" to "Samsung",
        "50:32:75" to "Samsung",
        "5C:0A:5B" to "Samsung",
        "84:38:38" to "Samsung",
        "A8:F2:74" to "Samsung",
        "CC:07:AB" to "Samsung",
        "EC:1F:72" to "Samsung",
        
        "00:23:76" to "HTC",
        "08:00:28" to "Xiaomi",
        "34:CE:00" to "Xiaomi",
        "50:8F:4C" to "Xiaomi",
        "78:02:F8" to "Xiaomi",
        "AC:C1:EE" to "Xiaomi",
        "F4:8E:92" to "Xiaomi",
        
        "00:22:58" to "Huawei",
        "00:25:9E" to "Huawei",
        "00:46:4B" to "Huawei",
        "48:F8:B3" to "Huawei",
        "70:72:3C" to "Huawei",
        "D0:59:E4" to "Huawei",
        
        "28:56:5A" to "OnePlus",
        "A0:32:47" to "OnePlus",
        
        // Smart TVs
        "00:09:D0" to "LG Electronics",
        "00:1E:75" to "LG Electronics",
        "F8:8F:CA" to "LG Electronics",
        
        "00:13:A9" to "Sony",
        "00:1D:BA" to "Sony",
        "00:23:BE" to "Sony",
        
        // IoT y cámaras
        "00:12:4B" to "Hikvision (Cámara)",
        "00:1B:2D" to "Dahua (Cámara)",
        "00:1E:90" to "Ring",
        "00:1C:B3" to "Google (Nest)",
        "F4:F5:D8" to "Google (Nest)",
        "18:B4:30" to "Nest Labs",
        
        "00:17:88" to "Amazon (Echo/Alexa)",
        "74:C2:46" to "Amazon (Echo/Alexa)",
        
        "00:0E:58" to "Sonos",
        "5C:AA:FD" to "Sonos",
        
        // Consolas
        "00:09:BF" to "Nintendo",
        "00:19:FD" to "Nintendo",
        "00:1F:C5" to "Nintendo",
        "00:1B:EA" to "Nintendo Switch",
        
        "00:04:1F" to "Sony PlayStation",
        "00:13:15" to "Sony PlayStation",
        
        "00:09:5B" to "Microsoft Xbox",
        "00:15:5D" to "Microsoft Xbox",
        "00:50:F2" to "Microsoft Xbox"
    )
}
