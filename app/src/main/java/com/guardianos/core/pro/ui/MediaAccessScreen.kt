package com.guardianos.core.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.pro.media.MediaAccessScanner
import com.guardianos.core.pro.media.MediaStoreAnalyzer
import com.guardianos.core.crash.DeviceOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MediaAccessScreen(context: android.content.Context, onBack: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var appsInfo by remember { mutableStateOf<List<MediaAccessScanner.MediaAccessInfo>>(emptyList()) }
    var privacyReport by remember { mutableStateOf<MediaAccessScanner.MediaPrivacyReport?>(null) }
    var recentActivity by remember { mutableStateOf<List<MediaStoreAnalyzer.AppMediaActivity>>(emptyList()) }
    var deviceProfile by remember { mutableStateOf<DeviceOptimizer.DeviceProfile?>(null) }
    val scope = rememberCoroutineScope()
    
    // Cargar información al abrir
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            
            // Analizar dispositivo para ajustar optimizaciones
            deviceProfile = withContext(Dispatchers.IO) {
                DeviceOptimizer.analyzeDevice(context)
            }
            
            // Análisis de permisos
            appsInfo = withContext(Dispatchers.IO) {
                MediaAccessScanner.getDetailedMediaAccessInfo(context)
            }
            privacyReport = withContext(Dispatchers.IO) {
                MediaAccessScanner.generatePrivacyReport(context)
            }
            
            // Análisis de accesos reales (con protección anti-crash)
            try {
                android.util.Log.d("MediaAccessScreen", "🔍 Iniciando análisis de accesos multimedia reales...")
                recentActivity = withContext(Dispatchers.IO) {
                    MediaStoreAnalyzer.analyzeRecentMediaAccess(context, daysBack = 7)
                }
                android.util.Log.d("MediaAccessScreen", "✅ Análisis completado: ${recentActivity.size} apps con actividad")
            } catch (e: Exception) {
                android.util.Log.e("MediaAccessScreen", "❌ Error en MediaStore", e)
            }
            
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "📸 Apps con Acceso a Multimedia",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "Detección de apps con permisos OTORGADos (no solo solicitados) para acceder a fotos, vídeos, documentos y almacenamiento.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            Text(
                text = "Analizando apps instaladas...",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            // ✅ REPORTE ESTADÍSTICO COMPLETO
            privacyReport?.let { report ->
                PrivacyReportCard(report)
                Spacer(Modifier.height(16.dp))
            }
            
            if (appsInfo.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✅ Excelente: No se detectaron apps con acceso a fotos, vídeos o documentos sensibles.",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Esto significa:\n" +
                               "• Ninguna app tiene permisos OTORGADOS para leer tu multimedia\n" +
                               "• Tu privacidad está protegida a nivel de archivos\n" +
                               "• No hay apps sospechosas con acceso a fotos personales",
                        fontSize = 12.sp,
                        color = Color(0xFF1B5E20)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ℹ️ ¿Por qué no aparecen apps?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Esta función detecta apps con permisos OTORGADOS (no solo solicitados). " +
                               "Si no hay resultados, es positivo: significa que controlas bien los permisos de tu dispositivo.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            Text(
                text = "Encontradas: ${appsInfo.size} apps",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(12.dp))
            
            appsInfo.forEach { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (app.riskLevel) {
                            "CRÍTICO" -> Color(0xFF8B0000).copy(alpha = 0.20f)
                            "ALTO" -> Color(0xFFB3261E).copy(alpha = 0.15f)
                            "MEDIO" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = app.appName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = when (app.riskLevel) {
                                    "CRÍTICO" -> Color(0xFF8B0000)
                                    "ALTO" -> Color(0xFFB3261E)
                                    "MEDIO" -> Color(0xFFF59E0B)
                                    else -> Color(0xFF4CAF50)
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = app.riskLevel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = app.packageName,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        
                        // Mostrar última vez usada y frecuencia
                        if (app.lastUsed > 0) {
                            Spacer(Modifier.height(6.dp))
                            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            Text(
                                text = "⏰ Última vez: ${dateFormat.format(Date(app.lastUsed))} • ${app.usageFrequency}",
                                fontSize = 11.sp,
                                color = Color(0xFF5D8BF4),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Patrones sospechosos detectados
                        if (app.suspiciousPatterns.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Patrones detectados:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB3261E)
                            )
                            app.suspiciousPatterns.forEach { pattern ->
                                Text(
                                    text = pattern,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB3261E),
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Permisos otorgados:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        app.grantedPermissions.forEach { perm ->
                            Text(
                                text = "• ${perm.substringAfterLast('.')}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        }
        
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }
}

@Composable
private fun PrivacyReportCard(report: MediaAccessScanner.MediaPrivacyReport) {
    val reportColor = when {
        report.criticalApps > 0 -> Color(0xFFB3261E)
        report.highRiskApps > 0 -> Color(0xFFFBBF24)
        report.totalAppsWithAccess == 0 -> Color(0xFF10B981)
        else -> Color(0xFF5D8BF4)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = reportColor.copy(alpha = 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, reportColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 Resumen de Privacidad",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = reportColor
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Estadísticas principales
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("Total Apps", "${report.totalAppsWithAccess}")
                StatColumn("Críticas", "${report.criticalApps}", Color(0xFFB3261E))
                StatColumn("Alto Riesgo", "${report.highRiskApps}", Color(0xFFFBBF24))
            }
            
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            
            // Detalles específicos
            if (report.appsWithWriteAccess > 0) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("✏️", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${report.appsWithWriteAccess} apps pueden MODIFICAR/ELIMINAR archivos",
                        fontSize = 12.sp,
                        color = Color(0xFFB3261E)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            
            if (report.appsWithLocationAccess > 0) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("📍", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${report.appsWithLocationAccess} apps pueden extraer ubicación GPS de fotos",
                        fontSize = 12.sp,
                        color = Color(0xFFB3261E)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            
            if (report.suspiciousApps.isNotEmpty()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("👁️", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${report.suspiciousApps.size} apps con patrones sospechosos detectados",
                        fontSize = 12.sp,
                        color = Color(0xFFB3261E)
                    )
                }
            }
            
            // Recomendaciones
            if (report.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "💡 Recomendaciones:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                report.recommendations.forEach { rec ->
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
private fun StatColumn(label: String, value: String, color: Color = Color(0xFF5D8BF4)) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}
