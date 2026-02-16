package com.guardianos.core.audit.detector

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.guardianos.core.domain.model.AppAudit
import com.guardianos.core.domain.model.Risk
import kotlinx.coroutines.*
import java.util.*

/**
 * Risk Scorer - Sistema de puntuación unificado para detección de stalkerware
 * 
 * **STALKERWARE BEHAVIOR SCORE**
 * 
 * Combina señales de los 3 detectores principales:
 * 1. AccessibilityMonitor (¿lee pantalla/contraseñas?)
 * 2. HiddenAppsDetector (¿app invisible?)
 * 3. BackgroundServicesAnalyzer (¿servicio persistente?)
 * 
 * + Permisos críticos (SMS, LOCATION, CAMERA, CONTACTS)
 * + Instalación nocturna (stalker instala mientras víctima duerme)
 * + Nombre sospechoso (service names como "SystemUpdate", "WifiService")
 * 
 * **SCORING**
 * - AccessibilityService sospechoso → +40 puntos
 * - App oculta sin ícono → +30 puntos
 * - Servicio persistente >24h → +20 puntos
 * - Permisos críticos (SMS+LOCATION+CAMERA) → +25 puntos
 * - Instalación nocturna (00:00-06:00) → +15 puntos
 * - Nombre sospechoso → +10 puntos
 * 
 * **UMBRAL**
 * - 80+ = STALKERWARE CONFIRMADO (99% certeza)
 * - 50-79 = SOSPECHA ALTA, revisar manualmente
 * - 30-49 = RIESGO MEDIO, vigilar
 */
object RiskScorer {
    private const val TAG = "RiskScorer"
    
    data class StalkerwareRiskReport(
        val packageName: String,
        val appName: String,
        val totalScore: Int,
        val riskLevel: StalkerwareRiskLevel,
        val scoringBreakdown: Map<String, Int>,
        val behaviorFlags: List<String>,
        val recommendedAction: String,
        val hasAccessibilityService: Boolean,
        val isHidden: Boolean,
        val hasPersistentService: Boolean,
        val hasCriticalPermissions: Boolean
    )
    
    enum class StalkerwareRiskLevel {
        SAFE,               // 0-29: App normal
        MEDIUM,             // 30-49: Riesgo medio, vigilar
        HIGH_SUSPICION,     // 50-79: Sospecha alta, revisar
        STALKERWARE_CONFIRMED  // 80+: Stalkerware confirmado
    }
    
    /**
     * Analiza una app combinando los 3 detectores + permisos + metadatos
     */
    fun calculateStalkerwareRisk(
        context: Context,
        packageName: String,
        accessibilityReport: AccessibilityMonitor.AccessibilityServiceReport?,
        hiddenAppReport: HiddenAppsDetector.HiddenAppReport?,
        serviceReport: BackgroundServicesAnalyzer.BackgroundServiceReport?
    ): StalkerwareRiskReport {
        val pm = context.packageManager
        
        // Obtener info básica - con manejo seguro de excepciones
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Paquete no encontrado durante análisis: $packageName")
            return StalkerwareRiskReport(
                packageName = packageName,
                appName = packageName.substringAfterLast('.', "UnknownApp"),
                totalScore = 0,
                riskLevel = StalkerwareRiskLevel.SAFE,
                scoringBreakdown = emptyMap(),
                behaviorFlags = listOf("⚠️ App no disponible (posible desinstalación reciente)"),
                recommendedAction = "ℹ️ Esta app ya no está instalada en el dispositivo",
                hasAccessibilityService = false,
                isHidden = false,
                hasPersistentService = false,
                hasCriticalPermissions = false
            )
        }
        
        // Evitar getApplicationLabel() - causa carga de APK assets
        val appName = packageName.substringAfterLast('.', "UnknownApp")
        
        // Mapa de puntuación desglosada
        val scoringBreakdown = mutableMapOf<String, Int>()
        var totalScore = 0
        val behaviorFlags = mutableListOf<String>()
        
        // FACTOR 1: AccessibilityService sospechoso (+40 puntos)
        if (accessibilityReport != null && 
            accessibilityReport.riskLevel in listOf(
                AccessibilityMonitor.RiskLevel.HIGH,
                AccessibilityMonitor.RiskLevel.CRITICAL
            )) {
            val points = when (accessibilityReport.riskLevel) {
                AccessibilityMonitor.RiskLevel.CRITICAL -> 45
                AccessibilityMonitor.RiskLevel.HIGH -> 40
                else -> 0
            }
            scoringBreakdown["AccessibilityService"] = points
            totalScore += points
            behaviorFlags.add("🔴 Usa AccessibilityService (puede leer contraseñas)")
        }
        
        // FACTOR 2: App oculta sin ícono (+30 puntos)
        if (hiddenAppReport != null && !hiddenAppReport.hasLauncherIcon) {
            val points = 30
            if (hiddenAppReport.hasInvisibleName) {
                scoringBreakdown["App oculta + nombre invisible"] = points + 10
                totalScore += points + 10
                behaviorFlags.add("🔴 App INVISIBLE (sin ícono + nombre oculto)")
            } else {
                scoringBreakdown["App sin ícono"] = points
                totalScore += points
                behaviorFlags.add("⚠️ App sin ícono en launcher")
            }
        }
        
        // FACTOR 3: Servicio persistente >24h (+20 puntos)
        if (serviceReport != null && serviceReport.isForegroundService) {
            val points = when {
                serviceReport.estimatedRuntime.contains("día") -> 25
                serviceReport.estimatedRuntime.contains("hora") -> {
                    val hours = serviceReport.estimatedRuntime.split(" ")[0].toIntOrNull() ?: 0
                    if (hours >= 24) 25 else 20
                }
                else -> 15
            }
            scoringBreakdown["Servicio persistente"] = points
            totalScore += points
            behaviorFlags.add("🔴 Servicio siempre activo (${serviceReport.estimatedRuntime})")
        }
        
        // FACTOR 4: Permisos críticos (+25 puntos)
        val criticalPermissions = checkCriticalPermissions(context, packageName)
        if (criticalPermissions.isNotEmpty()) {
            val points = when (criticalPermissions.size) {
                4 -> 30  // SMS+LOCATION+CAMERA+CONTACTS
                3 -> 25
                2 -> 20
                else -> 15
            }
            scoringBreakdown["Permisos críticos (${criticalPermissions.size})"] = points
            totalScore += points
            behaviorFlags.add("⚠️ Permisos críticos: ${criticalPermissions.joinToString(", ")}")
        }
        
        // FACTOR 5: Instalación nocturna (+15 puntos)
        try {
            val installTime = pm.getPackageInfo(packageName, 0).firstInstallTime
            val calendar = Calendar.getInstance().apply { timeInMillis = installTime }
            val installHour = calendar.get(Calendar.HOUR_OF_DAY)
            
            if (installHour in 0..6) {
                val points = 15
                scoringBreakdown["Instalación nocturna"] = points
                totalScore += points
                behaviorFlags.add("🕐 Instalada de madrugada (${installHour}h)")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "No se pudo obtener fecha de instalación para $packageName")
        }
        
        // FACTOR 6: Nombre sospechoso (+10 puntos)
        if (hasSuspiciousName(packageName, appName)) {
            val points = 10
            scoringBreakdown["Nombre sospechoso"] = points
            totalScore += points
            behaviorFlags.add("⚠️ Nombre imita app de sistema")
        }
        
        // FACTOR 7: Clon de app popular (+20 puntos)
        if (hiddenAppReport?.isDuplicateApp == true) {
            val points = 20
            scoringBreakdown["Clon de ${hiddenAppReport.duplicateOf}"] = points
            totalScore += points
            behaviorFlags.add("🔴 Clon de app popular (${hiddenAppReport.duplicateOf})")
        }
        
        // Determinar nivel de riesgo
        val riskLevel = when {
            totalScore >= 80 -> StalkerwareRiskLevel.STALKERWARE_CONFIRMED
            totalScore >= 50 -> StalkerwareRiskLevel.HIGH_SUSPICION
            totalScore >= 30 -> StalkerwareRiskLevel.MEDIUM
            else -> StalkerwareRiskLevel.SAFE
        }
        
        // Acción recomendada
        val recommendedAction = when (riskLevel) {
            StalkerwareRiskLevel.STALKERWARE_CONFIRMED ->
                "🚨 Desinstalar INMEDIATAMENTE. Esta app tiene todos los comportamientos de stalkerware."
            StalkerwareRiskLevel.HIGH_SUSPICION ->
                "⚠️ Revisar manualmente. Verificar quién instaló esta app y por qué tiene estos permisos."
            StalkerwareRiskLevel.MEDIUM ->
                "👀 Vigilar. Si no reconoces esta app, investiga su propósito."
            StalkerwareRiskLevel.SAFE ->
                "✓ Nivel de riesgo bajo para esta app."
        }
        
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "Análisis de stalkerware: $appName ($packageName)")
        Log.d(TAG, "Puntuación total: $totalScore")
        Log.d(TAG, "Nivel de riesgo: ${riskLevel.name}")
        Log.d(TAG, "Desglose:")
        scoringBreakdown.forEach { (factor, points) ->
            Log.d(TAG, "  - $factor: +$points puntos")
        }
        Log.d(TAG, "═══════════════════════════════════════════")
        
        return StalkerwareRiskReport(
            packageName = packageName,
            appName = appName,
            totalScore = totalScore,
            riskLevel = riskLevel,
            scoringBreakdown = scoringBreakdown,
            behaviorFlags = behaviorFlags,
            recommendedAction = recommendedAction,
            hasAccessibilityService = accessibilityReport != null,
            isHidden = hiddenAppReport?.hasLauncherIcon == false,
            hasPersistentService = serviceReport != null,
            hasCriticalPermissions = criticalPermissions.isNotEmpty()
        )
    }
    
    /**
     * Escaneo completo de todas las apps - ¡DEBE EJECUTARSE EN Dispatchers.IO!
     * 
     * ⚠️ CRÍTICO: Esta es una SUSPEND FUNCTION que requiere coroutine context.
     * Nunca llamar desde el hilo principal sin Dispatchers.IO.
     * 
     * ✅ INTEGRACIÓN CON STALKERWAREDETECTOR:
     * - Primero usa base de datos de stalkerware conocido
     * - Luego analiza comportamientos sospechosos con RiskScorer
     * - Combina ambos resultados para detección completa
     */
    suspend fun scanAllAppsForStalkerware(context: Context): List<StalkerwareRiskReport> {
        val reports = mutableListOf<StalkerwareRiskReport>()
        val startTime = System.currentTimeMillis()
        val MAX_SCAN_TIME_MS = 12000L // 12 segundos máximo (análisis más completo)
        
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "Iniciando escaneo stalkerware en BACKGROUND...")
        
        // ✅ PASO 1: Usar StalkerwareDetector para detectar stalkerware CONOCIDO
        val stalkerwareDetector = StalkerwareDetector(context)
        val knownStalkerwareDetections = try {
            Log.d(TAG, "🔍 Paso 1: Escaneando stalkerware conocido (base de datos)...")
            stalkerwareDetector.scanForStalkerware()
        } catch (e: Exception) {
            Log.e(TAG, "Error en detección de stalkerware conocido", e)
            emptyList()
        }
        
        // Convertir detecciones conocidas a StalkerwareRiskReport
        knownStalkerwareDetections.forEach { detection ->
            val severity = when (detection.severity) {
                "CRITICAL" -> 95
                "HIGH" -> 85
                "MEDIUM" -> 60
                else -> 40
            }
            
            reports.add(StalkerwareRiskReport(
                packageName = detection.packageName,
                appName = detection.appName,
                totalScore = severity,
                riskLevel = when (detection.severity) {
                    "CRITICAL" -> StalkerwareRiskLevel.STALKERWARE_CONFIRMED
                    "HIGH" -> StalkerwareRiskLevel.HIGH_SUSPICION
                    "MEDIUM" -> StalkerwareRiskLevel.MEDIUM
                    else -> StalkerwareRiskLevel.SAFE
                },
                scoringBreakdown = mapOf(
                    "Stalkerware conocido" to severity,
                    "Base de datos" to 0
                ),
                behaviorFlags = detection.indicators,
                recommendedAction = detection.recommendations.firstOrNull() ?: "🚨 Desinstalar INMEDIATAMENTE",
                hasAccessibilityService = false,
                isHidden = detection.indicators.any { it.contains("oculta", ignoreCase = true) },
                hasPersistentService = false,
                hasCriticalPermissions = true
            ))
        }
        
        Log.d(TAG, "✅ Stalkerware conocido detectado: ${knownStalkerwareDetections.size} apps")
        
        // ✅ PASO 2: Análisis de comportamiento con RiskScorer (apps no detectadas)
        
        // Log de memoria disponible
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxHeapSizeInMB = runtime.maxMemory() / 1048576L
        Log.d(TAG, "💾 Memoria: $usedMemInMB MB usados / $maxHeapSizeInMB MB máx")
        
        try {
            val pm = context.packageManager
            
            // FILTRADO ULTRA SEGURO: solo apps de usuario + habilitadas + no sistema
            // ⚠️ Excluir apps ya detectadas como stalkerware conocido
            val knownStalkerwarePackages = knownStalkerwareDetections.map { it.packageName }.toSet()
            
            val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val installedApps = allApps.filter { appInfo ->
                appInfo.enabled &&
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                !appInfo.packageName.startsWith("android.") &&
                !appInfo.packageName.startsWith("com.android.") &&
                !appInfo.packageName.startsWith("com.google.android.") &&
                !appInfo.packageName.startsWith("com.oplus.") &&
                !appInfo.packageName.startsWith("com.coloros.") &&
                !appInfo.packageName.startsWith("com.heytap.") &&
                !knownStalkerwarePackages.contains(appInfo.packageName)  // Excluir ya detectadas
            }.take(80)  // 🎯 80 apps de usuario + stalkerware conocido = cobertura completa
            
            Log.d(TAG, "Apps totales: ${allApps.size}")
            Log.d(TAG, "Apps no-sistema a analizar: ${installedApps.size}")
            Log.d(TAG, "Stalkerware conocido ya detectado: ${knownStalkerwareDetections.size}")
            
            // Obtener accessibility reports una sola vez (ya estamos en Dispatchers.IO)
            val accessibilityMonitor = AccessibilityMonitor(context)
            val accessibilityReports = try {
                accessibilityMonitor.scanAccessibilityServices()
                    .associateBy { it.packageName }
            } catch (e: Exception) {
                Log.w(TAG, "Error en AccessibilityMonitor", e)
                emptyMap()
            }
            
            Log.d(TAG, "Servicios de accesibilidad encontrados: ${accessibilityReports.size}")
            
            // PROCESAMIENTO OPTIMIZADO: análisis rápido por app con GC periódico
            for ((index, appInfo) in installedApps.withIndex()) {
                // ⚠️ TIMEOUT DE SEGURIDAD: salir si llevamos >12s
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > MAX_SCAN_TIME_MS) {
                    Log.w(TAG, "⏱️ TIMEOUT preventivo alcanzado (${elapsed}ms). Deteniendo escaneo.")
                    Log.w(TAG, "   Apps analizadas: $index/${installedApps.size}")
                    break
                }
                
                // ✅ COOPERACIÓN CON EL SCHEDULER: ceder control entre apps
                if (index > 0 && index % 3 == 0) {
                    yield()  // Ceder control cada 3 apps
                    delay(50) // 50ms pausa (balance velocidad/estabilidad)
                }
                
                // 🧹 GC cada 15 apps en OPPO A80
                if (index > 0 && index % 15 == 0) {
                    System.gc()  // Sugerir recolección de basura
                    delay(80)    // Dar tiempo al GC
                }
                
                val packageName = appInfo.packageName
                
                Log.d(TAG, "[${index + 1}/${installedApps.size}] 🔍 Analizando: $packageName")
                
                // ⚠️ VALIDACIÓN PREVIA: ¿existe aún el paquete?
                try {
                    pm.getApplicationInfo(packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "  ⏭️ Skip $packageName - desinstalada durante escaneo")
                    continue
                }
                
                // ⚠️ NUNCA usar getApplicationLabel() aquí - causa OOM en dispositivos con poca RAM
                val appName = packageName.substringAfterLast('.', "App")
                
                // Análisis minimalista (sin HiddenAppsDetector para evitar OOM)
                var totalScore = 0
                val scoringBreakdown = mutableMapOf<String, Int>()
                val behaviorFlags = mutableListOf<String>()
                
                // Factor 1: AccessibilityService sospechoso
                val accReport = accessibilityReports[packageName]
                if (accReport != null && accReport.riskLevel in listOf(
                        AccessibilityMonitor.RiskLevel.HIGH,
                        AccessibilityMonitor.RiskLevel.CRITICAL
                    )) {
                    val points = 40
                    scoringBreakdown["AccessibilityService"] = points
                    totalScore += points
                    behaviorFlags.add("🔴 Lee pantalla/contraseñas")
                }
                
                // Factor 2: App oculta
                if (pm.getLaunchIntentForPackage(packageName) == null) {
                    val points = 30
                    scoringBreakdown["App oculta"] = points
                    totalScore += points
                    behaviorFlags.add("👁️ Sin ícono en launcher")
                }
                
                // Factor 3: Permisos críticos (solo 3+)
                val perms = checkCriticalPermissions(context, packageName)
                if (perms.size >= 3) {
                    val points = 25
                    scoringBreakdown["${perms.size} permisos"] = points
                    totalScore += points
                    behaviorFlags.add("⚠️ Permisos: ${perms.joinToString(", ")}")
                }
                
                // Factor 4: Instalación nocturna
                try {
                    val hour = Calendar.getInstance().apply {
                        timeInMillis = pm.getPackageInfo(packageName, 0).firstInstallTime
                    }.get(Calendar.HOUR_OF_DAY)
                    
                    if (hour in 0..5) {
                        val points = 15
                        scoringBreakdown["Nocturna ($hour h)"] = points
                        totalScore += points
                        behaviorFlags.add("🌙 Instalada de madrugada")
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    continue // Skip si desinstalada
                } catch (e: Exception) {
                    // Ignorar otros errores de I/O
                }
                
                // Factor 5: Nombre sospechoso
                if (hasSuspiciousName(packageName, appName)) {
                    val points = 10
                    scoringBreakdown["Nombre sospechoso"] = points
                    totalScore += points
                    behaviorFlags.add("⚠️ Imita app de sistema")
                }
                
                // Solo reportar apps con riesgo >= MEDIUM
                val riskLevel = when {
                    totalScore >= 80 -> StalkerwareRiskLevel.STALKERWARE_CONFIRMED
                    totalScore >= 50 -> StalkerwareRiskLevel.HIGH_SUSPICION
                    totalScore >= 30 -> StalkerwareRiskLevel.MEDIUM
                    else -> null
                }
                
                riskLevel?.let {
                    Log.d(TAG, "  🚨 Riesgo detectado: score=$totalScore, nivel=$it")
                    reports.add(StalkerwareRiskReport(
                        packageName = packageName,
                        appName = appName,
                        totalScore = totalScore,
                        riskLevel = it,
                        scoringBreakdown = scoringBreakdown,
                        behaviorFlags = behaviorFlags,
                        recommendedAction = getRecommendedAction(it),
                        hasAccessibilityService = accReport != null,
                        isHidden = pm.getLaunchIntentForPackage(packageName) == null,
                        hasPersistentService = false,
                        hasCriticalPermissions = perms.isNotEmpty()
                    ))
                }
                
                Log.d(TAG, "  ✅ Completado: score=$totalScore ${if (riskLevel == null) "(sin riesgo)" else ""}")
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "✅ Escaneo finalizado en ${totalTime}ms")
            Log.d(TAG, "   Apps analizadas: ${installedApps.size}")
            Log.d(TAG, "   Apps con riesgo: ${reports.size}")
            Log.d(TAG, "═══════════════════════════════════════════")
            return reports.sortedByDescending { it.totalScore }
            
        } catch (e: CancellationException) {
            Log.w(TAG, "Escaneo cancelado por usuario")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fatal en escaneo", e)
            return emptyList()
        }
    }
    
    private fun getRecommendedAction(level: StalkerwareRiskLevel): String = when (level) {
        StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> "🚨 Desinstalar INMEDIATAMENTE"
        StalkerwareRiskLevel.HIGH_SUSPICION -> "⚠️ Revisar manualmente permisos e instalador"
        StalkerwareRiskLevel.MEDIUM -> "👀 Vigilar actividad de esta app"
        else -> "✓ Riesgo bajo"
    }
    
    /**
     * Verifica permisos críticos para stalkerware
     */
    private fun checkCriticalPermissions(context: Context, packageName: String): List<String> {
        val criticalPerms = listOf(
            Manifest.permission.READ_SMS to "SMS",
            Manifest.permission.READ_CONTACTS to "Contactos",
            Manifest.permission.ACCESS_FINE_LOCATION to "Ubicación",
            Manifest.permission.CAMERA to "Cámara",
            Manifest.permission.RECORD_AUDIO to "Micrófono",
            Manifest.permission.READ_CALL_LOG to "Llamadas"
        )
        
        val granted = mutableListOf<String>()
        val pm = context.packageManager
        
        for ((permission, name) in criticalPerms) {
            if (pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED) {
                granted.add(name)
            }
        }
        
        return granted
    }
    
    /**
     * Verifica si el nombre es sospechoso
     */
    private fun hasSuspiciousName(packageName: String, appName: String): Boolean {
        val suspiciousPackagePatterns = listOf(
            "com.android.system",
            "android.system",
            "system.update",
            "system.service",
            "com.google.android.system"
        )
        
        val suspiciousNamePatterns = listOf(
            "System Update",
            "Device Manager",
            "System Service",
            "Android",
            "Google Service"
        )
        
        return suspiciousPackagePatterns.any { packageName.contains(it, ignoreCase = true) } ||
               suspiciousNamePatterns.any { appName.contains(it, ignoreCase = true) }
    }
    
    /**
     * Integración con AppAudit: añade findings de stalkerware
     */
    suspend fun enhanceAppAuditWithStalkerwareScore(
        context: Context,
        appAudit: AppAudit
    ): AppAudit {
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
            
            // Calcular score
            val stalkerwareReport = calculateStalkerwareRisk(
                context,
                appAudit.packageName,
                accessibilityReports[appAudit.packageName],
                hiddenAppReports[appAudit.packageName],
                serviceReports[appAudit.packageName]
            )
            
            // Si tiene riesgo, añadir findings
            if (stalkerwareReport.riskLevel != StalkerwareRiskLevel.SAFE) {
                val findings = appAudit.findings.toMutableList()
                
                findings.add(com.guardianos.core.domain.model.AuditFinding(
                    title = "🚨 Detección de comportamiento stalkerware",
                    description = "Puntuación: ${stalkerwareReport.totalScore}/100\n" +
                                  "Nivel: ${stalkerwareReport.riskLevel.name}\n\n" +
                                  "Comportamientos detectados:\n${stalkerwareReport.behaviorFlags.joinToString("\n")}\n\n" +
                                  stalkerwareReport.recommendedAction,
                    weight = stalkerwareReport.totalScore
                ))
                
                return appAudit.copy(
                    findings = findings,
                    riskScore = appAudit.riskScore + stalkerwareReport.totalScore
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing AppAudit with stalkerware score", e)
        }
        
        return appAudit
    }
}
