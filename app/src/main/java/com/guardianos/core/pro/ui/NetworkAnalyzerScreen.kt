package com.guardianos.core.pro.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.network.NetworkScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NetworkAnalyzerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<NetworkScanner.NetworkDevice>>(emptyList()) }
    var wifiInfo by remember { mutableStateOf<com.guardianos.core.network.NetworkGuardian.WifiNetworkInfo?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Cargar dispositivos conocidos al inicio
    LaunchedEffect(Unit) {
        NetworkScanner.loadKnownDevices(context)
        // Obtener información WiFi actual
        val guardian = com.guardianos.core.network.NetworkGuardian(context)
        wifiInfo = guardian.getCurrentWifiInfo()
        // Escanear automáticamente al abrir
        isScanning = true
        devices = withContext(Dispatchers.IO) {
            NetworkScanner.scanLocalNetwork(context)
        }
        isScanning = false
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🌐 Análisis de Red",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (!isScanning) {
                TextButton(
                    onClick = {
                        scope.launch {
                            isScanning = true
                            val guardian = com.guardianos.core.network.NetworkGuardian(context)
                            wifiInfo = guardian.getCurrentWifiInfo()
                            devices = withContext(Dispatchers.IO) {
                                NetworkScanner.scanLocalNetwork(context)
                            }
                            isScanning = false
                        }
                    }
                ) {
                    Text("🔄 Re-escanear")
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Descripción
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF5D8BF4).copy(alpha = 0.15f)
            )
        ) {
            Text(
                text = "Escaneo avanzado de dispositivos en tu red Wi-Fi local. Detecta intrusos, puertos abiertos y analiza vulnerabilidades.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Estado de escaneo
        if (isScanning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFBBF24).copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Escaneando red local...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Analizando tabla ARP, verificando conectividad y escaneando puertos",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        
        // Estadísticas
        if (devices.isNotEmpty()) {
            NetworkStatisticsCard(devices)
            Spacer(Modifier.height(16.dp))
        }
        
        // Alertas de dispositivos nuevos o de alto riesgo
        val criticalDevices = devices.filter { 
            it.riskLevel >= NetworkScanner.RiskLevel.HIGH || it.isNewDevice 
        }
        if (criticalDevices.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFB3261E).copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, Color(0xFFB3261E))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠️ Alertas de Seguridad",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFB3261E)
                    )
                    Spacer(Modifier.height(8.dp))
                    criticalDevices.forEach { device ->
                        Text(
                            text = "• ${device.ipAddress} (${device.manufacturer})" + 
                                   if (device.isNewDevice) " - NUEVO DISPOSITIVO" else " - Puertos peligrosos: ${device.openPorts.joinToString()}",
                            fontSize = 12.sp,
                            color = Color(0xFFB3261E),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        
        // Lista de dispositivos
        if (devices.isEmpty() && !isScanning) {
            EmptyNetworkCard()
        } else {
            Text(
                text = "Dispositivos encontrados (${devices.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            
            devices.forEach { device ->
                NetworkDeviceCard(device)
                Spacer(Modifier.height(8.dp))
            }
        }
        
        // Footer informativo
        Spacer(Modifier.height(16.dp))
        TechnicalLimitationsNetworkCard()
        
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }
    }
}

@Composable
private fun NetworkStatisticsCard(devices: List<NetworkScanner.NetworkDevice>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 Estadísticas de Red",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF059669)
            )
            Spacer(Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem("Total", "${devices.size}", Modifier.weight(1f))
                StatItem("Activos", "${devices.count { it.isActive }}", Modifier.weight(1f))
                StatItem("Nuevos", "${devices.count { it.isNewDevice }}", Modifier.weight(1f))
                StatItem(
                    "Riesgo Alto", 
                    "${devices.count { it.riskLevel >= NetworkScanner.RiskLevel.HIGH }}", 
                    Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Tipos de dispositivos
            val deviceTypes = devices.groupBy { it.deviceType }.mapValues { it.value.size }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                deviceTypes.forEach { (type, count) ->
                    val icon = when (type) {
                        NetworkScanner.DeviceType.ROUTER -> "📡"
                        NetworkScanner.DeviceType.SMARTPHONE -> "📱"
                        NetworkScanner.DeviceType.COMPUTER -> "💻"
                        NetworkScanner.DeviceType.SMART_TV -> "📺"
                        NetworkScanner.DeviceType.IOT_DEVICE -> "🔮"
                        NetworkScanner.DeviceType.CAMERA -> "📷"
                        NetworkScanner.DeviceType.PRINTER -> "🖨️"
                        NetworkScanner.DeviceType.GAME_CONSOLE -> "🎮"
                        else -> "❓"
                    }
                    Text(
                        text = "$icon $count",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiInformationCard(wifiInfo: com.guardianos.core.network.NetworkGuardian.WifiNetworkInfo) {
    val securityColor = when (wifiInfo.securityLevel) {
        "SEGURA" -> Color(0xFF10B981)
        "ACEPTABLE" -> Color(0xFF10B981)
        "INSEGURA" -> Color(0xFFFBBF24)
        "PELIGROSA" -> Color(0xFFB3261E)
        else -> Color.Gray
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = securityColor.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, securityColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📡 Red WiFi Actual",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = securityColor
                )
                Surface(
                    color = securityColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = wifiInfo.securityLevel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            
            // SSID y BSSID
            InfoRow("Red", wifiInfo.ssid)
            InfoRow("Router (BSSID)", wifiInfo.bssid)
            
            Spacer(Modifier.height(8.dp))
            
            // Seguridad
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cifrado", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        wifiInfo.securityType,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (wifiInfo.isSecure) Color(0xFF10B981) else Color(0xFFB3261E)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Señal", fontSize = 11.sp, color = Color.Gray)
                    val signalQuality = when {
                        wifiInfo.signalStrength > -50 -> "Excelente"
                        wifiInfo.signalStrength > -60 -> "Buena"
                        wifiInfo.signalStrength > -70 -> "Media"
                        else -> "Débil"
                    }
                    Text(
                        "$signalQuality (${wifiInfo.signalStrength} dBm)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Frecuencia y velocidad
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Frecuencia", fontSize = 11.sp, color = Color.Gray)
                    val band = if (wifiInfo.frequency > 5000) "5 GHz" else "2.4 GHz"
                    Text(
                        "$band (Canal ${wifiInfo.channel})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Velocidad", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        "${wifiInfo.linkSpeed} Mbps",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Red
            InfoRow("IP Local", wifiInfo.ipAddress)
            InfoRow("Gateway", wifiInfo.gateway)
            if (wifiInfo.dns.isNotEmpty()) {
                InfoRow("DNS", wifiInfo.dns.joinToString(", "))
            }
            
            // Vulnerabilidades
            if (wifiInfo.vulnerabilities.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "⚠️ Vulnerabilidades detectadas:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB3261E)
                )
                wifiInfo.vulnerabilities.forEach { vuln ->
                    Text(
                        text = "• $vuln",
                        fontSize = 11.sp,
                        color = Color(0xFFB3261E),
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }
            
            // Recomendaciones
            if (wifiInfo.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "💡 Recomendaciones:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = securityColor
                )
                wifiInfo.recommendations.forEach { rec ->
                    Text(
                        text = "• $rec",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label:",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun NetworkDeviceCard(device: NetworkScanner.NetworkDevice) {
    val riskColor = when (device.riskLevel) {
        NetworkScanner.RiskLevel.CRITICAL -> Color(0xFF8B0000)
        NetworkScanner.RiskLevel.HIGH -> Color(0xFFB3261E)
        NetworkScanner.RiskLevel.MEDIUM -> Color(0xFFFBBF24)
        NetworkScanner.RiskLevel.LOW -> Color(0xFF10B981)
        NetworkScanner.RiskLevel.SAFE -> Color(0xFF4CAF50)
    }
    
    val deviceIcon = when (device.deviceType) {
        NetworkScanner.DeviceType.ROUTER -> "📡"
        NetworkScanner.DeviceType.SMARTPHONE -> "📱"
        NetworkScanner.DeviceType.TABLET -> "📱"
        NetworkScanner.DeviceType.COMPUTER -> "💻"
        NetworkScanner.DeviceType.SMART_TV -> "📺"
        NetworkScanner.DeviceType.IOT_DEVICE -> "🔮"
        NetworkScanner.DeviceType.CAMERA -> "📷"
        NetworkScanner.DeviceType.PRINTER -> "🖨️"
        NetworkScanner.DeviceType.GAME_CONSOLE -> "🎮"
        NetworkScanner.DeviceType.UNKNOWN -> "❓"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (device.riskLevel >= NetworkScanner.RiskLevel.HIGH)
                riskColor.copy(alpha = 0.10f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (device.isNewDevice) BorderStroke(2.dp, Color(0xFFFBBF24)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Icono + IP + Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = deviceIcon, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.ipAddress,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            if (device.isNewDevice) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFFBBF24),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "NUEVO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        device.hostname?.let {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                // Estado activo/inactivo
                Surface(
                    color = if (device.isActive) Color(0xFF10B981) else Color(0xFF6B7280),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (device.isActive) "●" else "○",
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))
            
            // MAC + Fabricante
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MAC", fontSize = 11.sp, color = Color.Gray)
                    Text(device.macAddress, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fabricante", fontSize = 11.sp, color = Color.Gray)
                    Text(device.manufacturer, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Tipo de dispositivo + Riesgo
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tipo", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        device.deviceType.name.replace("_", " "),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Nivel de Riesgo", fontSize = 11.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = riskColor,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = device.riskLevel.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // Puertos abiertos
            if (device.openPorts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Puertos abiertos:", fontSize = 11.sp, color = Color.Gray)
                Text(
                    text = device.openPorts.joinToString(", ") { port ->
                        "$port ${getPortDescription(port)}"
                    },
                    fontSize = 12.sp,
                    color = if (device.openPorts.any { it in listOf(445, 3389, 23) })
                        Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Última vez visto
            if (device.lastSeen > 0) {
                Spacer(Modifier.height(8.dp))
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                Text(
                    text = "⏰ Última vez visto: ${dateFormat.format(Date(device.lastSeen))}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun EmptyNetworkCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔍", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No se encontraron dispositivos",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Asegúrate de estar conectado a una red Wi-Fi local",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TechnicalLimitationsNetworkCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ℹ️ Limitaciones técnicas (sin root)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D8BF4)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "• Escaneo basado en tabla ARP del sistema (/proc/net/arp)\n" +
                       "• Puede no detectar dispositivos inactivos o en redes aisladas\n" +
                       "• Escaneo de puertos limitado a TCP (sin UDP)\n" +
                       "• No es posible bloquear dispositivos sin acceso al router\n" +
                       "• Monitoreo de tráfico requiere permisos de administrador\n\n" +
                       "✅ Todo el análisis es 100% local, sin envío de datos",
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
    }
}

private fun getPortDescription(port: Int): String = when (port) {
    22 -> "(SSH)"
    23 -> "(Telnet)"
    80 -> "(HTTP)"
    443 -> "(HTTPS)"
    445 -> "(SMB)"
    554 -> "(RTSP)"
    1883 -> "(MQTT)"
    3306 -> "(MySQL)"
    3389 -> "(RDP)"
    5000 -> "(UPnP)"
    5353 -> "(mDNS)"
    8080 -> "(HTTP-Alt)"
    8443 -> "(HTTPS-Alt)"
    9000 -> "(PHP-FPM)"
    else -> ""
}
