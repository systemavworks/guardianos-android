package com.guardianos.vault.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.domain.model.AppAudit
import com.guardianos.core.pro.PanicMode
import com.guardianos.core.pro.ScanComparator
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla de comparativa de escaneos.
 */
@Composable
fun ScanComparisonScreen(
    context: Context,
    oldScan: List<AppAudit>,
    newScan: List<AppAudit>,
    onBack: () -> Unit
) {
    val comparison = remember(oldScan, newScan) {
        ScanComparator.compareScan(oldScan, newScan)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Comparativa de Escaneos",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Resumen
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Total de cambios: ${comparison.totalChanges}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ComparisonStat(
                        label = "Nuevas",
                        count = comparison.newApps.size,
                        color = Color(0xFF3B82F6)
                    )
                    
                    ComparisonStat(
                        label = "Eliminadas",
                        count = comparison.removedApps.size,
                        color = Color(0xFFFF6B6B)
                    )
                    
                    ComparisonStat(
                        label = "Modificadas",
                        count = comparison.modifiedApps.size,
                        color = Color(0xFFFFA726)
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Lista de cambios
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Apps nuevas
            if (comparison.newApps.isNotEmpty()) {
                item {
                    Text(
                        "➕ Apps nuevas (${comparison.newApps.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6)
                    )
                }
                
                items(comparison.newApps) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(app.appName, fontWeight = FontWeight.Bold)
                            Text(
                                "Riesgo: ${app.riskScore}/100",
                                fontSize = 12.sp,
                                color = when {
                                    app.riskScore >= 70 -> Color(0xFFFF6B6B)
                                    app.riskScore >= 40 -> Color(0xFFFFA726)
                                    else -> Color(0xFF22C55E)
                                }
                            )
                        }
                    }
                }
            }
            
            // Apps eliminadas
            if (comparison.removedApps.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "➖ Apps eliminadas (${comparison.removedApps.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B)
                    )
                }
                
                items(comparison.removedApps) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(app.appName, fontWeight = FontWeight.Bold)
                            Text(
                                "Era: ${app.installSource}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
            
            // Apps modificadas
            if (comparison.modifiedApps.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🔄 Apps modificadas (${comparison.modifiedApps.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFA726)
                    )
                }
                
                items(comparison.modifiedApps) { change ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(change.appName, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            change.changes.forEach { detail ->
                                Text(
                                    detail,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Si no hay cambios
            if (comparison.totalChanges == 0) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFF22C55E)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No se detectaron cambios",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonStat(
    label: String,
    count: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            count.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

/**
 * Pantalla de configuración del modo pánico.
 */
@Composable
fun PanicModeConfigScreen(
    context: Context,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var success by remember { mutableStateOf("") }
    var decoyMode by remember { mutableStateOf(PanicMode.isDecoyMode(context)) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Modo Pánico",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            "Configura un PIN de emergencia para proteger tus datos en situaciones de riesgo",
            fontSize = 14.sp,
            color = Color.Gray
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Advertencia
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6B6B).copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "⚠️ El modo pánico es IRREVERSIBLE. Usa solo en emergencias reales.",
                    fontSize = 13.sp,
                    color = Color(0xFFFF6B6B)
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Configuración de PIN
        Text(
            "PIN de Pánico",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { pin = it; error = ""; success = "" } },
            label = { Text("PIN (4 dígitos)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Ej: 9999") }
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { confirmPin = it; error = ""; success = "" } },
            label = { Text("Confirmar PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = {
                when {
                    pin.length != 4 -> error = "El PIN debe tener exactamente 4 dígitos"
                    pin != confirmPin -> error = "Los PINs no coinciden"
                    else -> {
                        PanicMode.setPanicPin(context, pin).onSuccess {
                            success = "✅ PIN de pánico configurado correctamente"
                            pin = ""
                            confirmPin = ""
                        }.onFailure {
                            error = it.message ?: "Error al configurar PIN"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Configurar PIN de Pánico")
        }
        
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color.Red, fontSize = 13.sp)
        }
        
        if (success.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(success, color = Color(0xFF22C55E), fontSize = 13.sp)
        }
        
        Spacer(Modifier.height(32.dp))
        
        HorizontalDivider()
        
        Spacer(Modifier.height(24.dp))
        
        // Modo señuelo
        Text(
            "Modo de Activación",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (decoyMode) Color(0xFF3B82F6).copy(alpha = 0.1f) else Color(0xFFFF6B6B).copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (decoyMode) "Modo Señuelo" else "Modo Destrucción",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (decoyMode) Color(0xFF3B82F6) else Color(0xFFFF6B6B)
                        )
                        
                        Text(
                            if (decoyMode) 
                                "Muestra datos falsos sin destruir nada" 
                            else 
                                "Borra TODOS los datos del vault permanentemente",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Switch(
                        checked = decoyMode,
                        onCheckedChange = { 
                            decoyMode = it
                            PanicMode.enableDecoyMode(context, it)
                        }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        HorizontalDivider()
        
        Spacer(Modifier.height(24.dp))
        
        // Información de uso
        Text(
            "¿Cómo funciona?",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(12.dp))
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PanicModeInfoItem(
                number = "1",
                text = "Configura un PIN de 4 dígitos diferente a tu contraseña maestra"
            )
            
            PanicModeInfoItem(
                number = "2",
                text = "En situación de emergencia, introduce el PIN de pánico en lugar de tu contraseña normal"
            )
            
            PanicModeInfoItem(
                number = "3",
                text = if (decoyMode) 
                    "Se activará el modo señuelo: verás datos falsos para despistar" 
                else 
                    "Se borrarán TODOS los datos del vault de forma permanente"
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Botón de prueba de destrucción (solo en modo debug)
        if (!decoyMode) {
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Probar Modo Pánico (CUIDADO)")
            }
        }
    }
    
    // Diálogo de confirmación para prueba de pánico
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("⚠️ ADVERTENCIA CRÍTICA") },
            text = {
                Column {
                    Text(
                        "Estás a punto de BORRAR PERMANENTEMENTE:",
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("• Todas las credenciales del Family Vault")
                    Text("• Todos los documentos del Document Vault")
                    Text("• Todo el historial de escaneos")
                    Text("• Configuración del modo pánico")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Esta acción NO SE PUEDE DESHACER.",
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = PanicMode.executePanicAction(context)
                        showConfirmDialog = false
                        // Volver atrás tras destruir
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("SÍ, BORRAR TODO")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            },
            containerColor = Color(0xFF1A2332)
        )
    }
}

@Composable
fun PanicModeInfoItem(
    number: String,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    Color(0xFF3B82F6).copy(alpha = 0.2f),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3B82F6)
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Text(
            text,
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
