package com.guardianos.core.pro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.domain.model.AppScanResult
import com.guardianos.core.pro.forensic.ForensicReportHelper

@Composable
fun ForensicReportScreen(results: List<AppScanResult>, onBack: () -> Unit) {
    val forensicSummary = ForensicReportHelper.generateForensicSummary(results)
    val legalText = ForensicReportHelper.generateLegalSummary(results)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "⚖️ Informe Forense Legal",
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
                text = "Informe forense con validez legal para procedimientos ante la AEPD, cuerpos de seguridad o juzgados. Incluye hash SHA-256 y marca temporal.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Resumen ejecutivo
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (forensicSummary.threatsDetected > 0) 
                    Color(0xFFB3261E).copy(alpha = 0.15f)
                else 
                    Color(0xFF4CAF50).copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📊 Resumen Ejecutivo",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Apps escaneadas", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "${forensicSummary.totalAppsScanned}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Amenazas", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "${forensicSummary.threatsDetected}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (forensicSummary.threatsDetected > 0) 
                                Color(0xFFB3261E) 
                            else 
                                Color(0xFF4CAF50)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alto riesgo", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "${forensicSummary.highRiskApps}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))
                
                Text(
                    text = "🔗 Hash: ${forensicSummary.reportHash.take(32)}...",
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "🗓️ Timestamp: ${forensicSummary.timestampFormatted}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Informe completo
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = legalText,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }
}
