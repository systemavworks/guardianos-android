package com.guardianos.core.pro.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.audit.detector.RiskScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de detección de stalkerware
 * 
 * Muestra resultados del análisis combinado de:
 * - AccessibilityMonitor (servicios que leen pantalla)
 * - HiddenAppsDetector (apps ocultas sin ícono)
 * - BackgroundServicesAnalyzer (servicios persistentes 24/7)
 * - RiskScorer (puntuación unificada 0-100)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StalkerwareScreen(
    context: Context,
    onBack: () -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf<List<RiskScorer.StalkerwareRiskReport>>(emptyList()) }
    var scanCompleted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚨 Detección de Stalkerware") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFDC2626),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())  // Hacer toda la pantalla scrolleable
        ) {
            if (!scanCompleted) {
                // Estado inicial: info + botón de escaneo
                StalkerwareInfoCard()
                
                Spacer(Modifier.height(24.dp))
                
                if (isScanning) {
                    // Escaneando...
                    ScanningIndicator()
                } else {
                    // Botón de iniciar escaneo (siempre visible ahora)
                    Button(
                        onClick = {
                            scope.launch {
                                isScanning = true
                                android.util.Log.d("StalkerwareScreen", "Iniciando escaneo de stalkerware...")
                                try {
                                    reports = withContext(Dispatchers.IO) {
                                        android.util.Log.d("StalkerwareScreen", "Ejecutando RiskScorer.scanAllAppsForStalkerware()")
                                        RiskScorer.scanAllAppsForStalkerware(context)
                                    }
                                    android.util.Log.d("StalkerwareScreen", "Escaneo completado: ${reports.size} apps con riesgo")
                                    scanCompleted = true
                                } catch (e: OutOfMemoryError) {
                                    android.util.Log.e("StalkerwareScreen", "OutOfMemoryError durante escaneo stalkerware", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        "⚠️ Memoria insuficiente. Dispositivo con demasiadas apps instaladas (${try { context.packageManager.getInstalledApplications(0).size } catch (_: Exception) { "?" }}). Intenta desinstalar apps no usadas.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    isScanning = false
                                    scanCompleted = false
                                    reports = emptyList()
                                    // Forzar garbage collection
                                    System.gc()
                                } catch (e: Exception) {
                                    android.util.Log.e("StalkerwareScreen", "Error durante escaneo stalkerware", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        "❌ Error en el escaneo: ${e.message}\n\nIntenta reiniciar la app.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    isScanning = false
                                    scanCompleted = false
                                    reports = emptyList()
                                } finally {
                                    if (isScanning) {
                                        isScanning = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Iniciar Escaneo Completo",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Resultados del escaneo
                StalkerwareResultsScreen(
                    reports = reports,
                    onRescan = {
                        scanCompleted = false
                        reports = emptyList()
                    },
                    onExportPDF = {
                        scope.launch {
                            try {
                                val file = com.guardianos.core.pdf.ItextPDFGenerator.generateStalkerwareReport(
                                    context,
                                    reports,
                                    forensicMode = true
                                )
                                
                                // Compartir vía email
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Reporte ForenseStalkerware - GuardianOS PRO")
                                    putExtra(android.content.Intent.EXTRA_TEXT, 
                                        "Reporte de detección de stalkerware generado por GuardianOS PRO.\n\n" +
                                        "Este análisis identifica aplicaciones con comportamiento de espionaje " +
                                        "usando técnicas de detección avanzadas."
                                    )
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                
                                val chooserIntent = android.content.Intent.createChooser(shareIntent, "Compartir reporte stalkerware")
                                chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooserIntent)
                                
                                android.widget.Toast.makeText(
                                    context,
                                    "PDF forense generado: ${file.name}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Error al generar PDF: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StalkerwareInfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            // QUITAMOS verticalScroll aquí porque ya lo tiene el Column padre
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFDC2626).copy(alpha = 0.15f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🚨 ¿Qué es stalkerware?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Apps diseñadas para espiar tu dispositivo sin tu consentimiento. " +
                          "Leen contraseñas, mensajes, ubicación, fotos y llamadas.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔍 Qué analizamos",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                
                DetectionMethodItem(
                    icon = "👁️",
                    title = "Servicios de Accesibilidad",
                    description = "Apps que pueden leer pantalla y contraseñas"
                )
                DetectionMethodItem(
                    icon = "👻",
                    title = "Apps Ocultas",
                    description = "Apps sin ícono o con nombres invisibles"
                )
                DetectionMethodItem(
                    icon = "⏰",
                    title = "Servicios Persistentes",
                    description = "Apps activas 24/7 en segundo plano"
                )
                DetectionMethodItem(
                    icon = "📍",
                    title = "Permisos Críticos",
                    description = "SMS, ubicación, cámara, contactos"
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Privacidad garantizada",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "El análisis es 100% local. Nunca enviamos datos a servidores externos. " +
                          "No usamos root ni técnicas invasivas.",
                    fontSize = 12.sp,
                    color = Color(0xFF0F766E),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun DetectionMethodItem(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun ScanningIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = Color(0xFFDC2626),
            strokeWidth = 6.dp
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Analizando dispositivo...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Esto puede tardar 30-60 segundos",
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScanStep("✓ Servicios de accesibilidad")
                ScanStep("✓ Apps ocultas")
                ScanStep("✓ Servicios en segundo plano")
                ScanStep("⏳ Calculando puntuación...")
            }
        }
    }
}

@Composable
fun ScanStep(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (text.startsWith("✓")) Color(0xFF10B981) else Color.Gray
        )
    }
}

@Composable
fun StalkerwareResultsScreen(
    reports: List<RiskScorer.StalkerwareRiskReport>,
    onRescan: () -> Unit,
    onExportPDF: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Resumen estadístico
        StatisticsCard(reports)
        
        Spacer(Modifier.height(16.dp))
        
        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón de re-escanear
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Re-escanear")
            }
            
            // Botón exportar PDF
            Button(
                onClick = onExportPDF,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626)
                )
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Exportar PDF")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (reports.isEmpty()) {
            // Sin amenazas
            SafeDeviceCard()
        } else {
            // Lista de apps con riesgo
            Text(
                text = "⚠️ Apps con Comportamiento Sospechoso",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reports) { report ->
                    StalkerwareReportCard(report)
                }
            }
        }
    }
}

@Composable
fun StatisticsCard(reports: List<RiskScorer.StalkerwareRiskReport>) {
    val criticalCount = reports.count { 
        it.riskLevel == RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED 
    }
    val highCount = reports.count { 
        it.riskLevel == RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION 
    }
    val mediumCount = reports.count { 
        it.riskLevel == RiskScorer.StalkerwareRiskLevel.MEDIUM 
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                criticalCount > 0 -> Color(0xFFDC2626).copy(alpha = 0.15f)
                highCount > 0 -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                mediumCount > 0 -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                else -> Color(0xFF10B981).copy(alpha = 0.15f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                criticalCount > 0 -> Color(0xFFDC2626)
                highCount > 0 -> Color(0xFFF59E0B)
                mediumCount > 0 -> Color(0xFF3B82F6)
                else -> Color(0xFF10B981)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (reports.isEmpty()) {
                    "✅ Dispositivo seguro"
                } else {
                    "⚠️ Resultados del escaneo"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    criticalCount > 0 -> Color(0xFFDC2626)
                    highCount > 0 -> Color(0xFFF59E0B)
                    mediumCount > 0 -> Color(0xFF3B82F6)
                    else -> Color(0xFF10B981)
                }
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Apps analizadas", reports.size.toString(), Color.Gray)
                if (criticalCount > 0) {
                    StatItem("CRÍTICO", criticalCount.toString(), Color(0xFFDC2626))
                }
                if (highCount > 0) {
                    StatItem("ALTO", highCount.toString(), Color(0xFFF59E0B))
                }
                if (mediumCount > 0) {
                    StatItem("MEDIO", mediumCount.toString(), Color(0xFF3B82F6))
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

@Composable
fun SafeDeviceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "✅ No se detectó stalkerware",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tu dispositivo no tiene apps con comportamientos sospechosos de espionaje.",
                fontSize = 14.sp,
                color = Color.Gray,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StalkerwareReportCard(report: RiskScorer.StalkerwareRiskReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (report.riskLevel) {
                RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> 
                    Color(0xFFDC2626).copy(alpha = 0.15f)
                RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> 
                    Color(0xFFF59E0B).copy(alpha = 0.15f)
                RiskScorer.StalkerwareRiskLevel.MEDIUM -> 
                    Color(0xFF3B82F6).copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (report.riskLevel) {
                RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> Color(0xFFDC2626)
                RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> Color(0xFFF59E0B)
                RiskScorer.StalkerwareRiskLevel.MEDIUM -> Color(0xFF3B82F6)
                else -> Color.Gray
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Encabezado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.appName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = report.packageName,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                
                Spacer(Modifier.width(8.dp))
                
                // Puntuación
                Surface(
                    color = when (report.riskLevel) {
                        RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> Color(0xFFDC2626)
                        RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> Color(0xFFF59E0B)
                        RiskScorer.StalkerwareRiskLevel.MEDIUM -> Color(0xFF3B82F6)
                        else -> Color.Gray
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${report.totalScore}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Nivel de riesgo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (report.riskLevel) {
                        RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> "🚨 STALKERWARE CONFIRMADO"
                        RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> "⚠️ SOSPECHA ALTA"
                        RiskScorer.StalkerwareRiskLevel.MEDIUM -> "👀 RIESGO MEDIO"
                        else -> "✓ RIESGO BAJO"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (report.riskLevel) {
                        RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> Color(0xFFDC2626)
                        RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> Color(0xFFF59E0B)
                        RiskScorer.StalkerwareRiskLevel.MEDIUM -> Color(0xFF3B82F6)
                        else -> Color.Gray
                    }
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Comportamientos detectados
            if (report.behaviorFlags.isNotEmpty()) {
                Text(
                    text = "Comportamientos detectados:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
                report.behaviorFlags.forEach { flag ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("• ", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = flag,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Desglose de puntuación
            Text(
                text = "Desglose de puntuación:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(Modifier.height(6.dp))
            report.scoringBreakdown.forEach { (factor, points) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = factor,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "+$points pts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            points >= 30 -> Color(0xFFDC2626)
                            points >= 15 -> Color(0xFFF59E0B)
                            else -> Color.Gray
                        }
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Acción recomendada
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (report.riskLevel) {
                        RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> 
                            Color(0xFFDC2626).copy(alpha = 0.15f)
                        RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> 
                            Color(0xFFF59E0B).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📋 Recomendación:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = report.recommendedAction,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
