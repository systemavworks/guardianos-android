package com.guardianos.core.pro.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.pro.privacy.PrivacyProactiveManager
import kotlinx.coroutines.launch

@Composable
fun PrivacyProactiveScreen(context: android.content.Context, onBack: () -> Unit) {
    var showPanicConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "🛡️ Privacidad Proactiva",
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
                text = "Herramientas avanzadas para proteger tu privacidad en situaciones de emergencia o cuando necesites control total sobre los datos sensibles.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Modo Sigilo
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🕵️ Modo Sigilo",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Revoca temporalmente permisos críticos (cámara, micrófono, ubicación) de todas las apps para evitar vigilancia no autorizada.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { PrivacyProactiveManager.openPermissionSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔧 Abrir Ajustes de Privacidad")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "💡 Consejo: Revoca permisos a apps que no uses diariamente para reducir superficie de ataque.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Modo Pánico
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFB3261E).copy(alpha = 0.15f)
            ),
            border = BorderStroke(2.dp, Color(0xFFB3261E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Advertencia",
                        tint = Color(0xFFB3261E),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "🚨 Modo Pánico",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB3261E)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "ACCIÓN IRREVERSIBLE: Borra instantáneamente todos los datos sensibles:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "• Bóveda familiar cifrada\n" +
                            "• Historial de escaneos PRO\n" +
                            "• Configuración de Guardian Shield\n" +
                            "• Credenciales guardadas\n" +
                            "• Documentos cifrados",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "⚠️ Usar solo en emergencias (violencia digital, acceso no autorizado al dispositivo, etc.)",
                    fontSize = 12.sp,
                    color = Color(0xFFB3261E),
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showPanicConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB3261E)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🚨 Activar Modo Pánico")
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }
    
    // Diálogo de confirmación de pánico
    if (showPanicConfirm) {
        AlertDialog(
            onDismissRequest = { showPanicConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Advertencia",
                    tint = Color(0xFFB3261E)
                )
            },
            title = { Text("¿Activar Modo Pánico?") },
            text = {
                Text(
                    text = "Esta acción es IRREVERSIBLE.\n\n" +
                            "Se borrarán todos los datos sensibles de GuardianOS inmediatamente.\n\n" +
                            "¿Estás seguro?",
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            PrivacyProactiveManager.triggerPanic(context)
                            Toast.makeText(
                                context,
                                "🚨 Modo pánico activado. Datos borrados.",
                                Toast.LENGTH_LONG
                            ).show()
                            showPanicConfirm = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB3261E)
                    )
                ) {
                    Text("SÍ, BORRAR TODO")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
