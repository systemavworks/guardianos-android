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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaAccessScreen(context: android.content.Context, onBack: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var appsInfo by remember { mutableStateOf<List<MediaAccessScanner.MediaAccessInfo>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    // Cargar información al abrir
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            appsInfo = withContext(Dispatchers.IO) {
                MediaAccessScanner.getDetailedMediaAccessInfo(context)
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
        } else if (appsInfo.isEmpty()) {
            Card {
                Text(
                    text = "✅ No se detectaron apps con acceso a fotos o documentos sensibles.",
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFF4CAF50)
                )
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
        
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }
}
