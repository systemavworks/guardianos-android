package com.guardianos.core.pro.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ConsultingScreen(context: android.content.Context, pdfPath: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var recentPdf by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Buscar PDFs automáticamente al cargar la pantalla
    LaunchedEffect(Unit) {
        scope.launch {
            recentPdf = withContext(Dispatchers.IO) {
                findMostRecentPDF(context)
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
            text = "👥 Consultoría Personalizada",
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
                text = "¿Tienes dudas sobre los resultados? ¿Necesitas ayuda experta para interpretar un hallazgo? Envía tu informe y recibirás respuesta personalizada del equipo GuardianOS.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Información del servicio
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📝 ¿Qué incluye?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "✓ Análisis experto de tu informe\n" +
                            "✓ Recomendaciones personalizadas\n" +
                            "✓ Guía para desinfectar dispositivos\n" +
                            "✓ Orientación legal si procede\n" +
                            "✓ Respuesta en 24-48 horas hábiles",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Estado del PDF
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else if (recentPdf != null && recentPdf!!.exists()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "✅ Informe encontrado",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "PDF: ${recentPdf!!.name}\nFecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(recentPdf!!.lastModified())}",
                        fontSize = 11.sp,
                        color = Color(0xFF1B5E20)
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2196F3).copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ℹ️ Sin informe reciente",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Puedes enviar consultas generales sin adjuntar informe, o escanear primero para adjuntar el PDF.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Botones de acción
        Button(
            onClick = {
                try {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("soporte@guardianos.es"))
                        putExtra(Intent.EXTRA_SUBJECT, "Consultoría GuardianOS PRO")
                        
                        // Adjuntar PDF si existe
                        if (recentPdf != null && recentPdf!!.exists()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                recentPdf!!
                            )
                            // Cambiar a ACTION_SEND para adjuntos
                            action = Intent.ACTION_SEND
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT,
                                "Hola equipo GuardianOS,\n\n" +
                                "Adjunto mi informe de seguridad reciente y solicito consultoría sobre:\n\n" +
                                "[Describe aquí tu duda o situación]\n\n" +
                                "Gracias por el soporte."
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            putExtra(Intent.EXTRA_TEXT,
                                "Hola equipo GuardianOS,\n\n" +
                                "Solicito consultoría sobre:\n\n" +
                                "[Describe aquí tu duda o situación]\n\n" +
                                "Gracias por el soporte."
                            )
                        }
                    }
                    
                    context.startActivity(Intent.createChooser(emailIntent, "Enviar consulta"))
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Error al abrir cliente de email: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8B5CF6)
            )
        ) {
            Text(if (recentPdf != null) "📧 Enviar Consulta (PDF adjunto)" else "📧 Enviar Consulta")
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Información de contacto alternativa
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "📧 Email directo: soporte@guardianos.es",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "🌐 Web: https://guardianos.es/consultas",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Volver")
        }
    }
}

/**
 * Busca el PDF más reciente en las carpetas de documentos
 */
private fun findMostRecentPDF(context: android.content.Context): File? {
    return try {
        // Buscar en Documents y Downloads
        val documentsDir = context.getExternalFilesDir(null)
        val downloadDir = context.getExternalFilesDir("Downloads")
        
        val allPdfs = mutableListOf<File>()
        
        // Buscar en Documents
        documentsDir?.listFiles { file -> 
            file.isFile && file.extension.lowercase() == "pdf" && 
            file.name.contains("GuardianOS", ignoreCase = true)
        }?.let { allPdfs.addAll(it) }
        
        // Buscar en Downloads
        downloadDir?.listFiles { file -> 
            file.isFile && file.extension.lowercase() == "pdf" && 
            file.name.contains("GuardianOS", ignoreCase = true)
        }?.let { allPdfs.addAll(it) }
        
        // Retornar el más reciente
        allPdfs.maxByOrNull { it.lastModified() }
    } catch (e: Exception) {
        null
    }
}
