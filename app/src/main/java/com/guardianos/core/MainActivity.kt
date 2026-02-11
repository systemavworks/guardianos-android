/*
 * GuardianOS - Ethical digital protection for minors
 * Copyright (C) 2026 Victor Shift Lara
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.guardianos.core

import com.guardianos.core.BuildConfig
import com.guardianos.core.audit.AppAuditor
import com.guardianos.core.audit.ISOAuditor
import com.guardianos.core.domain.model.ISOViolation
import com.guardianos.core.audit.detector.StalkerwareDetector
import com.guardianos.core.monitor.GuardianShieldMonitor
import com.guardianos.core.monitor.PermissionAccessInfo
import com.guardianos.core.monitor.RealTimePermissionMonitor
import com.guardianos.core.monitor.ui.PermissionTransparencyDashboard
import com.guardianos.core.network.NetworkGuardian
import com.guardianos.core.data.MalwareDatabase
import com.guardianos.core.pdf.PDFGenerator
import com.guardianos.core.pdf.ItextPDFGenerator
import com.guardianos.core.pro.PrivacyAnalyzer
import com.guardianos.core.pro.ProActivationManager
import com.guardianos.core.pro.ScanHistory
import com.guardianos.core.pro.ui.ProFeatureCTA
import com.guardianos.core.pro.ui.NetworkAnalyzerScreen
import com.guardianos.core.pro.ui.MediaAccessScreen
import com.guardianos.core.pro.ui.ForensicReportScreen
import com.guardianos.core.pro.ui.PrivacyProactiveScreen
import com.guardianos.core.pro.ui.ConsultingScreen
import com.guardianos.core.pro.ui.ScanHistoryScreen
import com.guardianos.vault.ui.MasterPasswordSetupScreen
import com.guardianos.vault.ui.VaultUnlockScreen
import com.guardianos.vault.ui.FamilyVaultMainScreen
import com.guardianos.vault.security.VaultSecurityManager
import com.guardianos.core.crash.CrashHandler
import com.guardianos.core.network.DNSFixer
import com.guardianos.core.ui.DiagnosticsScreen
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.guardianos.core.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    private val malwareSignatures = MalwareDatabase()
    private val appAuditor = AppAuditor()

    companion object {
        private const val TAG = "MainActivity"
        private const val APP_VERSION = "2.0.0"
        private const val DEVELOPER_NAME = "Victor Shift Lara"
        private const val DEVELOPER_EMAIL = "info@guardianos.es"
        private const val WEB_URL = "https://guardianos.es"
        private const val LICENSE = "GNU General Public License v3.0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ⚠️ PRIORIDAD P0: Estabilización antes de features
        // 1. Crash handler minimalista (sin trackers)
        CrashHandler.initialize(this)
        
        // 2. Workaround DNS para OPPO A80 y similares
        DNSFixer.applyWorkaroundIfNeeded(this)
        
        Log.i(TAG, "✅ GuardianOS ${APP_VERSION} inicializado (crash handler + DNS workaround activos)")
        
        setContent { GuardianOSApp() }
    }

    /**
     * Verifica si la versión PRO está activada.
     */
    private fun isProActivated(context: Context): Boolean {
        return ProActivationManager.isProActivated(context)
    }

    /**
     * Guarda el estado de activación PRO.
     */
    private fun saveActivationState(context: Context, activated: Boolean, code: String) {
        ProActivationManager.saveActivationState(context, activated, code)
    }

    /**
     * Valida si el código de activación Pro es válido.
     * Soporta dos formatos:
     * 1. Firma digital RSA: GUAR-[DATA]-[SIGNATURE] (más seguro)
     * 2. Simple: GUAR-XXXX-XXXX-XXXX (fallback)
     */
    private fun validateActivationCode(code: String): Boolean {
        return try {
            // Intentar validación con firma digital primero
            if (ProActivationManager.validateActivationCode(code, "")) {
                return true
            }
            
            // Fallback: validación simple
            ProActivationManager.validateSimpleCode(code)
        } catch (e: Exception) {
            Log.e(TAG, "Error validating activation code", e)
            false
        }
    }

    /* ────────────────────────── UI ────────────────────────── */

    // Family Document Vault: UI y lógica básica
    @Composable
    fun DocumentVaultScreen(context: Context, onBack: () -> Unit) {
        var password by remember { mutableStateOf("") }
        var docName by remember { mutableStateOf("") }
        var docType by remember { mutableStateOf(com.guardianos.vault.data.DocumentType.DNI) }
        var selectedFileName by remember { mutableStateOf<String?>(null) }
        var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
        var docs by remember { mutableStateOf(listOf<com.guardianos.vault.data.FamilyDocument>()) }
        var error by remember { mutableStateOf("") }
        var showConsent by remember { mutableStateOf(false) }
        
        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    fileBytes = inputStream?.readBytes()
                    inputStream?.close()
                    
                    // Obtener nombre del archivo
                    val cursor = context.contentResolver.query(it, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                selectedFileName = c.getString(nameIndex)
                            }
                        }
                    }
                    
                    if (selectedFileName == null) {
                        selectedFileName = "documento_${System.currentTimeMillis()}"
                    }
                    
                    error = ""
                } catch (e: Exception) {
                    error = "Error al leer archivo: ${e.localizedMessage}"
                    Log.e(TAG, "Error reading file", e)
                }
            }
        }
        
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("📁 Bóveda de Documentos", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔒 Cifrado AES-256-GCM local",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "Tus documentos nunca salen de tu dispositivo. Todo se cifra localmente.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña de Vault") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true
            )
            
            Button(
                onClick = {
                    try {
                        docs = com.guardianos.core.pro.DocumentVault.loadDocuments(context)
                        error = if (docs.isEmpty()) {
                            "📂 Vault vacío. Agrega documentos abajo."
                        } else {
                            "✅ ${docs.size} documentos cargados. La contraseña se usa para guardar/abrir archivos."
                        }
                    } catch (e: Exception) {
                        error = "Error al cargar: ${e.localizedMessage}"
                        Log.e(TAG, "Error loading documents", e)
                    }
                },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            ) {
                Text("📂 Ver Documentos Guardados")
            }
            
            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    color = when {
                        error.startsWith("✅") -> Color(0xFF10B981)
                        error.startsWith("📂") -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    },
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Agregar nuevo documento
            Text("➕ Agregar Nuevo Documento", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            
            OutlinedTextField(
                value = docName,
                onValueChange = { docName = it },
                label = { Text("Nombre (ej: DNI Juan)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Selector de tipo de documento
            Text("Tipo de documento:", fontSize = 14.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    com.guardianos.vault.data.DocumentType.DNI to "🪪 DNI",
                    com.guardianos.vault.data.DocumentType.PASSPORT to "🛂 Pasaporte",
                    com.guardianos.vault.data.DocumentType.HEALTH_CARD to "🏥 Seguro",
                    com.guardianos.vault.data.DocumentType.OTHER to "📄 Otro"
                ).forEach { (type, label) ->
                    Button(
                        onClick = { docType = type },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (docType == type) 
                                MaterialTheme.colorScheme.secondary 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, fontSize = 10.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Botón para seleccionar archivo
            Button(
                onClick = { 
                    if (password.isBlank()) {
                        error = "❌ Configura una contraseña primero"
                    } else {
                        filePickerLauncher.launch("*/*")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedFileName != null) 
                        Color(0xFF10B981) 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (selectedFileName != null) 
                        "📎 Archivo: $selectedFileName" 
                    else 
                        "📎 Seleccionar archivo"
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Botón guardar
            Button(
                onClick = { 
                    when {
                        password.isBlank() -> error = "❌ Configura una contraseña"
                        docName.isBlank() -> error = "❌ Ingresa un nombre"
                        fileBytes == null -> error = "❌ Selecciona un archivo"
                        else -> showConsent = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = password.isNotBlank() && docName.isNotBlank() && fileBytes != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                )
            ) {
                Text("💾 Guardar Documento Cifrado")
            }
            
            if (showConsent) {
                AlertDialog(
                    onDismissRequest = { showConsent = false },
                    title = { Text("⚠️ Consentimiento Legal") },
                    text = {
                        Text(
                            "Vas a guardar un documento oficial (DNI, pasaporte, etc.) cifrado en tu dispositivo.\n\n" +
                            "✅ GuardianOS NUNCA subirá este archivo a la nube.\n" +
                            "✅ El cifrado es irreversible sin tu contraseña.\n" +
                            "✅ Eres responsable de su custodia.\n\n" +
                            "¿Confirmas guardar el documento?"
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            val doc = com.guardianos.vault.data.FamilyDocument(
                                id = java.util.UUID.randomUUID(),
                                name = docName,
                                type = docType,
                                encryptedFilePath = ""
                            )
                            
                            val result = com.guardianos.core.pro.DocumentVault.saveDocument(
                                context,
                                doc,
                                fileBytes!!,
                                password
                            )
                            
                            result.onSuccess {
                                docs = com.guardianos.core.pro.DocumentVault.loadDocuments(context)
                                docName = ""
                                selectedFileName = null
                                fileBytes = null
                                error = "✅ Documento cifrado y guardado correctamente"
                                showConsent = false
                                Toast.makeText(context, "✅ Documento guardado", Toast.LENGTH_SHORT).show()
                            }.onFailure { e ->
                                error = "❌ Error al guardar: ${e.localizedMessage}"
                                showConsent = false
                                Log.e(TAG, "Error saving document", e)
                            }
                        }) {
                            Text("✅ Aceptar y Guardar")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showConsent = false }) {
                            Text("❌ Cancelar")
                        }
                    }
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Lista de documentos
            if (docs.isNotEmpty()) {
                Text("📋 Documentos Guardados (${docs.size}):", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                
                docs.forEach { doc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (doc.type) {
                                com.guardianos.vault.data.DocumentType.DNI -> "🪪"
                                com.guardianos.vault.data.DocumentType.PASSPORT -> "🛂"
                                com.guardianos.vault.data.DocumentType.HEALTH_CARD -> "🏥"
                                else -> "📄"
                            }
                            Text(icon, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(doc.name, fontWeight = FontWeight.Bold)
                                Text("Tipo: ${doc.type}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B7280)
                )
            ) {
                Text("Volver")
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GuardianOSApp() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        var currentScreen by remember { mutableStateOf("home") }
        var scanResults by remember { mutableStateOf<List<AppScanResult>>(emptyList()) }
        var isScanning by remember { mutableStateOf(false) }
        var pdfPath by remember { mutableStateOf("") }
        val isPro = remember { mutableStateOf(isProActivated(context)) }

        // Estados adicionales requeridos por pantallas PRO
        var isMonitoring by remember { mutableStateOf(false) }
        var isAnalyzingISO by remember { mutableStateOf(false) }
        var isoResults by remember { mutableStateOf<List<ISOViolation>>(emptyList()) }
        var networkStats by remember { mutableStateOf<NetworkStats?>(null) }
        var connections by remember { mutableStateOf<List<com.guardianos.core.domain.model.NetworkConnection>>(emptyList()) }
        
        // Monitor de permisos en tiempo real (Transparencia Radical)
        val permissionMonitor = remember { RealTimePermissionMonitor(context) }
        
        // Cleanup cuando se destruye el composable
        DisposableEffect(Unit) {
            onDispose {
                permissionMonitor.stopMonitoring()
            }
        }
        
        // Paleta "Cyber Ethics" - diseño ético sin alarmismo
        // Indicadores de privacidad:
        // - Verde esmeralda (#4CAF50): Cifrado/procesamiento local verificado
        // - Amarillo ético (#FBBF24): Advertencia sin pánico
        // - Azul información (#3B82F6): Datos neutrales
        // - Rojo serio (#B3261E): Errores críticos sin alarmismo
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF5D8BF4),           // Azul ético (confianza sin infantilismo)
                primaryContainer = Color(0xFF2A3B57),  // Contenedores profundos
                secondary = Color(0xFF8AA8D1),          // Secundario sereno (no violeta "premium")
                secondaryContainer = Color(0xFF1A273A),
                tertiary = Color(0xFF64B5F6),           // Terciario para acciones educativas
                background = Color(0xFF0B111F),         // "Deep space" - más oscuro, reduce fatiga visual
                surface = Color(0xFF141E30),            // Azul-noche profundo (no gris plano)
                surfaceVariant = Color(0xFF1C2A42),
                error = Color(0xFFB3261E),              // Rojo serio (no alarmista)
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color(0xFFE2E8F0),
                onSurface = Color(0xFFCBD5E1),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "GuardianOS ${if (isPro.value) "PRO" else "FREE"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                // Indicador de privacidad 100% local
                                Surface(
                                    color = Color(0xFF4CAF50),  // Verde esmeralda (procesamiento local verificado)
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "100% LOCAL",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                if (isMonitoring || currentScreen == "network") {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0xFF10B981),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🛡️",
                                                fontSize = 10.sp
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Text(
                                                text = "ACTIVO",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.primary
                        ),
                        navigationIcon = {
                            if (currentScreen != "home") {
                                IconButton(onClick = { currentScreen = "home" }) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Inicio",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { currentScreen = "transparency" }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Info,
                                    contentDescription = "Transparencia",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { currentScreen = "about" }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Acerca de",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (currentScreen) {
                        "home" -> HomeScreen(
                            isPro = isPro.value,
                            onStartScan = {
                                currentScreen = "scan"
                                scope.launch {
                                    isScanning = true
                                    try {
                                        // FREE: Escaneo básico (malware conocido + permisos básicos)
                                        // PRO: Escaneo completo (malware + stalkerware + permisos avanzados + heurística)
                                        val useQuickScan = !isPro.value
                                        scanResults = performMalwareScan(context, isQuickScan = useQuickScan)
                                        
                                        // PRO: Guardar en historial automáticamente
                                        if (isPro.value && BuildConfig.PRO_VERSION) {
                                            // Convertir AppScanResult a AppAudit para historial
                                            val apps = appAuditor.auditApps(context, AuditMode.FULL)
                                            ScanHistory.saveScan(context, apps)
                                                .onFailure { e ->
                                                    Log.w(TAG, "No se pudo guardar en historial: ${e.message}")
                                                }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error during scan", e)
                                        Toast.makeText(
                                            context,
                                            "Error durante el escaneo: ${e.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        isScanning = false
                                    }
                                }
                            },
                            onActivatePro = { currentScreen = "pro_payment" },
                            onISOAudit = {
                                if (isPro.value) {
                                    currentScreen = "iso"
                                    scope.launch {
                                        isAnalyzingISO = true
                                        try {
                                            isoResults = performISOAudit(context)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error during ISO audit", e)
                                        } finally {
                                            isAnalyzingISO = false
                                        }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Función PRO. Activa la versión PRO para acceder.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onNetworkMonitor = {
                                if (isPro.value) {
                                    currentScreen = "network"
                                    scope.launch {
                                        isMonitoring = true
                                        try {
                                            val guardian = NetworkGuardian(context)
                                            val allConnections = guardian.scanActiveConnections()
                                            networkStats = NetworkStats(
                                                totalConnections = allConnections.size,
                                                suspiciousConnections = allConnections.count { it.isSuspicious }
                                            )
                                            connections = allConnections.map { conn ->
                                                com.guardianos.core.domain.model.NetworkConnection(
                                                    appName = conn.appName,
                                                    packageName = conn.packageName,
                                                    remoteAddress = conn.remoteAddress,
                                                    remotePort = conn.remotePort,
                                                    isSuspicious = conn.isSuspicious,
                                                    suspiciousReason = conn.suspiciousReason
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error monitoring network", e)
                                        } finally {
                                            isMonitoring = false
                                        }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Función PRO. Activa la versión PRO para acceder.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onMediaAccess = {
                                if (isPro.value) {
                                    currentScreen = "media"
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Función PRO. Activa la versión PRO para acceder.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onForensicReport = {
                                if (isPro.value) {
                                    currentScreen = "forensic"
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Función PRO. Activa la versión PRO para acceder.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onPrivacyProactive = {
                                if (isPro.value) {
                                    currentScreen = "privacy"
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Función PRO. Activa la versión PRO para acceder.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onConsulting = {
                                if (isPro.value) {
                                    currentScreen = "consulting"
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Función PRO. Activa la versión PRO para acceder.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onVault = {
                                if (isPro.value) {
                                    currentScreen = "vault"
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Función PRO. Activa la versión PRO para acceder.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onHistory = {
                                if (isPro.value) {
                                    currentScreen = "history"
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Función PRO. Activa la versión PRO para acceder.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        
                        "scan" -> ScanResultsScreen(
                            results = scanResults,
                            isScanning = isScanning,
                            isPro = isPro.value,
                            onBack = { currentScreen = "home" },
                            onUpgradePro = { currentScreen = "activation" },
                            onExportPDF = {
                                scope.launch {
                                    try {
                                        // FREE: PDF básico sin marca forense
                                        // PRO: PDF profesional con opción de modo forense
                                        val useForensicMode = isPro.value && BuildConfig.PRO_VERSION
                                        exportScanToPDF(context, scanResults, forensicMode = useForensicMode)
                                        
                                        val message = if (isPro.value) {
                                            "✅ PDF profesional generado (con marca forense)"
                                        } else {
                                            "✅ PDF básico generado (actualiza a PRO para informes forenses)"
                                        }
                                        
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error exporting PDF", e)
                                        Toast.makeText(
                                            context,
                                            "Error al exportar PDF: ${e.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                        
                        "pro_payment" -> PROPaymentScreen(
                            context = context,
                            onActivationSuccess = {
                                isPro.value = true
                                currentScreen = "home"
                                Toast.makeText(
                                    context,
                                    "✅ ¡GuardianOS PRO activado! Bienvenido a la protección ética.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onBack = { currentScreen = "home" }
                        )
                        
                        "activation" -> ActivationScreen(
                            onActivated = {
                                isPro.value = true
                                currentScreen = "home"
                            },
                            onBack = { currentScreen = "home" }
                        )
                        
                        "iso" -> ISOAuditScreen(
                            results = isoResults,
                            isAnalyzing = isAnalyzingISO,
                            onBack = { currentScreen = "home" },
                            onExportPDF = {
                                scope.launch {
                                    try {
                                        exportISOAuditPDF(context, isoResults)
                                        Toast.makeText(
                                            context,
                                            "📄 Informe ISO 27001 exportado correctamente",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Error al exportar PDF: ${e.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        Log.e(TAG, "Error exporting ISO PDF", e)
                                    }
                                }
                            }
                        )
                        
                        "network" -> NetworkAnalyzerScreen(onBack = { currentScreen = "home" })
                        "media" -> MediaAccessScreen(context = context, onBack = { currentScreen = "home" })
                        "forensic" -> ForensicReportScreen(results = scanResults, onBack = { currentScreen = "home" })
                        "privacy" -> PrivacyProactiveScreen(context = context, onBack = { currentScreen = "home" })
                        "consulting" -> ConsultingScreen(context = context, pdfPath = pdfPath, onBack = { currentScreen = "home" })
                        "transparency" -> PermissionTransparencyDashboard(
                            context = context,
                            monitor = permissionMonitor,
                            onBack = { currentScreen = "home" }
                        )
                        "cta_iso" -> ProFeatureCTA(onUpgrade = { currentScreen = "pro_payment" })
                        "cta_network" -> ProFeatureCTA(onUpgrade = { currentScreen = "pro_payment" })
                        "cta_media" -> ProFeatureCTA(onUpgrade = { currentScreen = "pro_payment" })
                        "cta_forensic" -> ProFeatureCTA(onUpgrade = { currentScreen = "pro_payment" })
                        "cta_privacy" -> ProFeatureCTA(onUpgrade = { currentScreen = "pro_payment" })
                        "cta_consulting" -> ProFeatureCTA(onUpgrade = { currentScreen = "pro_payment" })
                        
                        // Family Vault - Flujo de seguridad
                        "vault" -> {
                            if (isPro.value) {
                                // Verificar si ya existe master password
                                val hasPassword = VaultSecurityManager.isMasterPasswordSet(context)
                                if (hasPassword) {
                                    // Mostrar pantalla de desbloqueo
                                    VaultUnlockScreen(
                                        context = context,
                                        onUnlocked = { currentScreen = "vault_main" },
                                        onCancel = { currentScreen = "home" }
                                    )
                                } else {
                                    // Primera vez: configurar master password
                                    MasterPasswordSetupScreen(
                                        context = context,
                                        onPasswordSet = { currentScreen = "vault_main" },
                                        onCancel = { currentScreen = "home" }
                                    )
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Función PRO. La Bóveda Familiar requiere GuardianOS PRO.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                currentScreen = "home"
                            }
                        }
                        
                        "vault_main" -> FamilyVaultMainScreen(
                            context = context,
                            onBack = { currentScreen = "home" }
                        )
                        
                        // Scan History - Comparación temporal
                        "history" -> {
                            if (isPro.value) {
                                ScanHistoryScreen(
                                    context = context,
                                    onBack = { currentScreen = "home" }
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    "Función PRO. El Historial de Escaneos requiere GuardianOS PRO.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                currentScreen = "home"
                            }
                        }
                        
                        // Diagnóstico Técnico - Transparency tool
                        "diagnostics" -> DiagnosticsScreen(
                            context = context,
                            onBack = { currentScreen = "home" }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun HomeScreen(
        isPro: Boolean,
        onStartScan: () -> Unit,
        onActivatePro: () -> Unit,
        onISOAudit: () -> Unit,
        onNetworkMonitor: () -> Unit,
        onMediaAccess: () -> Unit,
        onForensicReport: () -> Unit,
        onPrivacyProactive: () -> Unit,
        onConsulting: () -> Unit,
        onVault: () -> Unit = {},
        onHistory: () -> Unit = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ✅ Banner educativo de privacidad (diferenciador ético)
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
                        imageVector = Icons.Default.Info,
                        contentDescription = "Privacidad",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "🔒 Todo el análisis ocurre 100% LOCALMENTE en tu dispositivo. " +
                               "Nunca enviamos tus datos a servidores.",
                        fontSize = 13.sp,
                        color = Color(0xFF0F766E) // Verde oscuro para texto
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Estado PRO/FREE con indicador visual claro
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPro)
                        Color(0xFF5D8BF4).copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isPro) "⭐ GuardianOS PRO" else "🆓 GuardianOS FREE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPro) Color(0xFF5D8BF4) else Color.Gray
                        )
                        Text(
                            text = if (isPro)
                                "Protección avanzada activada"
                            else
                                "Funcionalidades básicas disponibles",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    if (!isPro) {
                        Button(
                            onClick = onActivatePro,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5D8BF4)
                            )
                        ) {
                            Text("Activar PRO • 9,99€")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Funciones esenciales (siempre disponibles)
            Text(
                text = "🛡️ Protección Esencial",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            FeatureCard(
                icon = "🔍",
                title = "Escanear Apps",
                description = "Detecta malware y permisos invasivos",
                onClick = onStartScan,
                isPro = false,
                color = Color(0xFF5D8BF4)
            )

            Spacer(Modifier.height(24.dp))

            // Funciones PRO (con upgrade educativo)
            Text(
                text = "✨ Protección Avanzada ${if (isPro) "" else "(PRO)"}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            if (isPro) {
                FeatureCard(
                    icon = "👁️",
                    title = "Stalkerware Detection",
                    description = "Detecta apps que espían sin consentimiento",
                    onClick = onStartScan,
                    isPro = true,
                    color = Color(0xFF8B5CF6)
                )
                FeatureCard(
                    icon = "🛡️",
                    title = "Guardian Shield",
                    description = "Alertas en tiempo real de accesos a cámara/micrófono",
                    onClick = onActivatePro, // TODO: Enlazar a pantalla Shield
                    isPro = true,
                    color = Color(0xFF8B5CF6)
                )
                FeatureCard(
                    icon = "📊",
                    title = "Auditoría ISO 27001",
                    description = "Evaluación según estándar internacional",
                    onClick = onISOAudit,
                    isPro = true,
                    color = Color(0xFF8B5CF6)
                )
                FeatureCard(
                    icon = "🌐",
                    title = "Análisis de Red",
                    description = "Monitoriza conexiones en tiempo real",
                    onClick = onNetworkMonitor,
                    isPro = true,
                    color = Color(0xFF8B5CF6)
                )
                FeatureCard(
                    icon = "📸",
                    title = "Apps con Acceso a Multimedia",
                    description = "Detecta permisos otorgados a fotos/vídeos",
                    onClick = onMediaAccess,
                    isPro = true,
                    color = Color(0xFF8B5CF6)
                )
                FeatureCard(
                    icon = "⚖️",
                    title = "Informe Forense Legal",
                    description = "Con validez para procedimientos legales",
                    onClick = onForensicReport,
                    isPro = true,
                    color = Color(0xFF8B5CF6)
                )
                FeatureCard(
                    icon = "🛡️",
                    title = "Privacidad Proactiva",
                    description = "Modo sigilo y modo pánico",
                    onClick = onPrivacyProactive,
                    isPro = true,
                    color = Color(0xFF8B5CF6)
                )
                FeatureCard(
                    icon = "👥",
                    title = "Consultoría Personalizada",
                    description = "Ayuda experta para interpretar resultados",
                    onClick = onConsulting,
                    isPro = true,
                    color = Color(0xFF8B5CF6)
                )
                FeatureCard(
                    icon = "📁",
                    title = "Bóveda de Documentos",
                    description = "Almacena DNI, pasaportes y documentos con cifrado AES-256",
                    onClick = onVault,
                    isPro = true,
                    color = Color(0xFF10B981)
                )
                FeatureCard(
                    icon = "📅",
                    title = "Historial de Escaneos",
                    description = "Compara escaneos para detectar amenazas nuevas",
                    onClick = onHistory,
                    isPro = true,
                    color = Color(0xFF3B82F6)
                )
            } else {
                // Modo educativo FREE → PRO (sin bloqueo agresivo)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onActivatePro() },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF5D8BF4).copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF5D8BF4).copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⭐ ¿Quieres protección avanzada?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D8BF4)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Con GuardianOS PRO (9,99€ pago único) obtienes:",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "• Detección de stalkerware (apps que espían)",
                            "• Guardian Shield: monitorización tiempo real de cámara/micrófono",
                            "• Bóveda cifrada AES-256 para documentos familiares",
                            "• Informes forenses válidos legalmente",
                            "• Sin trackers, sin nube, sin analytics"
                        ).forEach { feature ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✓", color = Color(0xFF10B981), modifier = Modifier.padding(end = 6.dp))
                                Text(feature, fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onActivatePro,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5D8BF4)
                            )
                        ) {
                            Text("Activar PRO • 9,99€ • Pago único", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Origen andaluz destacado (valor diferencial)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🇪🇸 Desarrollado en Andalucía",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D8BF4)
                        )
                        Text(
                            text = "Protección digital ética desde Sevilla, España",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "guardianos.es",
                        fontSize = 13.sp,
                        color = Color(0xFF5D8BF4),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @Composable
    fun FeatureCard(
        icon: String,
        title: String,
        description: String,
        onClick: () -> Unit,
        isPro: Boolean = false,
        color: Color = MaterialTheme.colorScheme.primary
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = if (isPro)
                    color.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    fontSize = 32.sp,
                    color = color,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isPro) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = color,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "PRO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Ir",
                    tint = color
                )
            }
        }
    }
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    @Composable
    fun GuardianShieldCard(
        isActive: Boolean,
        onToggle: (Boolean) -> Unit,
        onClick: () -> Unit = {}
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive)
                    Color(0xFF10B981).copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
            ),
            border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF10B981)) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛡️",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Guardian Shield en Tiempo Real",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "PRO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isActive) 
                            "🟢 Monitorizando accesos a permisos cada 30s"
                        else
                            "Toca para ver detalles y configurar",
                        fontSize = 12.sp,
                        color = if (isActive) Color(0xFF10B981) else Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                androidx.compose.material3.Switch(
                    checked = isActive,
                    onCheckedChange = { newState ->
                        // Evitar que el click del switch propague al card
                        onToggle(newState)
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF10B981),
                        checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.5f)
                    )
                )
            }
        }
    }

    @Composable
    fun ScanResultsScreen(
        results: List<AppScanResult>,
        isScanning: Boolean,
        isPro: Boolean,
        onBack: () -> Unit,
        onUpgradePro: () -> Unit,
        onExportPDF: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = if (isPro) "Auditoría Completa PRO" else "Escaneo Básico",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // Descripción del tipo de escaneo
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPro) 
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    else 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPro) "🔒" else "🆓",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = if (isPro) {
                            "Malware + Stalkerware + Permisos avanzados + Heurística"
                        } else {
                            "Malware conocido + Permisos básicos de privacidad"
                        },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            
            if (isScanning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Escaneando aplicaciones...",
                            color = Color.Gray
                        )
                    }
                }
            } else {
                // Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${results.size}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Apps Analizadas",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val threats = results.count { it.isMalware || it.isStalkerware }
                            Text(
                                text = "$threats",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (threats > 0)
                                    MaterialTheme.colorScheme.error
                                else
                                    Color(0xFF10B981)
                            )
                            Text(
                                text = "Amenazas",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val appsWithPerms = results.count { it.suspiciousPermissions.isNotEmpty() }
                            Text(
                                text = "$appsWithPerms",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                text = "Apps Invasivas",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                // Banner upgrade FREE → PRO
                if (!isPro) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpgradePro() },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF7C3AED).copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "⭐ ¿Quieres el informe completo?",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA78BFA)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Con PRO (9,99€) obtienes la auditoría completa con todos los permisos detallados, análisis de stalkerware, y servicio de consultoría personalizada para resolver los problemas detectados.",
                                fontSize = 12.sp,
                                color = Color(0xFFC4B5FD)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Pulsa aquí para activar PRO →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA78BFA)
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Export Button
                Button(
                    onClick = onExportPDF,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPro) "📧 Enviar auditoría completa por email" else "📧 Enviar resumen por email")
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Results List
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(results.size) { index ->
                        val result = results[index]
                        AppResultCard(result, isPro)
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B7280)
                    )
                ) {
                    Text("Volver")
                }
            }
        }
    }

    @Composable
    fun AppResultCard(result: AppScanResult, isPro: Boolean = false) {
        val context = LocalContext.current
        val backgroundColor = when {
            result.isMalware || result.isStalkerware -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            result.suspiciousPermissions.size >= 5 -> Color(0xFFFBBF24).copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.surface
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", result.packageName, null)
                    }
                    context.startActivity(intent)
                },
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = result.packageName,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    if (result.isMalware || result.isStalkerware) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "⚠️ AMENAZA",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                if (result.isMalware) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "🦠 Malware detectado: ${result.malwareType}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (result.isStalkerware) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "👁️ Stalkerware: ${result.stalkerwareIndicators.joinToString(", ")}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (result.suspiciousPermissions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "🔐 Permisos de privacidad (${result.suspiciousPermissions.size}):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                    Spacer(Modifier.height(4.dp))
                    
                    if (isPro) {
                        // PRO: Mostrar TODOS los permisos detallados
                        result.suspiciousPermissions.forEach { perm ->
                            val (icon, name) = getPermissionIconAndName(perm)
                            Text(
                                text = "  $icon $name",
                                fontSize = 11.sp,
                                color = Color(0xFFD4D4D8)
                            )
                        }
                    } else {
                        // FREE: Solo primeros 3 permisos + mensaje de upgrade
                        result.suspiciousPermissions.take(3).forEach { perm ->
                            val (icon, name) = getPermissionIconAndName(perm)
                            Text(
                                text = "  $icon $name",
                                fontSize = 11.sp,
                                color = Color(0xFFD4D4D8)
                            )
                        }
                        if (result.suspiciousPermissions.size > 3) {
                            Text(
                                text = "  🔒 +${result.suspiciousPermissions.size - 3} permisos más (PRO)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5CF6)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ActivationScreen(
        onActivated: () -> Unit,
        onBack: () -> Unit
    ) {
        val context = LocalContext.current
        var activationCode by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Activación PRO",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⭐",
                        fontSize = 48.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Desbloquea funciones premium",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "• Análisis de privacidad avanzado\n" +
                               "• Bóveda de documentos familiares\n" +
                               "• Reportes detallados en PDF\n" +
                               "• Sin anuncios",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            OutlinedTextField(
                value = activationCode,
                onValueChange = {
                    activationCode = it.uppercase()
                    error = ""
                },
                label = { Text("Código de activación") },
                placeholder = { Text("GUAR-XXXX-XXXX-XXXX") },
                modifier = Modifier.fillMaxWidth(),
                isError = error.isNotEmpty(),
                supportingText = if (error.isNotEmpty()) {
                    { Text(error, color = MaterialTheme.colorScheme.error) }
                } else null
            )
            
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (validateActivationCode(activationCode)) {
                        // Guardar estado de activación
                        saveActivationState(context, true, activationCode)
                        
                        // Verificar que se guardó correctamente
                        val verified = isProActivated(context)
                        Log.d(TAG, "Activación PRO guardada: $verified con código: $activationCode")
                        
                        if (verified) {
                            Toast.makeText(
                                context,
                                "✅ ¡Versión PRO activada con éxito!",
                                Toast.LENGTH_LONG
                            ).show()
                            onActivated()
                        } else {
                            Log.e(TAG, "Error: Activación no persistió correctamente")
                            error = "Error al guardar activación. Inténtalo de nuevo."
                        }
                    } else {
                        error = "Código de activación inválido"
                        Log.w(TAG, "Código de activación rechazado: $activationCode")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = activationCode.isNotBlank()
            ) {
                Text("Activar PRO")
            }
            
            Spacer(Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "ℹ️ Formato del código",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "El código debe tener el formato: GUAR-1234-5678-6912",
                        fontSize = 11.sp,
                        color = Color.Gray
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
                Text("Volver")
            }
        }
    }

    @Composable
    fun ISOAuditScreen(
        results: List<ISOViolation>,
        isAnalyzing: Boolean,
        onBack: () -> Unit,
        onExportPDF: () -> Unit = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Auditoría ISO 27001",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (isAnalyzing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Analizando cumplimiento...",
                            color = Color.Gray
                        )
                    }
                }
            } else {
                // Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val score = if (results.isEmpty()) 100 else
                            maxOf(0, 100 - (results.size * 10))
                        
                        Text(
                            text = "$score%",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                score >= 80 -> Color(0xFF10B981)
                                score >= 50 -> Color(0xFFFBBF24)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = "Nivel de cumplimiento",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                if (results.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "✅", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Sin violaciones detectadas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tu dispositivo cumple con los estándares de seguridad ISO 27001",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Violaciones detectadas (${results.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(results.size) { index ->
                            ISOViolationCard(results[index])
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Botón exportar PDF (disponible siempre)
                Button(
                    onClick = onExportPDF,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("📄 Exportar Informe PDF")
                }
                
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B7280)
                    )
                ) {
                    Text("Volver")
                }
            }
        }
    }

    @Composable
    fun ISOViolationCard(violation: ISOViolation) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (violation.severity) {
                    "critical" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    "high" -> Color(0xFFFBBF24).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = violation.control,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Surface(
                        color = when (violation.severity) {
                            "critical" -> MaterialTheme.colorScheme.error
                            "high" -> Color(0xFFFBBF24)
                            else -> Color.Gray
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = violation.severity.uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = violation.description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                if (violation.recommendation.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "💡 ${violation.recommendation}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    @Composable
    fun PrivacyAnalysisScreen(
        report: PrivacyReport?,
        isAnalyzing: Boolean,
        onBack: () -> Unit,
        onExportPDF: () -> Unit = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Análisis de Privacidad",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (isAnalyzing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Analizando privacidad...",
                            color = Color.Gray
                        )
                    }
                }
            } else if (report != null) {
                // Privacy Score
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${report.privacyScore}%",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                report.privacyScore >= 80 -> Color(0xFF10B981)
                                report.privacyScore >= 50 -> Color(0xFFFBBF24)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = "Puntuación de privacidad",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Statistics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${report.riskyApps.size}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Apps de riesgo",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${report.excessivePermissions.size}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                text = "Permisos excesivos",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Risky Apps List
                if (report.riskyApps.isNotEmpty()) {
                    Text(
                        text = "Apps con riesgo de privacidad",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(report.riskyApps.size) { index ->
                            RiskyAppCard(report.riskyApps[index])
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Botón exportar PDF (PRO)
                Button(
                    onClick = onExportPDF,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("📄 Exportar Informe PDF")
                }
                
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B7280)
                    )
                ) {
                    Text("Volver")
                }
            }
        }
    }

    @Composable
    fun RiskyAppCard(app: RiskyApp) {
        val context = LocalContext.current
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                    }
                    context.startActivity(intent)
                },
            colors = CardDefaults.cardColors(
                containerColor = when (app.riskLevel) {
                    "high" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    "medium" -> Color(0xFFFBBF24).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = app.packageName,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    Surface(
                        color = when (app.riskLevel) {
                            "high" -> MaterialTheme.colorScheme.error
                            "medium" -> Color(0xFFFBBF24)
                            else -> Color.Gray
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = app.riskLevel.uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                if (app.concerns.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    app.concerns.forEach { concern ->
                        Text(
                            text = "⚠️ $concern",
                            fontSize = 12.sp,
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun NetworkMonitorScreen(
        stats: NetworkStats?,
        connections: List<NetworkConnection>,
        isMonitoring: Boolean,
        onBack: () -> Unit,
        onExportPDF: () -> Unit = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Monitor de Red",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (isMonitoring) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Monitoreando red...",
                            color = Color.Gray
                        )
                    }
                }
            } else if (stats != null) {
                // Network Statistics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${stats.totalConnections}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "Conexiones",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${stats.suspiciousConnections}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.suspiciousConnections > 0)
                                    MaterialTheme.colorScheme.error
                                else
                                    Color.Gray
                            )
                            Text(
                                text = "Sospechosas",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Alert if suspicious connections
                if (stats.suspiciousConnections > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "⚠️", fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Conexiones sospechosas detectadas",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Revisa las conexiones marcadas abajo",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                }
                
                // Empty state
                if (connections.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "📡", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Sin conexiones activas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "No se detectaron conexiones de red en este momento.",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Connections list
                    Text(
                        text = "📡 Conexiones activas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(connections.size) { index ->
                            NetworkConnectionCard(connections[index])
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Botón exportar PDF
                Button(
                    onClick = onExportPDF,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("📄 Exportar Informe PDF")
                }
                
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B7280)
                    )
                ) {
                    Text("Volver")
                }
            }
        }
    }

    @Composable
    fun NetworkConnectionCard(connection: NetworkConnection) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (connection.isSuspicious)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = connection.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (connection.isSuspicious) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "⚠️ SOSPECHOSA",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = "🌍 ${connection.remoteAddress}:${connection.remotePort}",
                    fontSize = 13.sp,
                    color = if (connection.isSuspicious)
                        MaterialTheme.colorScheme.error
                    else
                        Color(0xFF10B981)
                )
                
                if (connection.suspiciousReason != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "⚠️ ${connection.suspiciousReason}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = connection.packageName,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }

    @Composable
    fun AboutScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Logo y título con origen andaluz
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🛡️", fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "GuardianOS",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v$APP_VERSION",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Protección Digital Ética para Menores",
                        fontSize = 16.sp,
                        color = Color(0xFF5D8BF4),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🇪🇸", fontSize = 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Desarrollado en Andalucía, España",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D8BF4)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ✅ SECCIÓN DESTACADA: Compromiso de Privacidad (diferenciador clave)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                ),
                border = BorderStroke(2.dp, Color(0xFF10B981))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Privacidad",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "🔒 Nuestro Compromiso de Privacidad Radical",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrivacyPromiseItem("🚫", "CERO trackers, analytics o telemetry")
                        PrivacyPromiseItem("📱", "100% de los análisis se ejecutan LOCALMENTE en tu dispositivo")
                        PrivacyPromiseItem("☁️", "NUNCA guardamos tus datos en la nube")
                        PrivacyPromiseItem("🔐", "Cifrado AES-256-GCM 100% local (sin backdoors)")
                        PrivacyPromiseItem("👁️", "NUNCA monitorizamos tu uso de la app")
                        PrivacyPromiseItem("📜", "Código abierto bajo licencia GPL v3.0 para auditoría pública")
                        PrivacyPromiseItem("🇪🇸", "Desarrollado íntegramente en Andalucía,España")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Modelo de negocio transparente
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "💶 Modelo de Negocio Transparente",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "• FREE: Funcionalidades básicas sin limitaciones artificiales\n" +
                               "• PRO: 9,99€ pago único (sin suscripciones, sin renovaciones automáticas)\n" +
                               "• NO usamos Google Play Billing (evitamos trackers de Google)\n" +
                               "• El pago se realiza directamente en https://guardianos.es/pro\n" +
                               "• Sin publicidad, sin venta de datos, sin monetización oculta",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Contacto con información real
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "📬 Contacto Directo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    ContactItem(
                        icon = "📧",
                        label = "Email",
                        value = DEVELOPER_EMAIL,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$DEVELOPER_EMAIL")
                                putExtra(Intent.EXTRA_SUBJECT, "Consulta GuardianOS PRO")
                            }
                            context.startActivity(intent)
                        }
                    )
                    
                    ContactItem(
                        icon = "🌐",
                        label = "Web",
                        value = WEB_URL,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL))
                            context.startActivity(intent)
                        }
                    )
                    
                    ContactItem(
                        icon = "🇪🇸",
                        label = "Ubicación",
                        value = "Andalucía, España",
                        onClick = null
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Licencia y código abierto
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "📜 Código Abierto y Licencia",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "GuardianOS es software libre bajo licencia GNU GPL v3.0.\n" +
                               "Puedes auditar, modificar y redistribuir el código libremente.\n" +
                               "Repositorio: https://github.com/systemavworks/guardianos-android",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Copyright © 2026 $DEVELOPER_NAME\n" +
                               "Desarrollado en Andalucía, España",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            
            // Botón diagnóstico técnico (transparencia)
            OutlinedButton(
                onClick = { currentScreen = "diagnostics" },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF5D8BF4)
                ),
                border = BorderStroke(1.dp, Color(0xFF5D8BF4))
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Diagnóstico"
                )
                Spacer(Modifier.width(8.dp))
                Text("🔧 Diagnóstico Técnico")
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Botón volver con icono
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5D8BF4)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Volver"
                )
                Spacer(Modifier.width(8.dp))
                Text("Volver al Inicio")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun PrivacyPromiseItem(icon: String, text: String) {
        Row(verticalAlignment = Alignment.Top) {
            Text(icon, fontSize = 20.sp, color = Color(0xFF10B981), modifier = Modifier.padding(end = 12.dp))
            Text(text, fontSize = 14.sp, color = Color(0xFF0F766E), fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    private fun ContactItem(icon: String, label: String, value: String, onClick: (() -> Unit)?) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable(enabled = onClick != null) { onClick?.invoke() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp, color = Color(0xFF5D8BF4), modifier = Modifier.padding(end = 16.dp))
            Column {
                Text(label, fontSize = 13.sp, color = Color.Gray)
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (onClick != null) Color(0xFF5D8BF4) else Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Abrir",
                    tint = Color(0xFF5D8BF4),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    
    @Composable
    fun GuardianShieldScreen(
        context: Context,
        isActive: Boolean,
       onToggle: (Boolean) -> Unit,
        onBack: () -> Unit
    ) {
        val monitor = remember { GuardianShieldMonitor(context) }
        var hasPermission by remember { mutableStateOf(monitor.hasUsageStatsPermission()) }
        var recentAccesses by remember { mutableStateOf<List<PermissionAccessInfo>>(emptyList()) }
        var isLoadingHistory by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        
        // Cargar historial al entrar
        LaunchedEffect(Unit) {
            if (hasPermission) {
                isLoadingHistory = true
                try {
                    recentAccesses = monitor.getRecentPermissionAccess(hoursBack = 24)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading permission history", e)
                } finally {
                    isLoadingHistory = false
                }
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "🛡️ Guardian Shield",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Card de estado
            Card(
                modifier = Modifier.fillMaxWidth(),
               colors = CardDefaults.cardColors(
                    containerColor = if (isActive) 
                        Color(0xFF10B981).copy(alpha = 0.15f)
                    else 
                        MaterialTheme.colorScheme.surface
                ),
                border = if (isActive) 
                    androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF10B981)) 
                else null
            ) {
                Column( modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isActive) "🟢 Activo" else "⚫ Inactivo",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) Color(0xFF10B981) else Color.Gray
                            )
                            Text(
                                text = if (isActive) 
                                    "Monitorizando cada 30s" 
                                else 
                                    "Toca el switch para activar",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = isActive,
                            onCheckedChange = onToggle,
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Información sobre cómo funciona
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ℹ️ ¿Cómo funciona?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Guardian Shield verifica con AppOpsManager qué permisos sensibles (cámara, micrófono, ubicación, contactos, SMS) " +
                                "tienen REALMENTE CONCEDIDOS las apps de terceros.\n\n" +
                                "Solo alerta sobre apps no-sistema con permisos verificados como activos. " +
                                "No genera falsos positivos por permisos solo declarados pero no concedidos.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Permisos necesarios
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasPermission)
                        Color(0xFF10B981).copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (hasPermission) "✅" else "⚠️",
                            fontSize = 24.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                               text = "Permiso de Estadísticas de Uso",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasPermission) 
                                    "Concedido correctamente"
                                else 
                                    "Requerido para monitorizar accesos",
                                fontSize = 12.sp,
                                color = if (hasPermission) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    if (!hasPermission) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                    
                                    // Actualizar estado después de abrir ajustes
                                    scope.launch {
                                        kotlinx.coroutines.delay(500)
                                        hasPermission = monitor.hasUsageStatsPermission()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error al abrir ajustes: ${e.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("🔧 Abrir Ajustes de Permisos")
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "1. Busca 'GuardianOS' en la lista\n2. Activa 'Permitir acceso al uso'",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Historial de accesos
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 Accesos Recientes (24h)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (hasPermission) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isLoadingHistory = true
                                        try {
                                            hasPermission = monitor.hasUsageStatsPermission()
                                            if (hasPermission) {
                                                recentAccesses = monitor.getRecentPermissionAccess(hoursBack = 24)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error refreshing", e)
                                        } finally {
                                            isLoadingHistory = false
                                        }
                                    }
                                }
                            ) {
                                Text("🔄 Actualizar")
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    when {
                        !hasPermission -> {
                            Text(
                                text = "❌ Concede el permiso para ver el historial",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            )
                        }
                        isLoadingHistory -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        recentAccesses.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("✨", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Sin accesos detectados",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Las apps que accedan a permisos sensibles aparecerán aquí",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            recentAccesses.take(10).forEach { access ->
                                PermissionAccessCard(access)
                                Spacer(Modifier.height(8.dp))
                            }
                            
                            if (recentAccesses.size > 10) {
                                Text(
                                    text = "+ ${recentAccesses.size - 10} accesos más",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Botón volver
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B7280)
                )
            ) {
                Text("Volver")
            }
        }
    }
    
    @Composable
    fun PermissionAccessCard(access: PermissionAccessInfo) {
        val icon = when {
            access.permissionGroup.contains("CAMERA") -> "📷"
            access.permissionGroup.contains("MICROPHONE") -> "🎤"
            access.permissionGroup.contains("LOCATION") -> "📍"
            access.permissionGroup.contains("CONTACTS") -> "👥"
            access.permissionGroup.contains("SMS") -> "💬"
            access.permissionGroup.contains("CALL") -> "📞"
            else -> "🔒"
        }
        
        val permissionName = when {
            access.permissionGroup.contains("CAMERA") -> "Cámara"
            access.permissionGroup.contains("MICROPHONE") -> "Micrófono"
            access.permissionGroup.contains("LOCATION") -> "Ubicación"
            access.permissionGroup.contains("CONTACTS") -> "Contactos"
            access.permissionGroup.contains("SMS") -> "SMS"
            access.permissionGroup.contains("CALL") -> "Llamadas"
            else -> "Permiso sensible"
        }
        
        val timeAgo = getTimeAgo(access.lastAccessTime)
        val statusText = if (access.isRealAccess) "Permiso concedido" else "Permiso declarado"
        val statusColor = if (access.isRealAccess) Color(0xFFEF4444) else Color.Gray
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (access.isRealAccess)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = access.appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$permissionName • $timeAgo",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "✓ $statusText",
                        fontSize = 11.sp,
                        color = statusColor
                    )
                }
            }
        }
    }
    
    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60000 -> "Hace menos de 1 min"
            diff < 3600000 -> "Hace ${diff / 60000} min"
            diff < 86400000 -> "Hace ${diff / 3600000} h"
            else -> "Hace ${diff / 86400000} días"
        }
    }

    /* ────────────────────────── LOGIC FUNCTIONS ────────────────────────── */

    private suspend fun performMalwareScan(context: Context, isQuickScan: Boolean = true): List<AppScanResult> {
        return withContext(Dispatchers.IO) {
            try {
                // Verificar que tenemos permisos necesarios
                val packageManager = context.packageManager
                
                val installedApps = try {
                    packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Sin permiso QUERY_ALL_PACKAGES, usando lista limitada", e)
                    packageManager.getInstalledPackages(0) // Fallback sin permisos
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo apps instaladas", e)
                    emptyList()
                }
                
                if (installedApps.isEmpty()) {
                    Log.w(TAG, "No se pudieron obtener apps instaladas")
                    return@withContext emptyList()
                }
                
                val results = mutableListOf<AppScanResult>()
                
                // MODO QUICK (FREE): Solo detección de malware conocido + permisos básicos de privacidad
                // MODO FULL (PRO): Malware + Stalkerware + Permisos avanzados + Heurística completa
                
                Log.d(TAG, "Escaneo iniciado: ${if (isQuickScan) "QUICK (FREE)" else "FULL (PRO)"} - Apps: ${installedApps.size}")
                
                val stalkerwareDetector = StalkerwareDetector(context)
                val stalkerwareDetections = if (!isQuickScan) {
                    try {
                        Log.d(TAG, "Ejecutando análisis de stalkerware (PRO)...")
                        stalkerwareDetector.scanForStalkerware()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en análisis de stalkerware", e)
                        emptyList()
                    }
                } else {
                    emptyList() // En modo QUICK (FREE) no escanea stalkerware
                }
                
                installedApps.forEach { packageInfo ->
                    try {
                        val appName = try {
                            packageInfo.applicationInfo.loadLabel(packageManager).toString()
                        } catch (e: Exception) {
                            packageInfo.packageName // Fallback al nombre del paquete
                        }
                        val packageName = packageInfo.packageName
                        
                        // Scan for malware (siempre, en ambos modos)
                        val malwareMatch = malwareSignatures.checkPackageName(packageName)
                        val isMalware = malwareMatch != null
                        val malwareType = malwareMatch?.name ?: ""
                        
                        // Check if it's in stalkerware detections (solo en modo FULL)
                        val stalkerwareMatch = stalkerwareDetections.find { it.packageName == packageName }
                        val isStalkerware = stalkerwareMatch != null
                        val stalkerwareIndicators = stalkerwareMatch?.indicators ?: emptyList()
                        
                        // Analizar permisos de forma segura
                        val permissions = try {
                            packageInfo.requestedPermissions ?: emptyArray()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error obteniendo permisos de $packageName", e)
                            emptyArray()
                        }
                        
                        // Permisos básicos de privacidad (FREE + PRO)
                        val privacyPerms = permissions.filter { perm ->
                            perm.contains("CAMERA") ||
                            perm.contains("RECORD_AUDIO") ||
                            perm.contains("ACCESS_FINE_LOCATION") ||
                            perm.contains("ACCESS_COARSE_LOCATION") ||
                            perm.contains("READ_CONTACTS") ||
                            perm.contains("READ_PHONE_STATE") ||
                            perm.contains("READ_CALL_LOG") ||
                            perm.contains("READ_SMS") ||
                            perm.contains("SEND_SMS") ||
                            perm.contains("READ_EXTERNAL_STORAGE") ||
                            perm.contains("WRITE_EXTERNAL_STORAGE") ||
                            perm.contains("READ_MEDIA_IMAGES") ||
                            perm.contains("READ_MEDIA_VIDEO") ||
                            perm.contains("READ_MEDIA_AUDIO") ||
                            perm.contains("ACCESS_MEDIA_LOCATION") ||
                            perm.contains("BODY_SENSORS")
                        }
                        
                        // En modo PRO: añadir más permisos avanzados
                        val extraProPerms = if (!isQuickScan) {
                            permissions.filter { perm ->
                                perm.contains("CALL_PHONE") ||
                                perm.contains("WRITE_CALL_LOG") ||
                                perm.contains("READ_CALENDAR") ||
                                perm.contains("WRITE_CALENDAR") ||
                                perm.contains("BLUETOOTH_CONNECT") ||
                                perm.contains("NEARBY_WIFI_DEVICES") ||
                                perm.contains("ACTIVITY_RECOGNITION") ||
                                perm.contains("ACCESS_BACKGROUND_LOCATION") ||
                                perm.contains("SYSTEM_ALERT_WINDOW") ||
                                perm.contains("REQUEST_INSTALL_PACKAGES") ||
                                perm.contains("BIND_ACCESSIBILITY_SERVICE")
                            }
                        } else {
                            emptyList()
                        }
                        
                        val allSuspicious = (privacyPerms + extraProPerms).distinct()
                        
                        // Solo incluir apps que tengan algo relevante
                        val isRelevant = isMalware || isStalkerware || allSuspicious.isNotEmpty()
                        
                        if (isRelevant) {
                            results.add(
                                AppScanResult(
                                    appName = appName,
                                    packageName = packageName,
                                    isMalware = isMalware,
                                    malwareType = malwareType,
                                    isStalkerware = isStalkerware,
                                    stalkerwareIndicators = stalkerwareIndicators,
                                    suspiciousPermissions = allSuspicious
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error scanning app: ${packageInfo.packageName}", e)
                    }
                }
                
                results.sortedWith(
                    compareByDescending<AppScanResult> { it.isMalware || it.isStalkerware }
                        .thenByDescending { it.suspiciousPermissions.size }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error performing malware scan", e)
                emptyList()
            }
        }
    }

    private suspend fun performISOAudit(context: Context): List<ISOViolation> {
        return withContext(Dispatchers.IO) {
            try {
                // Primero obtener auditoría de apps
                val apps = appAuditor.auditApps(context, AuditMode.FULL)
                
                // Usar ISOAuditor (es un object, no necesita constructor)
                val report = ISOAuditor.auditISO27001(context, apps)
                
                // Convertir a lista de violaciones simples
                report.controls.flatMap { control ->
                    if (!control.compliant) {
                        listOf(ISOViolation(
                            control = control.id,
                            description = control.name,
                            severity = when (control.severity) {
                                ControlSeverity.CRITICAL -> "critical"
                                ControlSeverity.HIGH -> "high"
                                ControlSeverity.MEDIUM -> "medium"
                                ControlSeverity.LOW -> "low"
                            },
                            recommendation = control.findings.firstOrNull() ?: "Revisar configuración"
                        ))
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing ISO audit", e)
                emptyList()
            }
        }
    }

    private suspend fun exportScanToPDF(context: Context, results: List<AppScanResult>, forensicMode: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                // Validar que tenemos resultados
                if (results.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "No hay datos para exportar",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@withContext
                }
                
                // Verificar espacio disponible
                val externalDir = context.getExternalFilesDir(null)
                if (externalDir == null || !externalDir.canWrite()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "No se puede escribir en almacenamiento externo",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@withContext
                }
                
                // Usar ItextPDFGenerator para PDFs profesionales
                val pdfFile = ItextPDFGenerator.generateScanReport(
                    context,
                    results,
                    forensicMode
                )
                
                // Share PDF con soporte explícito para email
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile
                    )
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Reporte de seguridad GuardianOS")
                        putExtra(Intent.EXTRA_TEXT, 
                            "Adjunto el informe de seguridad generado por GuardianOS.\n\n" +
                            "Apps analizadas: ${results.size}\n" +
                            "Amenazas detectadas: ${results.count { it.isMalware || it.isStalkerware }}\n\n" +
                            "Más información: https://guardianos.es"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    context.startActivity(Intent.createChooser(shareIntent, "Enviar reporte por email"))
                    
                    Toast.makeText(
                        context,
                        "PDF generado: ${pdfFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error al generar PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Detecta permisos excesivos en apps (PRO feature).
     * Un permiso se considera excesivo si la app lo tiene pero es inusual para su categoría.
     */
    private fun detectExcessivePermissions(context: Context, apps: List<AppAudit>): List<ExcessivePermission> {
        val excessive = mutableListOf<ExcessivePermission>()
        val pm = context.packageManager
        
        // Permisos considerados excesivos si no son apps del sistema
        val dangerousPerms = listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE"
        )
        
        apps.forEach { app ->
            try {
                val packageInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                val requestedPerms = packageInfo.requestedPermissions ?: emptyArray()
                val grantedPerms = packageInfo.requestedPermissionsFlags ?: IntArray(0)
                
                requestedPerms.forEachIndexed { index, perm ->
                    // Verificar si está granted y es peligroso
                    val isGranted = (grantedPerms[index] and PackageManager.PERMISSION_GRANTED) != 0
                    
                    if (isGranted && dangerousPerms.any { perm.contains(it) }) {
                        // Analizar si es excesivo basado en el tipo de app
                        val isExcessive = when {
                            // Linterna no debería necesitar ubicación, contactos, etc
                            app.appName.contains("linterna", ignoreCase = true) || 
                            app.appName.contains("flashlight", ignoreCase = true) -> 
                                perm.contains("LOCATION") || perm.contains("CONTACTS") || perm.contains("SMS")
                            
                            // Apps de fondos de pantalla no deberían necesitar cámara, mic, SMS
                            app.appName.contains("wallpaper", ignoreCase = true) ||
                            app.appName.contains("fondo", ignoreCase = true) ->
                                perm.contains("CAMERA") || perm.contains("RECORD_AUDIO") || perm.contains("SMS")
                            
                            // Juegos que piden ubicación background es sospechoso
                            app.appName.contains("game", ignoreCase = true) ||
                            app.appName.contains("juego", ignoreCase = true) ->
                                perm.contains("BACKGROUND_LOCATION") || perm.contains("READ_SMS")
                            
                            // Apps que no son de mensajería no deberían leer SMS
                            !app.appName.contains("message", ignoreCase = true) &&
                            !app.appName.contains("sms", ignoreCase = true) &&
                            !app.appName.contains("whatsapp", ignoreCase = true) &&
                            !app.appName.contains("telegram", ignoreCase = true) ->
                                perm.contains("READ_SMS") || perm.contains("SEND_SMS")
                            
                            else -> false
                        }
                        
                        if (isExcessive) {
                            excessive.add(
                                ExcessivePermission(
                                    packageName = app.packageName,
                                    permission = getFriendlyPermissionName(perm),
                                    reason = "Inusual para este tipo de app"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignorar apps que no se pueden analizar
            }
        }
        
        return excessive
    }
    
    /**
     * Exporta el informe de privacidad en formato PDF (PRO).
     */
    private suspend fun exportPrivacyReportPDF(context: Context, report: PrivacyReport?) {
        if (report == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No hay reporte para exportar", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                // Crear PDF con información del reporte de privacidad
                val timestamp = System.currentTimeMillis()
                val fileName = "GuardianOS_Privacy_Report_$timestamp.pdf"
                val pdfFile = File(context.getExternalFilesDir(null), fileName)
                
                // Usar iText para generar PDF profesional
                val document = com.itextpdf.kernel.pdf.PdfDocument(
                    com.itextpdf.kernel.pdf.PdfWriter(pdfFile)
                )
                val pdfDoc = com.itextpdf.layout.Document(document)
                
                // Título
                val titleFont = com.itextpdf.kernel.font.PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD
                )
                val title = com.itextpdf.layout.element.Paragraph("INFORME DE PRIVACIDAD")
                    .setFont(titleFont)
                    .setFontSize(24f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                pdfDoc.add(title)
                
                // Score
                val scoreColor = when {
                    report.privacyScore >= 80 -> com.itextpdf.kernel.colors.ColorConstants.GREEN
                    report.privacyScore >= 50 -> com.itextpdf.kernel.colors.ColorConstants.ORANGE
                    else -> com.itextpdf.kernel.colors.ColorConstants.RED
                }
                val score = com.itextpdf.layout.element.Paragraph("Puntuación: ${report.privacyScore}/100")
                    .setFont(titleFont)
                    .setFontSize(18f)
                    .setFontColor(scoreColor)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                pdfDoc.add(score)
                
                pdfDoc.add(com.itextpdf.layout.element.Paragraph("\n"))
                
                // Resumen
                pdfDoc.add(com.itextpdf.layout.element.Paragraph("RESUMEN EJECUTIVO")
                    .setFont(titleFont)
                    .setFontSize(14f))
                pdfDoc.add(com.itextpdf.layout.element.Paragraph(
                    "• Apps con riesgo de privacidad: ${report.riskyApps.size}\n" +
                    "• Permisos excesivos detectados: ${report.excessivePermissions.size}\n" +
                    "• Apps de alto riesgo: ${report.riskyApps.count { it.riskLevel == "high" }}\n\n"
                ))
                
                // Apps de riesgo
                if (report.riskyApps.isNotEmpty()) {
                    pdfDoc.add(com.itextpdf.layout.element.Paragraph("APPS CON RIESGO DE PRIVACIDAD")
                        .setFont(titleFont)
                        .setFontSize(14f))
                    
                    report.riskyApps.forEach { app ->
                        pdfDoc.add(com.itextpdf.layout.element.Paragraph(
                            "• ${app.appName} (${app.riskLevel.uppercase()})\n" +
                            "  Package: ${app.packageName}\n" +
                            "  Preocupaciones: ${app.concerns.joinToString(", ")}\n"
                        ))
                    }
                    pdfDoc.add(com.itextpdf.layout.element.Paragraph("\n"))
                }
                
                // Permisos excesivos
                if (report.excessivePermissions.isNotEmpty()) {
                    pdfDoc.add(com.itextpdf.layout.element.Paragraph("PERMISOS EXCESIVOS")
                        .setFont(titleFont)
                        .setFontSize(14f))
                    
                    report.excessivePermissions.forEach { perm ->
                        pdfDoc.add(com.itextpdf.layout.element.Paragraph(
                            "• ${perm.permission}\n" +
                            "  App: ${perm.packageName}\n" +
                            "  Razón: ${perm.reason}\n"
                        ))
                    }
                }
                
                // Footer
                pdfDoc.add(com.itextpdf.layout.element.Paragraph("\n\n"))
                pdfDoc.add(com.itextpdf.layout.element.Paragraph(
                    "Generado por GuardianOS v1.0\n" +
                    "Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                ).setFontSize(10f).setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                
                pdfDoc.close()
                
                // Compartir PDF
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile
                    )
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir informe"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting privacy PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error al generar PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Exporta el informe de auditoría ISO 27001 en formato PDF (PRO).
     */
    private suspend fun exportISOAuditPDF(context: Context, results: List<ISOViolation>) {
        withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val fileName = "GuardianOS_ISO27001_$timestamp.pdf"
                val pdfFile = File(context.getExternalFilesDir(null), fileName)
                
                val document = com.itextpdf.kernel.pdf.PdfDocument(
                    com.itextpdf.kernel.pdf.PdfWriter(pdfFile)
                )
                val pdfDoc = com.itextpdf.layout.Document(document)
                
                val titleFont = com.itextpdf.kernel.font.PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD
                )
                pdfDoc.add(com.itextpdf.layout.element.Paragraph("AUDITORÍA ISO 27001")
                    .setFont(titleFont).setFontSize(24f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                
                val score = if (results.isEmpty()) 100 else maxOf(0, 100 - (results.size * 10))
                pdfDoc.add(com.itextpdf.layout.element.Paragraph("Cumplimiento: $score%")
                    .setFont(titleFont).setFontSize(18f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                
                if (results.isNotEmpty()) {
                    pdfDoc.add(com.itextpdf.layout.element.Paragraph("\nVIOLACIONES:").setFont(titleFont))
                    results.forEach {
                        pdfDoc.add(com.itextpdf.layout.element.Paragraph(
                            "• ${it.control} (${it.severity}): ${it.description}\n"
                        ))
                    }
                }
                
                pdfDoc.close()
                
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir informe"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting ISO PDF", e)
            }
        }
    }
    
    /**
     * Exporta el informe del monitor de red en formato PDF (PRO).
     */
    private suspend fun exportNetworkMonitorPDF(context: Context, stats: NetworkStats?, connections: List<NetworkConnection>) {
        if (stats == null) return
        
        withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val fileName = "GuardianOS_Network_$timestamp.pdf"
                val pdfFile = File(context.getExternalFilesDir(null), fileName)
                
                val document = com.itextpdf.kernel.pdf.PdfDocument(
                    com.itextpdf.kernel.pdf.PdfWriter(pdfFile)
                )
                val pdfDoc = com.itextpdf.layout.Document(document)
                
                val titleFont = com.itextpdf.kernel.font.PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD
                )
                pdfDoc.add(com.itextpdf.layout.element.Paragraph("MONITOR DE RED")
                    .setFont(titleFont).setFontSize(24f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                
                pdfDoc.add(com.itextpdf.layout.element.Paragraph("\nESTADÍSTICAS:").setFont(titleFont))
                pdfDoc.add(com.itextpdf.layout.element.Paragraph(
                    "Conexiones: ${stats.totalConnections}\nSospechosas: ${stats.suspiciousConnections}\n"
                ))
                
                if (connections.any { it.isSuspicious }) {
                    pdfDoc.add(com.itextpdf.layout.element.Paragraph("\nSOSPECHOSAS:").setFont(titleFont))
                    connections.filter { it.isSuspicious }.forEach {
                        pdfDoc.add(com.itextpdf.layout.element.Paragraph(
                            "• ${it.appName} → ${it.remoteAddress}:${it.remotePort}\n"
                        ))
                    }
                }
                
                pdfDoc.close()
                
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir informe"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting network PDF", e)
            }
        }
    }
    
    /**
     * Convierte nombre técnico de permiso a nombre amigable.
     */
    private fun getPermissionIconAndName(permission: String): Pair<String, String> {
        return when {
            permission.contains("CAMERA") -> "📷" to "Cámara"
            permission.contains("RECORD_AUDIO") -> "🎤" to "Micrófono"
            permission.contains("ACCESS_BACKGROUND_LOCATION") -> "📍" to "Ubicación en segundo plano"
            permission.contains("ACCESS_FINE_LOCATION") -> "📍" to "Ubicación precisa (GPS)"
            permission.contains("ACCESS_COARSE_LOCATION") -> "📍" to "Ubicación aproximada"
            permission.contains("ACCESS_MEDIA_LOCATION") -> "📍" to "Ubicación en fotos/vídeos"
            permission.contains("READ_CONTACTS") -> "👤" to "Contactos"
            permission.contains("READ_SMS") || permission.contains("SEND_SMS") -> "💬" to "Mensajes SMS"
            permission.contains("READ_CALL_LOG") || permission.contains("WRITE_CALL_LOG") -> "📞" to "Registro de llamadas"
            permission.contains("CALL_PHONE") -> "📞" to "Realizar llamadas"
            permission.contains("READ_PHONE_STATE") -> "📱" to "Estado del teléfono (IMEI)"
            permission.contains("READ_EXTERNAL_STORAGE") -> "📁" to "Acceso a archivos"
            permission.contains("WRITE_EXTERNAL_STORAGE") -> "📁" to "Escritura en archivos"
            permission.contains("READ_MEDIA_IMAGES") -> "🖼️" to "Fotos"
            permission.contains("READ_MEDIA_VIDEO") -> "🎬" to "Vídeos"
            permission.contains("READ_MEDIA_AUDIO") -> "🎵" to "Archivos de audio"
            permission.contains("BODY_SENSORS") -> "💓" to "Sensores corporales"
            permission.contains("ACTIVITY_RECOGNITION") -> "🏃" to "Actividad física"
            permission.contains("READ_CALENDAR") || permission.contains("WRITE_CALENDAR") -> "📅" to "Calendario"
            permission.contains("BLUETOOTH_CONNECT") -> "📶" to "Bluetooth"
            permission.contains("NEARBY_WIFI_DEVICES") -> "📶" to "Dispositivos Wi-Fi cercanos"
            permission.contains("SYSTEM_ALERT_WINDOW") -> "⚠️" to "Superposición sobre apps"
            permission.contains("REQUEST_INSTALL_PACKAGES") -> "⚠️" to "Instalar apps externas"
            permission.contains("ACCESSIBILITY") -> "⚠️" to "Accesibilidad (control total)"
            else -> "🔒" to permission.substringAfterLast(".")
        }
    }

    private fun getFriendlyPermissionName(permission: String): String {
        return when {
            permission.contains("CAMERA") -> "Cámara"
            permission.contains("RECORD_AUDIO") -> "Micrófono"
            permission.contains("LOCATION") -> "Ubicación"
            permission.contains("CONTACTS") -> "Contactos"
            permission.contains("SMS") -> "SMS"
            permission.contains("CALL") -> "Registro de Llamadas"
            permission.contains("PHONE") -> "Estado del Teléfono"
            else -> permission.substringAfterLast(".")
        }
    }
}
