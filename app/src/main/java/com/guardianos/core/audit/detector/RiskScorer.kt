package com.guardianos.core.audit.detector

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.guardianos.core.domain.model.AppAudit
import com.guardianos.core.domain.model.Risk
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
        
        // Obtener info básica
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val appName = pm.getApplicationLabel(appInfo).toString()
        
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
        val installTime = pm.getPackageInfo(packageName, 0).firstInstallTime
        val calendar = Calendar.getInstance().apply { timeInMillis = installTime }
        val installHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        if (installHour in 0..6) {
            val points = 15
            scoringBreakdown["Instalación nocturna"] = points
            totalScore += points
            behaviorFlags.add("🕐 Instalada de madrugada (${installHour}h)")
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
     * Escaneo completo de todas las apps
     */
    fun scanAllAppsForStalkerware(context: Context): List<StalkerwareRiskReport> {
        val reports = mutableListOf<StalkerwareRiskReport>()
        
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "Iniciando escaneo completo de stalkerware...")
        
        try {
            // 1. Ejecutar los 3 detectores (los más pesados primero para fail-fast)
            Log.d(TAG, "[1/3] Escaneando servicios de accesibilidad...")
            val accessibilityReports = AccessibilityMonitor.scanAccessibilityServices(context)
                .associateBy { it.packageName }
            Log.d(TAG, "  ✓ ${accessibilityReports.size} servicios analizados")
            
            // Liberar memoria antes del siguiente detector
            System.gc()
            Thread.sleep(100)
            
            Log.d(TAG, "[2/3] Detectando apps ocultas...")
            val hiddenAppReports = HiddenAppsDetector.scanHiddenApps(context)
                .associateBy { it.packageName }
            Log.d(TAG, "  ✓ ${hiddenAppReports.size} apps con técnicas de ocultación")
            
            // Liberar memoria antes del siguiente detector
            System.gc()
            Thread.sleep(100)
            
            Log.d(TAG, "[3/3] Analizando servicios en segundo plano...")
            val serviceReports = BackgroundServicesAnalyzer.analyzeBackgroundServices(context)
                .associateBy { it.packageName }
            Log.d(TAG, "  ✓ ${serviceReports.size} servicios persistentes")
            
            // Liberar memoria antes de procesar resultados
            System.gc()
            Thread.sleep(100)
            
            // 2. Combinar todas las apps detectadas
            val allPackages = (accessibilityReports.keys + hiddenAppReports.keys + serviceReports.keys).distinct()
            Log.d(TAG, "Total de apps sospechosas a analizar: ${allPackages.size}")
            
            // 3. Calcular score para cada app (en chunks para prevenir OOM)
            val chunkSize = 20
            val chunks = allPackages.chunked(chunkSize)
            
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                Log.d(TAG, "[Scoring ${chunkIndex + 1}/${chunks.size}] Procesando ${chunk.size} apps...")
                
                for (packageName in chunk) {
                try {
                    val report = calculateStalkerwareRisk(
                        context,
                        packageName,
                        accessibilityReports[packageName],
                        hiddenAppReports[packageName],
                        serviceReports[packageName]
                    )
                    
                    // Solo incluir apps con riesgo >= MEDIUM
                    if (report.riskLevel != StalkerwareRiskLevel.SAFE) {
                        reports.add(report)
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error analizando $packageName", e)
                }
            }
                
                // Liberar memoria entre chunks
                if (chunkIndex < chunks.size - 1) {
                    System.gc()
                    Thread.sleep(50)
                }
            }
            
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "Escaneo completo finalizado")
            Log.d(TAG, "Apps analizadas: ${allPackages.size}")
            Log.d(TAG, "Apps con riesgo detectado: ${reports.size}")
            Log.d(TAG, "  - STALKERWARE CONFIRMADO: ${reports.count { it.riskLevel == StalkerwareRiskLevel.STALKERWARE_CONFIRMED }}")
            Log.d(TAG, "  - SOSPECHA ALTA: ${reports.count { it.riskLevel == StalkerwareRiskLevel.HIGH_SUSPICION }}")
            Log.d(TAG, "  - RIESGO MEDIO: ${reports.count { it.riskLevel == StalkerwareRiskLevel.MEDIUM }}")
            
            return reports.sortedByDescending { it.totalScore }
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError en escaneo stalkerware - dispositivo con demasiadas apps", e)
            throw e  // Re-lanzar para que StalkerwareScreen lo capture
        } catch (e: Exception) {
            Log.e(TAG, "Error fatal en escaneo stalkerware", e)
            throw e
        }
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
    fun enhanceAppAuditWithStalkerwareScore(
        context: Context,
        appAudit: AppAudit
    ): AppAudit {
        try {
            // Obtener reportes de los 3 detectores
            val accessibilityReports = AccessibilityMonitor.scanAccessibilityServices(context)
                .associateBy { it.packageName }
            val hiddenAppReports = HiddenAppsDetector.scanHiddenApps(context)
                .associateBy { it.packageName }
            val serviceReports = BackgroundServicesAnalyzer.analyzeBackgroundServices(context)
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
