package com.guardianos.core.audit

import com.guardianos.core.domain.model.*
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.guardianos.core.data.MalwareDatabase
import com.guardianos.core.audit.detector.StalkerwareDetector
import com.guardianos.core.audit.detector.RiskScorer
import com.guardianos.core.audit.detector.AccessibilityMonitor
import com.guardianos.core.audit.detector.HiddenAppsDetector
import com.guardianos.core.audit.detector.BackgroundServicesAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.math.min

/**
 * Motor de auditoría de seguridad de aplicaciones.
 * Implementa análisis en múltiples capas con optimizaciones de rendimiento.
 */
class AppAuditor(
    private val malwareDatabase: MalwareDatabase = MalwareDatabase()
) {
    private val TAG = "AppAuditor"
    // Caché de hashes de certificados para evitar cálculos repetidos
    private val certificateHashCache = ConcurrentHashMap<String, String>()

    suspend fun auditApps(
        context: Context,
        mode: AuditMode
    ): List<AppAudit> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        Log.d(TAG, "Iniciando auditoría de ${apps.size} aplicaciones en modo $mode")
        val startTime = System.currentTimeMillis()
        
        val results = apps.mapNotNull { app ->
            try {
                val pkg = pm.getPackageInfo(
                    app.packageName,
                    PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
                )
                val permissions = pkg.requestedPermissions?.map {
                    AppPermission(it, isDangerousPermission(it))
                } ?: emptyList()
                val source = detectInstallSource(context, app.packageName)
                val apkPath = context.packageManager.getApplicationInfo(app.packageName, 0).sourceDir

                // 🔒 Excluir overlays y recursos del sistema
                if (isSystemOverlayOrResource(app.packageName, apkPath)) {
                    Log.v(TAG, "Excluyendo overlay/recurso del sistema: ${app.packageName}")
                    return@mapNotNull AppAudit(
                        appName = pm.getApplicationLabel(app).toString(),
                        packageName = app.packageName,
                        versionName = pkg.versionName ?: "N/A",
                        isSystemApp = true,
                        installSource = InstallSource.SYSTEM,
                        permissions = emptyList(),
                        findings = emptyList(),
                        riskScore = 0,
                        risk = Risk.LOW
                    )
                }

                val findings = mutableListOf<AuditFinding>()
                var score = 0

                // 🔴 CAPA 1: HUELLAS ESTÁTICAS
                val signatureCheck = checkMalwareSignatures(pkg, app.packageName)
                findings.addAll(signatureCheck.findings)
                score += signatureCheck.score

                // 📡 CAPA 1B: TRACKERS CONOCIDOS
                val trackerMatch = malwareDatabase.checkTracker(app.packageName)
                if (trackerMatch != null) {
                    findings.add(AuditFinding(
                        "📡 Tracker conocido",
                        "Contiene: ${trackerMatch.name}",
                        trackerMatch.riskScore
                    ))
                    score += trackerMatch.riskScore
                }

                // 🟡 CAPA 2: HEURÍSTICA
                val dangerousPerms = permissions.filter { it.dangerous }
                score += dangerousPerms.size * 12
                findings.addAll(detectSuspiciousPermissionCombinations(permissions))
                findings.addAll(analyzePackageName(app.packageName))
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

                // 🟠 CAPA 3: INTEGRIDAD APK (solo FULL)
                if (mode == AuditMode.FULL) {
                    val integrityCheck = analyzeApkIntegrity(context, pkg, app.packageName)
                    findings.addAll(integrityCheck.findings)
                    score += integrityCheck.score
                }

                // 🔵 CAPA 4: IoC ligero (solo FULL)
                if (mode == AuditMode.FULL) {
                    val iocCheck = checkNetworkIndicators(app.packageName)
                    findings.addAll(iocCheck.findings)
                    score += iocCheck.score
                }

                // 🔴 CAPA 5: STALKERWARE DETECTION 2.0 (solo FULL/PRO)
                // Sistema unificado con AccessibilityMonitor + HiddenAppsDetector + BackgroundServicesAnalyzer
                if (mode == AuditMode.FULL) {
                    try {
                        // Crear instancias de los detectores
                        val accessibilityMonitor = AccessibilityMonitor(context)
                        val hiddenAppsDetector = HiddenAppsDetector(context)
                        val servicesAnalyzer = BackgroundServicesAnalyzer(context)
                        
                        // Obtener reportes de los 3 detectores
                        val accessibilityReports = accessibilityMonitor.scanAccessibilityServices()
                            .associateBy { it.packageName }
                        val hiddenAppReports = hiddenAppsDetector.scanHiddenApps()
                            .associateBy { it.packageName }
                        val serviceReports = servicesAnalyzer.analyzeBackgroundServices()
                            .associateBy { it.packageName }
                        
                        // Calcular score stalkerware para esta app
                        val stalkerwareReport = RiskScorer.calculateStalkerwareRisk(
                            context,
                            app.packageName,
                            accessibilityReports[app.packageName],
                            hiddenAppReports[app.packageName],
                            serviceReports[app.packageName]
                        )
                        
                        // Solo añadir findings si hay riesgo detectado
                        if (stalkerwareReport.riskLevel != RiskScorer.StalkerwareRiskLevel.SAFE) {
                            findings.add(AuditFinding(
                                title = when (stalkerwareReport.riskLevel) {
                                    RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> "🚨 STALKERWARE CONFIRMADO"
                                    RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> "⚠️ SOSPECHA ALTA DE STALKERWARE"
                                    RiskScorer.StalkerwareRiskLevel.MEDIUM -> "⚠️ COMPORTAMIENTO SOSPECHOSO"
                                    else -> "ℹ️ RIESGO BAJO"
                                },
                                description = "Puntuación: ${stalkerwareReport.totalScore}/100\n" +
                                            "Comportamientos detectados:\n${stalkerwareReport.behaviorFlags.joinToString("\n")}\n\n" +
                                            stalkerwareReport.recommendedAction,
                                weight = stalkerwareReport.totalScore
                            ))
                            score += stalkerwareReport.totalScore
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error en detección stalkerware para ${app.packageName}: ${e.message}")
                    }
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
                score = min(score, 100)
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
                Log.w(TAG, "Error auditando ${app.packageName}: ${e.message}")
                null
            }
        }.sortedByDescending { it.riskScore }
        
        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Auditoría completada: ${results.size} apps analizadas en ${duration}ms")
        
        results
    }

    suspend fun auditSystem(context: Context): List<AuditFinding> = withContext(Dispatchers.Default) {
        val list = mutableListOf<AuditFinding>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (!km.isDeviceSecure) {
            list.add(AuditFinding("Bloqueo de pantalla", "No hay bloqueo configurado", 40))
        }
        if (isRooted()) {
            list.add(AuditFinding("Dispositivo rooteado", "El dispositivo tiene acceso root", 60))
        }
        val adbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        if (adbEnabled) {
            list.add(AuditFinding("Depuración USB", "La depuración USB está activada", 25))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            val unknownSources = Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1
            if (unknownSources) {
                list.add(AuditFinding("Fuentes desconocidas", "Instalación desde fuentes desconocidas habilitada", 30))
            }
        }
        try {
            val verifyApps = Settings.Global.getInt(context.contentResolver, "package_verifier_enable", 0)
            if (verifyApps == 0) {
                list.add(AuditFinding("Verificación de apps desactivada", "Google Play Protect o verificación de apps está desactivada", 35))
            }
        } catch (e: Exception) {
            // ignore
        }
        list
    }

    /* ─── FUNCIONES AUXILIARES ─── */

    private fun isSystemOverlayOrResource(packageName: String, apkPath: String?): Boolean {
        // Detectar por nombre de paquete (lista ampliada)
        val systemPrefixes = listOf(
            "android.",
            "com.android.",
            "com.google.android.overlay",
            "com.google.android.ext.",
            "com.qualcomm.",
            "com.qti."
        )
        
        if (systemPrefixes.any { packageName.startsWith(it) }) {
            return true
        }
        
        // Detectar por palabras clave en el nombre
        val systemKeywords = listOf(
            ".overlay",
            "frameworkres",
            "resources",
            "permissioncontroller",
            "connectivity",
            "media.module",
            "wifiresources",
            "cellbroadcast",
            "healthfitness",
            "documentsui",
            "ext.services",
            "captiveportal",
            "networkstack"
        )
        
        if (systemKeywords.any { packageName.contains(it, ignoreCase = true) }) {
            return true
        }

        // Detectar por ruta de APK
        apkPath?.let { path ->
            val systemPaths = listOf(
                "/system/",
                "/product/",
                "/apex/",
                "/vendor/",
                "/system_ext/"
            )
            if (systemPaths.any { path.startsWith(it) }) {
                return true
            }
        }

        return false
    }

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
                // Usar caché para evitar cálculos repetidos
                val signatureKey = signature.toCharsString()
                val certHash = certificateHashCache.getOrPut(signatureKey) {
                    calculateSHA256(signature.toByteArray())
                }
                
                val malwareMatch = malwareDatabase.checkCertificateHash(certHash)
                if (malwareMatch != null) {
                    findings.add(AuditFinding(
                        "🚨 FIRMA DE MALWARE DETECTADA",
                        "Certificado coincide con malware conocido: ${malwareMatch.name}",
                        50
                    ))
                    score += 50
                    Log.w(TAG, "Malware detectado en $packageName: ${malwareMatch.name}")
                }
                
                if (certHash.startsWith("a40da80a") || isDebugCertificate(signature)) {
                    findings.add(AuditFinding(
                        "Certificado de desarrollo",
                        "App firmada con certificado debug en producción",
                        20
                    ))
                    score += 20
                }
            }
            
            val packageMatch = malwareDatabase.checkPackageName(packageName)
            if (packageMatch != null) {
                findings.add(AuditFinding(
                    "🚨 PAQUETE MALICIOSO CONOCIDO",
                    "Paquete identificado como: ${packageMatch.name}",
                    50
                ))
                score += 50
                Log.w(TAG, "Paquete malicioso detectado: $packageName - ${packageMatch.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando firmas de $packageName", e)
        }
        return SecurityCheckResult(findings, score)
    }

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
        if (perms.any { it.contains("CAMERA") } &&
            perms.any { it.contains("RECORD_AUDIO") } &&
            perms.any { it.contains("LOCATION") }
        ) {
            findings.add(AuditFinding("Patrón de vigilancia", "Combinación típica de spyware: cámara + audio + ubicación", 35))
        }
        if (perms.any { it.contains("READ_SMS") } &&
            perms.any { it.contains("READ_CONTACTS") } &&
            perms.any { it.contains("CALL_PHONE") }
        ) {
            findings.add(AuditFinding("Acceso total a comunicaciones", "Control completo sobre SMS, contactos y llamadas", 30))
        }
        if (perms.any { it.contains("WRITE_EXTERNAL") } &&
            perms.any { it.contains("INTERNET") } &&
            perms.any { it.contains("REQUEST_INSTALL") }
        ) {
            findings.add(AuditFinding("Perfil de ransomware", "Puede cifrar archivos, comunicarse externamente e instalar apps", 30))
        }
        if (perms.any { it.contains("SYSTEM_ALERT_WINDOW") } &&
            perms.any { it.contains("READ_SMS") } &&
            perms.any { it.contains("INTERNET") }
        ) {
            findings.add(AuditFinding("Patrón de troyano bancario", "Puede crear overlays, leer SMS (2FA) y enviar datos", 35))
        }
        return findings
    }

    private fun analyzePackageName(packageName: String): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()
        val suspicious = listOf(
            "com.app.test", "com.example", "com.android.test",
            "free.vpn", "free.antivirus", "hack", "crack", "mod",
            "pro.unlock", "premium.free", "cheat"
        )
        if (suspicious.any { packageName.contains(it, ignoreCase = true) }) {
            findings.add(AuditFinding("Nombre de paquete sospechoso", "Sigue patrones comunes en malware/apps pirateadas", 18))
        }
        val parts = packageName.split(".")
        if (parts.any { it.length <= 1 } || parts.any { it.matches(Regex(".*\\d{4,}.*")) }) {
            findings.add(AuditFinding("Estructura anómala de paquete", "Nombre con partes muy cortas o muchos números", 12))
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
            if ((appName.contains(key, ignoreCase = true) || packageName.contains(key, ignoreCase = true)) &&
                packageName != legitPkg
            ) {
                findings.add(AuditFinding("⚠️ Posible suplantación", "Imita a $key (legítimo: $legitPkg)", 40))
            }
        }
        return findings
    }

    private fun detectObfuscationPatterns(packageName: String): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()
        if (packageName.matches(Regex(".*[Il1oO0]{3,}.*"))) {
            findings.add(AuditFinding("Ofuscación de nombre", "Usa caracteres similares para confundir (l, I, 1, o, O, 0)", 15))
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
                    findings.add(AuditFinding("Capacidades de administrador", "Puede obtener privilegios de administrador del dispositivo", 30))
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return findings
    }

    private fun analyzeApkIntegrity(context: Context, pkg: PackageInfo, packageName: String): SecurityCheckResult {
        val findings = mutableListOf<AuditFinding>()
        var score = 0
        try {
            val apkPath = context.packageManager.getApplicationInfo(packageName, 0).sourceDir
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return SecurityCheckResult(findings, score)

            if (apkFile.length() < 150_000) {
                findings.add(AuditFinding("APK sospechosamente pequeño", "Tamaño: ${apkFile.length()} bytes — posible stub o downloader", 20))
                score += 20
            }

            val installTime = pkg.firstInstallTime
            val lastModified = apkFile.lastModified()
            if (lastModified > installTime + 2 * 3600_000) {
                findings.add(AuditFinding("APK modificado tras instalación", "Archivo modificado ${Date(lastModified)} vs instalación ${Date(installTime)}", 25))
                score += 25
            }

            val signatureCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.signingInfo?.apkContentsSigners?.size ?: 1
            } else {
                @Suppress("DEPRECATION")
                pkg.signatures?.size ?: 1
            }
            if (signatureCount > 1) {
                findings.add(AuditFinding("Múltiples firmas en APK", "Detectadas $signatureCount firmas — indica reempaquetado o inyección", 30))
                score += 30
            }

            try {
                ZipFile(apkFile).use { zip ->
                    val entries = zip.entries().toList()
                    val hasDex = entries.any { it.name == "classes.dex" || it.name.startsWith("classes") }
                    val hasManifest = entries.any { it.name == "AndroidManifest.xml" }
                    if (!hasDex) {
                        findings.add(AuditFinding("⚠️ APK sin código ejecutable", "No contiene classes.dex — posible APK vacío o corrompido", 25))
                        score += 25
                    }
                    if (!hasManifest) {
                        findings.add(AuditFinding("⚠️ APK sin manifiesto", "Falta AndroidManifest.xml — estructura inválida", 30))
                        score += 30
                    }
                }
            } catch (e: Exception) {
                findings.add(AuditFinding("Error al leer APK", "No se pudo analizar la estructura ZIP — posible corrupción o protección", 20))
                score += 20
            }
        } catch (e: Exception) {
            // no penalty
        }
        return SecurityCheckResult(findings, score)
    }

    private fun checkNetworkIndicators(packageName: String): SecurityCheckResult {
        val findings = mutableListOf<AuditFinding>()
        var score = 0
        val suspiciousKeywords = listOf("tracker", "analytics", "adservice", "stat", "click", "log")
        if (suspiciousKeywords.any { packageName.contains(it, ignoreCase = true) }) {
            findings.add(AuditFinding("Nombre sugiere tracking", "El nombre del paquete contiene términos asociados a rastreo", 15))
            score += 15
        }
        return SecurityCheckResult(findings, score)
    }

    private fun isRooted(): Boolean = try {
        File("/system/bin/su").exists() ||
        File("/system/xbin/su").exists() ||
        Build.TAGS.contains("test-keys")
    } catch (e: Exception) {
        false
    }

    private fun calculateSHA256(data: ByteArray): String = try {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "error"
    }

    private fun isDebugCertificate(signature: Signature): Boolean = try {
        val certFactory = CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(signature.toByteArray().inputStream()) as X509Certificate
        cert.subjectX500Principal.name.contains("Android Debug", ignoreCase = true)
    } catch (e: Exception) {
        false
    }

    private fun detectInstallSource(context: Context, packageName: String): InstallSource {
        val pm = context.packageManager
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            when {
                installer == null -> InstallSource.UNKNOWN
                installer.contains("com.android.vending") -> InstallSource.PLAY_STORE
                installer.contains("com.amazon.venezia") -> InstallSource.AMAZON
                installer.contains("com.sec.android.app.samsungapps") -> InstallSource.SAMSUNG
                installer.contains("adb") || installer.contains("packageinstaller") -> InstallSource.ADB
                else -> InstallSource.UNKNOWN
            }
        } catch (e: Exception) {
            InstallSource.UNKNOWN
        }
    }
}
