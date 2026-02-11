package com.guardianos.core.pro.ui

import android.widget.Toast
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
import com.guardianos.core.pro.consulting.ConsultingManager
import java.io.File

@Composable
fun ConsultingScreen(context: android.content.Context, pdfPath: String, onBack: () -> Unit) {
    val hasPdf = pdfPath.isNotEmpty() && File(pdfPath).exists()
    
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
        if (hasPdf) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                )
            ) {
                Text(
                    text = "✅ Informe listo para enviar\nPDF: ${File(pdfPath).name}",
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠️ No hay informe disponible",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Primero realiza un escaneo y exporta el PDF desde la pantalla de resultados.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Botón enviar
        Button(
            onClick = {
                if (hasPdf) {
                    try {
                        ConsultingManager.sendConsultingEmail(context, pdfPath)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Error al abrir cliente de email: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Primero genera un informe PDF desde la pantalla de resultados",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasPdf
        ) {
            Text("📧 Enviar Consulta por Email")
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
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }
}
