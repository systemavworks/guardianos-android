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

import com.guardianos.core.audit.AppAuditor
import com.guardianos.core.data.MalwareDatabase
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
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuardianOSApp() }
    }

    /* ────────────────────────── UI ────────────────────────── */
    @Composable
    fun GuardianOSApp() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var loading by remember { mutableStateOf(false) }
        var auditMode by remember { mutableStateOf(AuditMode.QUICK) }
        var systemFindings by remember { mutableStateOf<List<AuditFinding>>(emptyList()) }
        var apps by remember { mutableStateOf<List<AppAudit>>(emptyList()) }
        var selectedApp by remember { mutableStateOf<AppAudit?>(null) }
        var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
        var showAboutDialog by remember { mutableStateOf(false) } // 👈 Estado del diálogo

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF3B82F6),
                background = Color(0xFF0F1C2E),
                surface = Color(0xFF1A2332),
                onSurface = Color.White
            )
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    // 👇 Header con botón de info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("GuardianOS", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("Protección digital ética para menores", fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { showAboutDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Quiénes somos",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo auditoría")
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = auditMode == AuditMode.FULL,
                            onCheckedChange = {
                                auditMode = if (it) AuditMode.FULL else AuditMode.QUICK
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (auditMode == AuditMode.FULL) "Completa (con APK)" else "Rápida")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        onClick = {
                            scope.launch {
                                loading = true
                                deviceInfo = getDeviceInfo()
                                systemFindings = appAuditor.auditSystem(context)
                                apps = appAuditor.auditApps(context, auditMode)
                                loading = false
                            }
                        }
                    ) {
                        Text(if (loading) "Auditando…" else "Iniciar auditoría")
                    }
                    Spacer(Modifier.height(16.dp))
                    deviceInfo?.let { info ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Dispositivo auditado", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Modelo: ${info.model}", fontSize = 12.sp)
                                Text("Fabricante: ${info.manufacturer}", fontSize = 12.sp)
                                Text("Android: ${info.androidVersion} (API ${info.sdkVersion})", fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        if (selectedApp == null) {
                            item {
                                if (apps.isNotEmpty()) {
                                    val critical = apps.count { it.risk == Risk.CRITICAL }
                                    val high = apps.count { it.risk == Risk.HIGH }
                                    val suspicious = apps.count { it.findings.isNotEmpty() }
                                    val malware = apps.count { it.findings.any { f ->
                                        f.title.contains("Malware", ignoreCase = true) ||
                                                f.title.contains("Firma", ignoreCase = true)
                                    } }
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (malware > 0 || critical > 0) Color(0xFF7F1D1D)
                                            else if (high > 0) Color(0xFF7C2D12)
                                            else Color(0xFF14532D)
                                        )
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("📊 Resumen de Auditoría", fontWeight = FontWeight.Bold)
                                            if (malware > 0) {
                                                Text("🚨 $malware apps con FIRMAS DE MALWARE",
                                                    color = Color(0xFFFF6B6B),
                                                    fontWeight = FontWeight.Bold)
                                            }
                                            if (critical > 0) {
                                                Text("⚠️ $critical apps CRÍTICAS detectadas", color = Color(0xFFFF6B6B))
                                            }
                                            if (high > 0) {
                                                Text("⚠️ $high apps de ALTO riesgo", color = Color(0xFFFFA726))
                                            }
                                            Text("🔍 $suspicious apps con comportamiento sospechoso")
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            item {
                                Text("Aplicaciones (${apps.size})", fontWeight = FontWeight.Bold)
                            }
                            items(apps.size) { i ->
                                val app = apps[i]
                                val hasMalwareSignature = app.findings.any {
                                    it.title.contains("Malware", ignoreCase = true) ||
                                            it.title.contains("Firma", ignoreCase = true)
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { selectedApp = app },
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            hasMalwareSignature -> Color(0xFF450A0A)
                                            app.risk == Risk.CRITICAL -> Color(0xFF450A0A)
                                            app.risk == Risk.HIGH -> Color(0xFF431407)
                                            app.risk == Risk.MEDIUM -> Color(0xFF422006)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                if (hasMalwareSignature) {
                                                    Text("🚨 MALWARE", fontSize = 10.sp,
                                                        color = Color(0xFFFF6B6B),
                                                        fontWeight = FontWeight.Bold)
                                                }
                                                Text(app.appName, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                app.risk.toString(),
                                                fontSize = 11.sp,
                                                color = when (app.risk) {
                                                    Risk.CRITICAL -> Color(0xFFFF6B6B)
                                                    Risk.HIGH -> Color(0xFFFFA726)
                                                    Risk.MEDIUM -> Color(0xFFFFD93D)
                                                    Risk.LOW -> Color(0xFF6BCF7F)
                                                }
                                            )
                                        }
                                        Text("Puntuación: ${app.riskScore}/100", fontSize = 12.sp)
                                        if (app.findings.isNotEmpty()) {
                                            Text(
                                                "⚠️ ${app.findings.size} hallazgos",
                                                fontSize = 11.sp,
                                                color = Color(0xFFFF6B6B)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                AppDetailScreen(
                                    context = context,
                                    app = selectedApp!!,
                                    deviceInfo = deviceInfo,
                                    onBack = { selectedApp = null },
                                    onExportPdf = {
                                        val pdf = generateAppPdf(context, selectedApp!!, deviceInfo)
                                        sharePdf(context, pdf)
                                    }
                                )
                            }
                        }
                        item {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = deviceInfo != null && apps.isNotEmpty(),
                                onClick = {
                                    val pdf = generateGlobalPdf(context, deviceInfo!!, systemFindings, apps)
                                    sharePdf(context, pdf)
                                }
                            ) {
                                Text("Exportar informe general PDF")
                            }
                        }
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "https://guardianos.es · info@guardianos.es",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 👇 Diálogo "Quiénes somos"
            if (showAboutDialog) {
                AboutDialog(onDismiss = { showAboutDialog = false })
            }
        }
    }

    @Composable
    fun AppDetailScreen(
        context: Context,
        app: AppAudit,
        deviceInfo: DeviceInfo?,
        onBack: () -> Unit,
        onExportPdf: () -> Unit
    ) {
        Column {
            Text(app.appName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Paquete: ${app.packageName}", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Riesgo: ${app.risk} (${app.riskScore}/100)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = when (app.risk) {
                    Risk.CRITICAL -> Color(0xFFFF6B6B)
                    Risk.HIGH -> Color(0xFFFFA726)
                    Risk.MEDIUM -> Color(0xFFFFD93D)
                    Risk.LOW -> Color(0xFF6BCF7F)
                }
            )
            Spacer(Modifier.height(12.dp))
            if (app.findings.isNotEmpty()) {
                Text("🚨 Hallazgos de Seguridad", fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                Spacer(Modifier.height(4.dp))
                app.findings.sortedByDescending { it.weight }.forEach { finding ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                finding.weight >= 40 -> Color(0xFF450A0A)
                                finding.weight >= 25 -> Color(0xFF431407)
                                else -> Color(0xFF422006)
                            }
                        )
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("• ${finding.title}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(finding.description, fontSize = 11.sp, color = Color.Gray)
                            Text("Impacto: ${finding.weight} pts", fontSize = 10.sp,
                                color = Color(0xFFFFA726))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text("Permisos peligrosos (${app.permissions.count { it.dangerous }})",
                fontWeight = FontWeight.Bold)
            app.permissions.filter { it.dangerous }.take(10).forEach {
                Text("• ${it.name.substringAfterLast(".")}", fontSize = 12.sp)
            }
            if (app.permissions.count { it.dangerous } > 10) {
                Text("... y ${app.permissions.count { it.dangerous } - 10} más",
                    fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Text("Origen: ${app.installSource}", fontSize = 12.sp)
            Text("Versión: ${app.versionName}", fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onExportPdf, modifier = Modifier.fillMaxWidth()) {
                Text("Exportar PDF detallado")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://guardianos.es/contact"))
                    )
                }
            ) {
                Text("Contactar con GuardianOS")
            }
            TextButton(onClick = onBack) {
                Text("← Volver")
            }
        }
    }

    // 👇 FUNCIÓN DEL DIÁLOGO "QUIÉNES SOMOS"
    @Composable
    fun AboutDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text(
                        "🛡️ GuardianOS",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Protección digital ética para menores",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        "Acerca del Proyecto",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        "GuardianOS es un proyecto de código abierto dedicado a la privacidad y seguridad ética en dispositivos móviles Android, con especial enfoque en la protección de menores.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        "🌍 Origen",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "Desarrollado en Sevilla, Andalucía, España",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        "👨‍💻 Responsable del Proyecto",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "Víctor Shift Lara",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        "🎯 Misión",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "Proporcionar herramientas gratuitas y transparentes para que familias y educadores puedan proteger a los menores de amenazas digitales, respetando siempre su privacidad y derechos.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        "✨ Características",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Column(modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)) {
                        Text("• Auditoría local sin envío de datos", fontSize = 12.sp)
                        Text("• Detección de malware y amenazas", fontSize = 12.sp)
                        Text("• Análisis de permisos peligrosos", fontSize = 12.sp)
                        Text("• Código abierto y transparente", fontSize = 12.sp)
                        Text("• Informes PDF detallados", fontSize = 12.sp)
                    }

                    Text(
                        "🔒 Privacidad",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "Todo el análisis se realiza localmente en el dispositivo. No se envía ninguna información personal ni datos de apps a servidores externos.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        "📧 Contacto",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text("Web: https://guardianos.es", fontSize = 12.sp)
                    Text("Email: info@guardianos.es", fontSize = 12.sp)
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Text(
                        "Versión 1.0.0 - 2025",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    /* ────────────────── AUDITORÍA AVANZADA CON 4 CAPAS ────────────────── */
    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = when (Build.VERSION.SDK_INT) {
                Build.VERSION_CODES.TIRAMISU -> "Android 13"
                Build.VERSION_CODES.S_V2 -> "Android 12L"
                Build.VERSION_CODES.S -> "Android 12"
                Build.VERSION_CODES.R -> "Android 11"
                Build.VERSION_CODES.Q -> "Android 10"
                Build.VERSION_CODES.P -> "Android 9"
                Build.VERSION_CODES.O_MR1 -> "Android 8.1"
                Build.VERSION_CODES.O -> "Android 8.0"
                else -> if (Build.VERSION.SDK_INT >= 34) "Android 14+" else "Android ${Build.VERSION.SDK_INT}"
            },
            sdkVersion = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH
            } else {
                "N/A"
            }
        )
    }

    private suspend fun auditApps(
        context: Context,
        mode: AuditMode
    ): List<AppAudit> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { app ->
            try {
                val pkg = pm.getPackageInfo(app.packageName,
                    PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES)
                val permissions = pkg.requestedPermissions?.map {
                    AppPermission(it, isDangerousPermission(it))
                } ?: emptyList()
                val source = detectInstallSource(context, app.packageName)
                val findings = mutableListOf<AuditFinding>()
                var score = 0
                // ╔═══════════════════════════════════════════════════
                // 🔴 CAPA 1: HUELLAS ESTÁTICAS (Signatures Light)
                // ╚═══════════════════════════════════════════════════
                val signatureCheck = checkMalwareSignatures(pkg, app.packageName)
                findings.addAll(signatureCheck.findings)
                score += signatureCheck.score
                // ╔═══════════════════════════════════════════════════
                // 🟡 CAPA 2: COMPORTAMIENTO HEURÍSTICO
                // ╚═══════════════════════════════════════════════════
                val dangerousPerms = permissions.filter { it.dangerous }
                score += dangerousPerms.size * 12
                findings.addAll(detectSuspiciousPermissionCombinations(permissions))
                findings.addAll(analyzePackageName(app.packageName, pm.getApplicationLabel(app).toString()))
                findings.addAll(detectImpersonation(app.packageName, pm.getApplicationLabel(app).toString()))
                findings.addAll(detectAdminCapabilities(context, app.packageName))
                findings.addAll(detectObfuscationPatterns(app.packageName))
                if (permissions.size > 25) {
                    findings.add(AuditFinding(
                        "Permisos excesivos",
                        "Solicita ${permissions.size} permisos (umbral alto)",
                        15
                    ))
                }
                // ╔═══════════════════════════════════════════════════
                // 🟠 CAPA 3: INTEGRIDAD DEL APK (solo modo FULL)
                // ╚═══════════════════════════════════════════════════
                if (mode == AuditMode.FULL) {
                    val integrityCheck = analyzeApkIntegrity(context, pkg, app.packageName)
                    findings.addAll(integrityCheck.findings)
                    score += integrityCheck.score
                }
                // ╔═══════════════════════════════════════════════════
                // 🔵 CAPA 4: INDICADORES DE COMPROMISO (IoC ligero)
                // ╚═══════════════════════════════════════════════════
                if (mode == AuditMode.FULL) {
                    val iocCheck = checkNetworkIndicators(context, app.packageName)
                    findings.addAll(iocCheck.findings)
                    score += iocCheck.score
                }
                // Penalizaciones adicionales
                if (source == InstallSource.UNKNOWN && dangerousPerms.size > 3) {
                    findings.add(AuditFinding(
                        "Origen no verificado + permisos",
                        "Origen desconocido con múltiples permisos sensibles",
                        25
                    ))
                    score += 25
                }
                score += findings.sumOf { it.weight }
                score = minOf(score, 100)
                val risk = when {
                    score >= 80 -> Risk.CRITICAL
                    score >= 60 -> Risk.HIGH
                    score >= 30 -> Risk.MEDIUM
                    else -> Risk.LOW
                }
                AppAudit(
                    appName = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    versionName = pkg.versionName ?: "N/A",
                    isSystemApp = source == InstallSource.SYSTEM,
                    installSource = source,
                    permissions = permissions,
                    findings = findings,
                    riskScore = score,
                    risk = risk
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.riskScore }
    }

    /* ╔═══════════════════════════════════════════════════
    🔴 CAPA 1: HUELLAS ESTÁTICAS
    ╚═══════════════════════════════════════════════════ */
    private fun checkMalwareSignatures(pkg: PackageInfo, packageName: String): SecurityCheckResult {
        val findings = mutableListOf<AuditFinding>()
        var score = 0
        try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pkg.signatures
            }
            signatures?.forEach { signature ->
                val certHash = calculateSHA256(signature.toByteArray())
                // Verificar contra base de datos de firmas maliciosas
                val malwareMatch = malwareSignatures.checkCertificateHash(certHash)
                if (malwareMatch != null) {
                    findings.add(AuditFinding(
                        "🚨 FIRMA DE MALWARE DETECTADA",
                        "Certificado coincide con malware conocido: ${malwareMatch.name}",
                        50
                    ))
                    score += 50
                }
                // Certificados de debug
                if (certHash.startsWith("a40da80a") || isDebugCertificate(signature)) {
                    findings.add(AuditFinding(
                        "Certificado de desarrollo",
                        "App firmada con certificado debug en producción",
                        20
                    ))
                    score += 20
                }
            }
            // Verificar hash del paquete
            val packageMatch = malwareSignatures.checkPackageName(packageName)
            if (packageMatch != null) {
                findings.add(AuditFinding(
                    "🚨 PAQUETE MALICIOSO CONOCIDO",
                    "Paquete identificado como: ${packageMatch.name}",
                    50
                ))
                score += 50
            }
        } catch (e: Exception) {
            // Ignorar errores
        }
        return SecurityCheckResult(findings, score)
    }

    /* ╔═══════════════════════════════════════════════════
    🟡 CAPA 2: COMPORTAMIENTO HEURÍSTICO
    ╚═══════════════════════════════════════════════════ */
    private fun isDangerousPermission(permission: String): Boolean {
        val dangerous = listOf(
            "CAMERA", "LOCATION", "FINE_LOCATION", "COARSE_LOCATION",
            "RECORD_AUDIO", "READ_CONTACTS", "WRITE_CONTACTS",
            "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_PHONE_STATE",
            "CALL_PHONE", "READ_CALL_LOG", "WRITE_CALL_LOG",
            "READ_CALENDAR", "WRITE_CALENDAR", "BODY_SENSORS",
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
            "ACCESS_MEDIA_LOCATION", "BLUETOOTH", "NEARBY_WIFI",
            "POST_NOTIFICATIONS", "REQUEST_INSTALL_PACKAGES",
            "SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS"
        )
        return dangerous.any { permission.contains(it) }
    }

    private fun detectSuspiciousPermissionCombinations(permissions: List<AppPermission>): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()
        val perms = permissions.map { it.name }
        // Patrón spyware
        if (perms.any { it.contains("CAMERA") } &&
            perms.any { it.contains("RECORD_AUDIO") } &&
            perms.any { it.contains("LOCATION") }) {
            findings.add(AuditFinding(
                "Patrón de vigilancia",
                "Combinación típica de spyware: cámara + audio + ubicación",
                35
            ))
        }
        // Acceso total comunicaciones
        if (perms.any { it.contains("READ_SMS") } &&
            perms.any { it.contains("READ_CONTACTS") } &&
            perms.any { it.contains("CALL_PHONE") }) {
            findings.add(AuditFinding(
                "Acceso total a comunicaciones",
                "Control completo sobre SMS, contactos y llamadas",
                30
            ))
        }
        // Ransomware potencial
        if (perms.any { it.contains("WRITE_EXTERNAL") } &&
            perms.any { it.contains("INTERNET") } &&
            perms.any { it.contains("REQUEST_INSTALL") }) {
            findings.add(AuditFinding(
                "Perfil de ransomware",
                "Puede cifrar archivos, comunicarse externamente e instalar apps",
                30
            ))
        }
        // Troyano bancario
        if (perms.any { it.contains("SYSTEM_ALERT_WINDOW") } &&
            perms.any { it.contains("READ_SMS") } &&
            perms.any { it.contains("INTERNET") }) {
            findings.add(AuditFinding(
                "Patrón de troyano bancario",
                "Puede crear overlays, leer SMS (2FA) y enviar datos",
                35
            ))
        }
        return findings
    }

    private fun analyzePackageName(packageName: String, appName: String): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()
        val suspicious = listOf(
            "com.app.test", "com.example", "com.android.test",
            "free.vpn", "free.antivirus", "hack", "crack", "mod",
            "pro.unlock", "premium.free", "cheat"
        )
        if (suspicious.any { packageName.contains(it, ignoreCase = true) }) {
            findings.add(AuditFinding(
                "Nombre de paquete sospechoso",
                "Sigue patrones comunes en malware/apps pirateadas",
                18
            ))
        }
        val parts = packageName.split(".")
        if (parts.any { it.length <= 1 } ||
            parts.any { it.matches(Regex(".*\\d{4,}.*")) }) {
            findings.add(AuditFinding(
                "Estructura anómala de paquete",
                "Nombre con partes muy cortas o muchos números",
                12
            ))
        }
        return findings
    }

    private fun detectImpersonation(packageName: String, appName: String): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()
        val legitApps = mapOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "tiktok" to "com.zhiliaoapp.musically",
            "youtube" to "com.google.android.youtube",
            "netflix" to "com.netflix.mediaclient",
            "spotify" to "com.spotify.music"
        )
        legitApps.forEach { (key, legitPkg) ->
            if ((appName.contains(key, ignoreCase = true) ||
                        packageName.contains(key, ignoreCase = true)) &&
                packageName != legitPkg) {
                findings.add(AuditFinding(
                    "⚠️ Posible suplantación",
                    "Imita a $key (legítimo: $legitPkg)",
                    40
                ))
            }
        }
        return findings
    }

    private fun detectObfuscationPatterns(packageName: String): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()
        // Nombres ofuscados con caracteres similares
        if (packageName.matches(Regex(".*[Il1oO0]{3,}.*"))) {
            findings.add(AuditFinding(
                "Ofuscación de nombre",
                "Usa caracteres similares para confundir (l, I, 1, o, O, 0)",
                15
            ))
        }
        return findings
    }

    private fun detectAdminCapabilities(context: Context, packageName: String): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()
        try {
            val pm = context.packageManager
            val pkg = pm.getPackageInfo(packageName, PackageManager.GET_RECEIVERS)
            pkg.receivers?.forEach { receiver ->
                if (receiver.permission == "android.permission.BIND_DEVICE_ADMIN") {
                    findings.add(AuditFinding(
                        "Capacidades de administrador",
                        "Puede obtener privilegios de administrador del dispositivo",
                        30
                    ))
                }
            }
        } catch (e: Exception) {
            // Ignorar
        }
        return findings
    }

    /* ╔═══════════════════════════════════════════════════
    🟠 CAPA 3: INTEGRIDAD DEL APK
    ╚═══════════════════════════════════════════════════ */
    private fun analyzeApkIntegrity(context: Context, pkg: PackageInfo, packageName: String): SecurityCheckResult {
        val findings = mutableListOf<AuditFinding>()
        var score = 0
        try {
            val apkPath = context.packageManager.getApplicationInfo(packageName, 0).sourceDir
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return SecurityCheckResult(findings, score)
            // 1. Tamaño anormalmente pequeño (< 150 KB)
            if (apkFile.length() < 150_000) {
                findings.add(AuditFinding(
                    "APK sospechosamente pequeño",
                    "Tamaño: ${apkFile.length()} bytes — posible stub o downloader",
                    20
                ))
                score += 20
            }
            // 2. Fecha de modificación posterior a instalación (reempaquetado)
            val installTime = pkg.firstInstallTime
            val lastModified = apkFile.lastModified()
            if (lastModified > installTime + 2 * 3600_000) { // >2h después
                findings.add(AuditFinding(
                    "APK modificado tras instalación",
                    "Archivo modificado ${Date(lastModified)} vs instalación ${Date(installTime)}",
                    25
                ))
                score += 25
            }
            // 3. Verificar número de firmas
            val signatureCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.signingInfo?.apkContentsSigners?.size ?: 1
            } else {
                @Suppress("DEPRECATION")
                pkg.signatures?.size ?: 1
            }
            if (signatureCount > 1) {
                findings.add(AuditFinding(
                    "Múltiples firmas en APK",
                    "Detectadas $signatureCount firmas — indica reempaquetado o inyección",
                    30
                ))
                score += 30
            }
            // 4. Verificar integridad ZIP mínima
            try {
                ZipFile(apkFile).use { zip ->
                    val entries = zip.entries().toList()
                    val hasDex = entries.any { it.name == "classes.dex" || it.name.startsWith("classes") }
                    val hasManifest = entries.any { it.name == "AndroidManifest.xml" }
                    if (!hasDex) {
                        findings.add(AuditFinding(
                            "⚠️ APK sin código ejecutable",
                            "No contiene classes.dex — posible APK vacío o corrompido",
                            25
                        ))
                        score += 25
                    }
                    if (!hasManifest) {
                        findings.add(AuditFinding(
                            "⚠️ APK sin manifiesto",
                            "Falta AndroidManifest.xml — estructura inválida",
                            30
                        ))
                        score += 30
                    }
                }
            } catch (e: Exception) {
                findings.add(AuditFinding(
                    "Error al leer APK",
                    "No se pudo analizar la estructura ZIP — posible corrupción o protección",
                    20
                ))
                score += 20
            }
        } catch (e: Exception) {
            // No penalizar por errores técnicos
        }
        return SecurityCheckResult(findings, score)
    }

    /* ╔═══════════════════════════════════════════════════
    🔵 CAPA 4: INDICADORES DE COMPROMISO (IoC ligero)
    ╚═══════════════════════════════════════════════════ */
    private fun checkNetworkIndicators(context: Context, packageName: String): SecurityCheckResult {
        val findings = mutableListOf<AuditFinding>()
        var score = 0
        // Dominios sospechosos (offline, sin red)
        val suspiciousKeywords = listOf("tracker", "analytics", "adservice", "stat", "click", "log")
        if (suspiciousKeywords.any { packageName.contains(it, ignoreCase = true) }) {
            findings.add(AuditFinding(
                "Nombre sugiere tracking",
                "El nombre del paquete contiene términos asociados a rastreo",
                15
            ))
            score += 15
        }
        return SecurityCheckResult(findings, score)
    }

    /* ────────────────────────── FUNCIONES AUXILIARES ────────────────────────── */
    private fun isRooted(): Boolean {
        return try {
            (File("/system/bin/su").exists() ||
                    File("/system/xbin/su").exists() ||
                    Build.TAGS.contains("test-keys"))
        } catch (e: Exception) {
            false
        }
    }

    private fun calculateSHA256(data: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(data)
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "error"
        }
    }

    private fun isDebugCertificate(signature: Signature): Boolean {
        return try {
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(signature.toByteArray().inputStream()) as X509Certificate
            val subjectDN = cert.subjectX500Principal.name
            subjectDN.contains("Android Debug", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun detectInstallSource(context: Context, packageName: String): InstallSource {
        val pm = context.packageManager
        try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            return when {
                installer == null -> InstallSource.UNKNOWN
                installer.contains("com.android.vending") -> InstallSource.PLAY_STORE
                installer.contains("com.amazon.venezia") -> InstallSource.AMAZON
                installer.contains("com.sec.android.app.samsungapps") -> InstallSource.SAMSUNG
                installer.contains("adb") || installer.contains("packageinstaller") -> InstallSource.ADB
                else -> InstallSource.UNKNOWN
            }
        } catch (e: Exception) {
            return InstallSource.UNKNOWN
        }
    }

    private suspend fun auditSystem(context: Context): List<AuditFinding> =
        withContext(Dispatchers.Default) {
            val list = mutableListOf<AuditFinding>()
            // 1. Bloqueo de pantalla
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!km.isDeviceSecure) {
                list.add(AuditFinding("Bloqueo de pantalla", "No hay bloqueo configurado", 40))
            }
            // 2. Root
            if (isRooted()) {
                list.add(AuditFinding("Dispositivo rooteado", "El dispositivo tiene acceso root", 60))
            }
            // 3. Depuración USB
            val adbEnabled = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
            if (adbEnabled) {
                list.add(AuditFinding("Depuración USB", "La depuración USB está activada", 25))
            }
            // 4. Instalación de fuentes desconocidas (Android < 8)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val unknownSources = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.INSTALL_NON_MARKET_APPS,
                    0
                ) == 1
                if (unknownSources) {
                    list.add(AuditFinding(
                        "Fuentes desconocidas",
                        "Instalación desde fuentes desconocidas habilitada",
                        30
                    ))
                }
            }
            // 5. Verificación de apps
            try {
                val verifyApps = Settings.Global.getInt(
                    context.contentResolver,
                    "package_verifier_enable",
                    0
                )
                if (verifyApps == 0) {
                    list.add(AuditFinding(
                        "Verificación de apps desactivada",
                        "Google Play Protect o verificación de apps está desactivada",
                        35
                    ))
                }
            } catch (e: Exception) {
                // Si no se puede leer, no agregar el hallazgo
            }
            list
        }

    private fun sharePdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "Informe GuardianOS")
                putExtra(Intent.EXTRA_TEXT, "Informe de seguridad generado por GuardianOS – https://guardianos.es")
            }
            context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAppPdf(context: Context, app: AppAudit, deviceInfo: DeviceInfo?): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        var y = margin + 20f
        val titlePaint = Paint().apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(59, 130, 246)
        }
        val headerPaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        val normalPaint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
        }
        val smallPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.GRAY
        }
        canvas.drawText("GuardianOS – Informe de App", margin, y, titlePaint)
        y += 40f
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${dateFormat.format(Date())}", margin, y, smallPaint)
        y += 20f
        deviceInfo?.let { device ->
            canvas.drawText("Dispositivo: ${device.manufacturer} ${device.model}", margin, y, smallPaint)
            y += 15f
            canvas.drawText("Android: ${device.androidVersion}", margin, y, smallPaint)
            y += 25f
        }
        canvas.drawText("App: ${app.appName}", margin, y, headerPaint)
        y += 20f
        canvas.drawText("Paquete: ${app.packageName}", margin, y, normalPaint)
        y += 15f
        canvas.drawText("Versión: ${app.versionName} | Origen: ${app.installSource}", margin, y, normalPaint)
        y += 25f
        canvas.drawText("RIESGO: ${app.risk} (${app.riskScore}/100)", margin, y, headerPaint)
        y += 25f
        if (app.findings.isNotEmpty()) {
            canvas.drawText("HALLAZGOS (${app.findings.size}):", margin, y, headerPaint)
            y += 20f
            app.findings.sortedByDescending { it.weight }.take(10).forEach { f ->
                val color = when {
                    f.weight >= 40 -> "🔴 "
                    f.weight >= 25 -> "🟠 "
                    else -> "⚠️ "
                }
                canvas.drawText("$color${f.title}", margin, y, normalPaint)
                y += 15f
                canvas.drawText("   ${f.description} (${f.weight} pts)", margin + 10f, y, normalPaint)
                y += 20f
            }
            if (app.findings.size > 10) {
                canvas.drawText("... y ${app.findings.size - 10} hallazgos más", margin, y, smallPaint)
                y += 20f
            }
        }
        y += 20f
        canvas.drawText("Permisos peligrosos: ${app.permissions.count { it.dangerous }}", margin, y, normalPaint)
        y += 15f
        canvas.drawText("– Generado por GuardianOS", margin, y + 30f, smallPaint)
        canvas.drawText("https://guardianos.es | info@guardianos.es", margin, y + 45f, smallPaint)
        pdf.finishPage(page)
        val file = File(context.cacheDir, "guardianos_app_${app.packageName.take(15)}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun generateGlobalPdf(
        context: Context,
        deviceInfo: DeviceInfo,
        systemFindings: List<AuditFinding>,
        apps: List<AppAudit>
    ): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val maxY = pageHeight - margin - 30f
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin + 20f
        // Configuración de estilos
        val titlePaint = Paint().apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.rgb(59, 130, 246)
        }
        val headerPaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        val normalPaint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
        }
        val smallPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.GRAY
        }
        val criticalPaint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.rgb(220, 38, 38)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Función auxiliar para crear nueva página
        fun newPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin + 20f
        }
        // Función auxiliar para verificar espacio
        fun checkSpace(needed: Float) {
            if (y + needed > maxY) {
                newPage()
            }
        }
        // ========== PÁGINA 1: PORTADA Y RESUMEN ==========
        canvas.drawText("GuardianOS", margin, y, titlePaint)
        y += 25f
        canvas.drawText("Informe Global de Seguridad", margin, y, headerPaint)
        y += 40f
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${dateFormat.format(Date())}", margin, y, smallPaint)
        y += 30f
        // Información del dispositivo
        canvas.drawText("DISPOSITIVO AUDITADO", margin, y, headerPaint)
        y += 20f
        canvas.drawText("Modelo: ${deviceInfo.manufacturer} ${deviceInfo.model}", margin, y, normalPaint)
        y += 15f
        canvas.drawText("Android: ${deviceInfo.androidVersion} (API ${deviceInfo.sdkVersion})", margin, y, normalPaint)
        y += 15f
        canvas.drawText("Parche de seguridad: ${deviceInfo.securityPatch}", margin, y, normalPaint)
        y += 30f
        // Resumen estadístico
        val critical = apps.count { it.risk == Risk.CRITICAL }
        val high = apps.count { it.risk == Risk.HIGH }
        val medium = apps.count { it.risk == Risk.MEDIUM }
        val low = apps.count { it.risk == Risk.LOW }
        val malware = apps.count { it.findings.any { f ->
            f.title.contains("Malware", ignoreCase = true) ||
                    f.title.contains("Firma", ignoreCase = true)
        } }
        val suspicious = apps.count { it.findings.isNotEmpty() }
        canvas.drawText("RESUMEN EJECUTIVO", margin, y, headerPaint)
        y += 20f
        canvas.drawText("• Total de aplicaciones analizadas: ${apps.size}", margin, y, normalPaint)
        y += 15f
        if (malware > 0) {
            canvas.drawText("🚨 APPS CON FIRMAS DE MALWARE: $malware", margin, y, criticalPaint)
            y += 15f
        }
        canvas.drawText("• Apps de riesgo CRÍTICO: $critical", margin, y, normalPaint)
        y += 15f
        canvas.drawText("• Apps de riesgo ALTO: $high", margin, y, normalPaint)
        y += 15f
        canvas.drawText("• Apps de riesgo MEDIO: $medium", margin, y, normalPaint)
        y += 15f
        canvas.drawText("• Apps de riesgo BAJO: $low", margin, y, normalPaint)
        y += 15f
        canvas.drawText("• Apps con comportamiento sospechoso: $suspicious", margin, y, normalPaint)
        y += 30f
        // Hallazgos del sistema
        if (systemFindings.isNotEmpty()) {
            checkSpace(100f)
            canvas.drawText("HALLAZGOS DEL SISTEMA (${systemFindings.size})", margin, y, headerPaint)
            y += 20f
            systemFindings.take(5).forEach { finding ->
                checkSpace(30f)
                canvas.drawText("⚠️ ${finding.title}", margin, y, normalPaint)
                y += 15f
                canvas.drawText("   ${finding.description}", margin + 10f, y, smallPaint)
                y += 20f
            }
            if (systemFindings.size > 5) {
                canvas.drawText("... y ${systemFindings.size - 5} hallazgos más", margin, y, smallPaint)
                y += 20f
            }
        }
        // ========== PÁGINAS SIGUIENTES: DETALLE DE APPS ==========
        newPage()
        canvas.drawText("DETALLE DE APLICACIONES", margin, y, headerPaint)
        y += 30f
        // Ordenar apps por riesgo (críticas primero)
        val sortedApps = apps.sortedWith(
            compareByDescending<AppAudit> { it.risk.ordinal }
                .thenByDescending { it.riskScore }
        )
        sortedApps.forEach { app ->
            checkSpace(120f)
            // Barra de separación
            canvas.drawLine(margin, y, pageWidth - margin, y, Paint().apply {
                color = android.graphics.Color.LTGRAY
                strokeWidth = 1f
            })
            y += 15f
            // Nombre y riesgo
            val riskColor = when (app.risk) {
                Risk.CRITICAL -> android.graphics.Color.rgb(220, 38, 38)
                Risk.HIGH -> android.graphics.Color.rgb(234, 88, 12)
                Risk.MEDIUM -> android.graphics.Color.rgb(234, 179, 8)
                Risk.LOW -> android.graphics.Color.rgb(34, 197, 94)
            }
            val appTitlePaint = Paint().apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.BLACK
            }
            val riskPaint = Paint().apply {
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = riskColor
            }
            canvas.drawText(app.appName, margin, y, appTitlePaint)
            canvas.drawText("[${app.risk}]", pageWidth - margin - 80f, y, riskPaint)
            y += 18f
            canvas.drawText("Paquete: ${app.packageName}", margin, y, smallPaint)
            y += 12f
            canvas.drawText("Puntuación: ${app.riskScore}/100 | Origen: ${app.installSource}", margin, y, smallPaint)
            y += 18f
            // Hallazgos más importantes
            if (app.findings.isNotEmpty()) {
                checkSpace(60f)
                canvas.drawText("Hallazgos (${app.findings.size}):", margin, y, normalPaint)
                y += 15f
                app.findings.sortedByDescending { it.weight }.take(3).forEach { finding ->
                    checkSpace(25f)
                    val icon = when {
                        finding.title.contains("Malware", ignoreCase = true) -> "🚨"
                        finding.weight >= 40 -> "🔴"
                        finding.weight >= 25 -> "🟠"
                        else -> "⚠️"
                    }
                    canvas.drawText("$icon ${finding.title} (${finding.weight} pts)", margin + 10f, y, smallPaint)
                    y += 12f
                }
                if (app.findings.size > 3) {
                    canvas.drawText("   ... y ${app.findings.size - 3} hallazgos más", margin + 10f, y, smallPaint)
                    y += 12f
                }
            }
            y += 8f
            // Permisos peligrosos
            val dangerousCount = app.permissions.count { it.dangerous }
            if (dangerousCount > 0) {
                checkSpace(20f)
                canvas.drawText("Permisos peligrosos: $dangerousCount", margin, y, smallPaint)
                y += 15f
            }
            y += 10f
        }
        // ========== PIE DE PÁGINA FINAL ==========
        checkSpace(80f)
        y += 20f
        canvas.drawLine(margin, y, pageWidth - margin, y, Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f
        })
        y += 20f
        canvas.drawText("RECOMENDACIONES", margin, y, headerPaint)
        y += 20f
        if (malware > 0 || critical > 0) {
            canvas.drawText("⚠️ Se detectaron apps de alto riesgo o con firmas de malware.", margin, y, normalPaint)
            y += 15f
            canvas.drawText("   Contacta con GuardianOS para una auditoría profesional.", margin, y, normalPaint)
            y += 20f
        }
        canvas.drawText("Para más información: https://guardianos.es", margin, y, normalPaint)
        y += 15f
        canvas.drawText("Contacto: info@guardianos.es", margin, y, normalPaint)
        y += 30f
        canvas.drawText("– Informe generado localmente. Sin datos enviados.", margin, y, smallPaint)
        y += 12f
        canvas.drawText("© GuardianOS s.l. | Generado el ${dateFormat.format(Date())}", margin, y, smallPaint)
        pdf.finishPage(page)
        val file = File(context.cacheDir, "guardianos_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }
}
