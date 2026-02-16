/*
 * GuardianOS - Ethical digital protection for minors
 * Copyright (C) 2026 Victor Shift Lara
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.guardianos.core.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.crash.CrashHandler
import com.guardianos.core.network.DNSFixer
import com.guardianos.core.network.DNSStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla de diagnóstico técnico transparente.
 * Muestra crashes, DNS, y estado del sistema.
 * 
 * Filosofía ética: El usuario ve exactamente qué hace la app internamente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    context: Context,
    onBack: () -> Unit
) {
    var crashLogs by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var dnsStatus by remember { mutableStateOf<DNSStatus?>(null) }
    var isCheckingDNS by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Cargar crash logs al iniciar
    LaunchedEffect(Unit) {
        crashLogs = CrashHandler.getRecentCrashes(context)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111F))
    ) {
        // TopAppBar
        TopAppBar(
            title = { Text("🔧 Diagnóstico Técnico") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Volver")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1A1F2E)
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Banner transparencia
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, Color(0xFF10B981))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Esta pantalla muestra información técnica REAL de la app. " +
                        "Sin telemetría oculta, sin servidores externos.",
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Sección: Device Info
            SectionTitle("📱 Dispositivo")
            InfoCard {
                InfoRow("Fabricante", Build.MANUFACTURER)
                InfoRow("Modelo", Build.MODEL)
                InfoRow("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                InfoRow("Build", Build.DISPLAY)
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Sección: DNS Status
            SectionTitle("🌐 Estado de Conectividad DNS")
            
            val workaroundStatus = DNSFixer.getWorkaroundStatus(context)
            if (workaroundStatus.active) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFBBF24).copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFBBF24))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Workaround DNS Activo",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            workaroundStatus.reason ?: "DNS local bloqueado",
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            
            Button(
                onClick = {
                    isCheckingDNS = true
                    scope.launch {
                        dnsStatus = DNSFixer.checkDNSConnectivity(context)
                        isCheckingDNS = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCheckingDNS,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5D8BF4)
                )
            ) {
                if (isCheckingDNS) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isCheckingDNS) "Comprobando..." else "Comprobar Conectividad DNS")
            }
            
            dnsStatus?.let { status ->
                Spacer(Modifier.height(12.dp))
                InfoCard {
                    InfoRow(
                        "Estado",
                        if (status.isWorking) "✅ Funcionando" else "❌ Problemas detectados"
                    )
                    InfoRow("Servidores OK", "${status.workingCount}/${status.totalTests}")
                    
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Detalles de tests:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    status.tests.forEach { test ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    test.serverName,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    test.serverIP,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                if (test.success) "✅ ${test.latencyMs}ms" else "❌",
                                fontSize = 12.sp,
                                color = if (test.success) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Sección: Crash Logs
            SectionTitle("💥 Crash Logs Recientes (${crashLogs.size})")
            
            if (crashLogs.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✅", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Sin Crashes Detectados",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "La app no ha experimentado crashes recientemente",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                crashLogs.forEach { logFile ->
                    CrashLogCard(logFile)
                    Spacer(Modifier.height(8.dp))
                }
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = {
                        CrashHandler.clearAllCrashLogs(context)
                        crashLogs = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFEF4444)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Borrar Logs de Crashes")
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F2E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = Color.Gray
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun CrashLogCard(logFile: java.io.File) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    val date = dateFormat.format(Date(logFile.lastModified()))
    val sizeKB = logFile.length() / 1024
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        logFile.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$date • ${sizeKB}KB",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
