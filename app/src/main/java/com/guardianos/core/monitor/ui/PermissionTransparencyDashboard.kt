/*
 * GuardianOS - Ethical digital protection for minors
 * Copyright (C) 2026 Victor Shift Lara
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.guardianos.core.monitor.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.monitor.ActivePermissionUsage
import com.guardianos.core.monitor.PermissionType
import com.guardianos.core.monitor.RealTimePermissionMonitor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dashboard de Transparencia Ética - Monitorización en Tiempo Real
 * 
 * Filosofía: Informa sin alarmar, muestra límites técnicos transparentemente
 */
@Composable
fun PermissionTransparencyDashboard(
    context: Context,
    monitor: RealTimePermissionMonitor,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val recentUsages = remember { mutableStateListOf<ActivePermissionUsage>() }
    val activeSessions = remember { mutableStateListOf<ActivePermissionUsage>() }
    
    // Escuchar eventos en tiempo real
    LaunchedEffect(monitor) {
        scope.launch {
            monitor.activeUsages.collect { usage ->
                // Agregar al historial
                recentUsages.add(0, usage)
                if (recentUsages.size > 50) recentUsages.removeAt(recentUsages.size - 1)
                
                // Actualizar sesiones activas
                if (usage.isActive) {
                    val existing = activeSessions.indexOfFirst {
                        it.packageName == usage.packageName && it.permissionType == usage.permissionType
                    }
                    if (existing >= 0) {
                        activeSessions[existing] = usage
                    } else {
                        activeSessions.add(usage)
                    }
                } else {
                    // Remover sesiones finalizadas
                    activeSessions.removeAll {
                        it.packageName == usage.packageName && it.permissionType == usage.permissionType
                    }
                }
            }
        }
    }
    
    // Actualizar sesiones activas periódicamente
    LaunchedEffect(monitor.isMonitoring) {
        if (monitor.isMonitoring) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val current = monitor.getActiveSessions()
                activeSessions.clear()
                activeSessions.addAll(current)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header con filosofía ética
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B) // Azul-noche profundo
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Transparencia",
                        tint = Color(0xFF5D8BF4), // Azul ético
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "👁️ Transparencia Radical",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5D8BF4) // Azul ético
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "GuardianOS muestra EXACTAMENTE qué permisos están siendo usados en tiempo real. Sin alarmismo, sin juicios: solo datos verificables.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "🔒 Todo el análisis ocurre 100% en tu dispositivo. Nunca enviamos estos datos a ningún servidor.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50) // Verde esmeralda para cifrado local
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Control de monitorización
        GuardianShieldControlCard(
            isActive = monitor.isMonitoring,
            onToggle = { shouldActivate ->
                if (shouldActivate) {
                    monitor.startMonitoring()
                    Toast.makeText(
                        context,
                        "✅ Guardian Shield activado",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    monitor.stopMonitoring()
                    activeSessions.clear()
                    Toast.makeText(
                        context,
                        "Guardian Shield desactivado",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRequestPermissions = {
                // Abrir ajustes de uso de apps
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "No se puede abrir ajustes automáticamente",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Sesiones activas AHORA
        if (monitor.isMonitoring && activeSessions.isNotEmpty()) {
            Text(
                text = "🔴 Accesos ACTIVOS ahora (${activeSessions.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D8BF4)
            )
            Spacer(Modifier.height(12.dp))
            
            activeSessions.forEach { usage ->
                PermissionUsageCard(usage, isRecent = true, isActive = true)
                Spacer(Modifier.height(8.dp))
            }
            
            Spacer(Modifier.height(20.dp))
        }
        
        // Timeline de accesos recientes
        Text(
            text = "📅 Historial Reciente${if (recentUsages.isNotEmpty()) " (${recentUsages.size})" else ""}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        
        if (recentUsages.isEmpty()) {
            EmptyStateCard(
                icon = "🔍",
                title = "Sin accesos registrados",
                description = if (monitor.isMonitoring) {
                    "Cuando una app use permisos sensibles (cámara, micrófono, ubicación), aparecerá aquí en tiempo real."
                } else {
                    "Activa Guardian Shield arriba para comenzar la monitorización."
                }
            )
        } else {
            recentUsages.take(20).forEach { usage ->
                PermissionUsageCard(usage, isRecent = false, isActive = usage.isActive)
                Spacer(Modifier.height(6.dp))
            }
            
            if (recentUsages.size > 20) {
                Text(
                    text = "... y ${recentUsages.size - 20} accesos más antiguos",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Leyenda ética con límites técnicos
        TechnicalLimitationsCard()
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Volver")
        }
    }
}

@Composable
private fun GuardianShieldControlCard(
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                Color(0xFF4CAF50).copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isActive) BorderStroke(2.dp, Color(0xFF4CAF50)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isActive) "🛡️ Guardian Shield ACTIVO" else "🛡️ Guardian Shield",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isActive) {
                        "Monitorizando permisos en tiempo real"
                    } else {
                        "Activa para recibir alertas en tiempo real"
                    },
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            
            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun PermissionUsageCard(
    usage: ActivePermissionUsage,
    isRecent: Boolean,
    isActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> Color(0xFF5D8BF4).copy(alpha = 0.2f) // Azul ético destacado
                isRecent -> Color(0xFF5D8BF4).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isActive) BorderStroke(2.dp, Color(0xFF5D8BF4)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono del permiso
            Text(
                text = usage.permissionType.icon(),
                fontSize = 28.sp
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = usage.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF5D8BF4),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "AHORA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = "${usage.permissionType.humanReadable()}${if (usage.durationMs > 0) " • ${formatDuration(usage.durationMs)}" else ""}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                if (!usage.isForeground) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "⚠️ En segundo plano",
                        fontSize = 11.sp,
                        color = Color(0xFFFBBF24), // Amarillo ético (no rojo alarmista)
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Timestamp
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = getTimeAgo(usage.timestamp),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                if (!usage.isActive && usage.durationMs > 0) {
                    Text(
                        text = "Finalizado",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(icon: String, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun TechnicalLimitationsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "ℹ️ Límites técnicos transparentes",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D8BF4)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "• Android limita qué apps pueden ver otros permisos en uso (sin root no vemos apps específicas usando micrófono/cámara en algunos casos)\n\n" +
                       "• Solo mostramos permisos REALMENTE concedidos (no solo declarados en el manifest)\n\n" +
                       "• La precisión depende de los permisos que concedas a GuardianOS en Ajustes\n\n" +
                       "• Nunca usamos AccessibilityService (respetamos tu privacidad)\n\n" +
                       "• Monitorización cada 2 segundos (balance entre precisión y batería)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

// Helpers
private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).toInt()
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

private fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = (diff / 1000).toInt()
    
    return when {
        seconds < 5 -> "Ahora"
        seconds < 60 -> "Hace ${seconds}s"
        seconds < 3600 -> "Hace ${seconds / 60}m"
        seconds < 86400 -> "Hace ${seconds / 3600}h"
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
