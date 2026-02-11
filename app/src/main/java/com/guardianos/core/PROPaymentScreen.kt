package com.guardianos.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.BuildConfig
import kotlinx.coroutines.launch

/**
 * Pantalla de pago PRO ético sin Google Play Billing.
 * 
 * Filosofía:
 * - Pago único de 9,99€ (sin suscripciones ni renovaciones automáticas)
 * - Sin trackers de Google (no usa Google Play Billing)
 * - Pago web externo en https://guardianos.es/pro
 * - Activación offline con códigos GUAR-XXXX-XXXX-XXXX
 * - Transparencia radical sobre privacidad y modelo de negocio
 * 
 * @param context Contexto de Android para abrir navegador
 * @param onActivationSuccess Callback al activar PRO exitosamente
 * @param onBack Callback para volver atrás
 */
@Composable
fun PROPaymentScreen(
    context: Context,
    onActivationSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var activationCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showWebPayment by remember { mutableStateOf(false) }
    var showEthicalPromise by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header con valor ético
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF5D8BF4).copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, Color(0xFF5D8BF4))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⭐", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "GuardianOS PRO",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D8BF4)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "9,99€ • Pago único • Sin suscripciones",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Valor PRO explicado sin presión
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "✨ ¿Qué incluye GuardianOS PRO?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                listOf(
                    "🔍 Detección avanzada de stalkerware (apps que espían sin consentimiento)",
                    "🛡️ Guardian Shield: monitorización en tiempo real de accesos a cámara/micrófono",
                    "📁 Bóveda cifrada AES-256 para documentos familiares sensibles",
                    "⚖️ Informes forenses válidos para procedimientos legales",
                    "📊 Análisis de red: conexiones activas y servidores sospechosos",
                    "📞 Control de acceso multimedia: qué apps acceden a fotos/contactos/llamadas",
                    "📄 Auditoría ISO 27001: cumplimiento de estándares de seguridad",
                    "📧 Consultoría personalizada incluida (1 sesión gratuita)"
                ).forEachIndexed { index, feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "✓",
                            color = Color(0xFF10B981),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = feature,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                    if (index < 7) Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ✅ PROMESA ÉTICA DESTACADA (diferenciador clave)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
            ),
            border = BorderStroke(2.dp, Color(0xFF10B981))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Ético",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Nuestra promesa ética",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "• 🔒 CERO trackers, CERO analytics, CERO telemetry\n" +
                           "• 📱 100% de los análisis se ejecutan LOCALMENTE en tu dispositivo\n" +
                           "• ☁️ NUNCA guardamos tus datos en la nube\n" +
                           "• 💶 Pago único de 9,99€ (sin renovaciones automáticas)\n" +
                           "• 📜 Código abierto auditado bajo licencia GPL v3.0",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showEthicalPromise = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Ver política de privacidad completa", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Método de pago (web segura)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💳 Método de pago",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "Para respetar tu privacidad, NO usamos Google Play Billing ni ningún sistema con trackers. " +
                           "El pago se realiza directamente en nuestra web segura:",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = {
                        showWebPayment = true
                        // Abrir web en navegador externo (no WebView para mayor seguridad)
                        scope.launch {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://guardianos.es/pro"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Error al abrir navegador: ${e.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5D8BF4)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Comprar",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Pagar 9,99€ en guardianos.es", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "⚠️ Tras completar el pago recibirás un código de activación por email (info@guardianos.es) para introducir aquí abajo",
                    fontSize = 12.sp,
                    color = Color(0xFFFBBF24),
                    fontStyle = FontStyle.Italic
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Activación con código
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔑 Código de activación",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = activationCode,
                    onValueChange = {
                        activationCode = it.uppercase()
                        error = ""
                    },
                    label = { Text("Introduce tu código GUAR-XXXX-XXXX-XXXX") },
                    placeholder = { Text("GUAR-1234-5678-9012") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) {
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (activationCode.isBlank()) {
                            error = "Introduce tu código de activación"
                            return@Button
                        }
                        
                        // Validación offline (sin llamadas a servidor)
                        if (validateActivationCode(activationCode)) {
                            saveActivationState(context, true, activationCode)
                            
                            // Verificar persistencia
                            if (isProActivated(context)) {
                                Toast.makeText(
                                    context,
                                    "✅ ¡GuardianOS PRO activado! Bienvenido a la protección ética.",
                                    Toast.LENGTH_LONG
                                ).show()
                                onActivationSuccess()
                            } else {
                                error = "Error al guardar activación. Inténtalo de nuevo."
                            }
                        } else {
                            error = "Código inválido. Verifica que lo hayas copiado correctamente."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = activationCode.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    )
                ) {
                    Text("Activar GuardianOS PRO", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Información de contacto
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📧 Soporte y contacto",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "¿Problemas con el pago o activación?\n" +
                           "Escríbenos a: info@guardianos.es\n" +
                           "Web: https://guardianos.es",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6B7280)
            )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Volver"
            )
            Spacer(Modifier.width(8.dp))
            Text("Volver al inicio")
        }
    }

    // Diálogo ético explicativo
    if (showEthicalPromise) {
        AlertDialog(
            onDismissRequest = { showEthicalPromise = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = "Ético", tint = Color(0xFF10B981))
                    Spacer(Modifier.width(8.dp))
                    Text("Nuestra promesa de privacidad", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔒 GuardianOS respeta radicalmente tu privacidad:")
                    Text("• NUNCA incluimos trackers, analytics ni telemetry")
                    Text("• NUNCA enviamos datos de tu dispositivo a servidores")
                    Text("• NUNCA guardamos tus documentos en la nube")
                    Text("• Todo el análisis ocurre 100% LOCALMENTE en tu teléfono")
                    Text("• El cifrado de documentos es AES-256-GCM sin backdoors")
                    Text("• Código abierto bajo GPL v3.0 para auditoría pública")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Somos una alternativa ética a las apps de ciberseguridad que venden tus datos. " +
                        "Nuestro modelo es simple: tú pagas 9,99€ una vez y obtienes protección real sin comprometer tu privacidad.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontStyle = FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showEthicalPromise = false }) {
                    Text("Entendido")
                }
            }
        )
    }

    // Diálogo explicativo de pago web
    if (showWebPayment) {
        AlertDialog(
            onDismissRequest = { showWebPayment = false },
            title = { Text("🔒 Pago seguro en nuestra web") },
            text = {
                Text(
                    "Para proteger tu privacidad, NO usamos Google Play Billing (que incluye trackers de Google).\n\n" +
                    "El pago se realiza directamente en:\nhttps://guardianos.es/pro\n\n" +
                    "• Conexión HTTPS segura\n" +
                    "• Sin almacenamiento de tus datos bancarios\n" +
                    "• Recibirás tu código de activación por email en minutos\n" +
                    "• Pago único de 9,99€ (sin renovaciones automáticas)",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    showWebPayment = false
                    // Ya abrimos el navegador arriba
                }) {
                    Text("Ir a pagar ahora")
                }
            },
            dismissButton = {
                Button(onClick = { showWebPayment = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Valida código de activación offline.
 * 
 * Formato esperado: GUAR-XXXX-XXXX-XXXX
 * 
 * Esta función implementa validación básica offline.
 * Los códigos reales se generan con generate_pro_codes.py
 * 
 * @param code Código introducido por el usuario
 * @return true si el código es válido
 */
fun validateActivationCode(code: String): Boolean {
    // Validación básica de formato
    if (!code.matches(Regex("^GUAR-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$"))) {
        return false
    }
    
    // En producción: validar contra base de datos local de códigos válidos
    // o implementar algoritmo criptográfico de verificación offline
    
    // Por ahora, acepta cualquier código con formato correcto para testing
    // TODO: Implementar validación con pro_codes.txt o algoritmo HMAC
    return true
}

/**
 * Guarda estado de activación PRO en SharedPreferences.
 * 
 * @param context Contexto de Android
 * @param activated Estado de activación
 * @param code Código de activación usado
 */
fun saveActivationState(context: Context, activated: Boolean, code: String) {
    val prefs = context.getSharedPreferences("guardianos_pro", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putString("status", if (activated) "activated" else "free")
        putString("activation_code", code)
        putLong("activation_timestamp", System.currentTimeMillis())
        apply()
    }
}

/**
 * Verifica si PRO está activado.
 * 
 * @param context Contexto de Android
 * @return true si PRO está activado
 */
fun isProActivated(context: Context): Boolean {
    if (!BuildConfig.PRO_VERSION) return false
    
    val prefs = context.getSharedPreferences("guardianos_pro", Context.MODE_PRIVATE)
    return prefs.getString("status", "free") == "activated"
}
