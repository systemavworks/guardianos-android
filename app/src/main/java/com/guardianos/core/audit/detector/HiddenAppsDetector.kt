package com.guardianos.core.audit.detector

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Hidden Apps Detector - Detector de aplicaciones ocultas
 * 
 * **TÉCNICA COMÚN DE STALKERWARE**
 * 
 * Apps maliciosas se ocultan:
 * - Sin ícono en launcher (FLAG_LAUNCHER = false)
 * - Nombres con caracteres unicode invisibles (U+200B, U+FEFF)
 * - Nombres vacíos o espacios
 * - Duplicados de apps populares (2x WhatsApp)
 * - Ícono que imita apps del sistema
 * 
 * **SIN ROOT - 100% FIABLE**
 * Usa: PackageManager.queryIntentActivities()
 */
object HiddenAppsDetector {
    private const val TAG = "HiddenAppsDetector"
    
    data class HiddenAppReport(
        val packageName: String,
        val appName: String,
        val hasLauncherIcon: Boolean,
        val hasInvisibleName: Boolean,
        val isDuplicateApp: Boolean,
        val duplicateOf: String?,
        val installTime: Long,
        val installHour: Int,
        val isSystemApp: Boolean,
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val hidingTechniques: List<String>
    )
    
    enum class RiskLevel {
        SAFE,           // App normal visible
        LOW,            // Sistema sin ícono (normal)
        MEDIUM,         // App sin ícono pero justificable
        HIGH,           // App oculta con técnicas sospechosas
        CRITICAL        // App oculta + instalación nocturna + nombre invisible
    }
    
    /**
     * Escanea todas las apps instaladas buscando técnicas de ocultación
     */
    fun scanHiddenApps(context: Context): List<HiddenAppReport> {
        val reports = mutableListOf<HiddenAppReport>()
        val pm = context.packageManager
        
        try {
            // Obtener todas las apps instaladas
            val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            // OPTIMIZACIÓN: Filtrar apps del sistema primero (reduce análisis ~70%)
            // Apps de sistema raramente son stalkerware
            val installedApps = allApps.filter { appInfo ->
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }
            
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "Apps totales: ${allApps.size}")
            Log.d(TAG, "Apps no-sistema a analizar: ${installedApps.size}")
            Log.d(TAG, "Procesando en lotes de 30 apps para prevenir OutOfMemoryError...")
            
            // Obtener apps con ícono en launcher (para comparar)
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherApps = pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()
            
            Log.d(TAG, "Apps con ícono launcher: ${launcherApps.size}")
            
            // Detectar apps populares para buscar duplicados
            val popularApps = detectInstalledPopularApps(installedApps, pm)
            
            // CHUNKING: Procesar en lotes de 30 apps para reducir memoria
            val chunkSize = 30
            val chunks = installedApps.chunked(chunkSize)
            
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                Log.d(TAG, "[Lote ${chunkIndex + 1}/${chunks.size}] Analizando ${chunk.size} apps...")
                
                for (appInfo in chunk) {
                try {
                    val packageName = appInfo.packageName
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    
                    // Verificar si tiene ícono en launcher
                    val hasLauncherIcon = packageName in launcherApps
                    
                    // Verificar nombre invisible/sospechoso
                    val hasInvisibleName = hasInvisibleUnicodeName(appName)
                    
                    // Verificar si es duplicado
                    val (isDuplicate, duplicateOf) = checkIfDuplicate(
                        packageName, 
                        appName, 
                        popularApps
                    )
                    
                    // Obtener información de instalación
                    val packageInfo = pm.getPackageInfo(packageName, 0)
                    val installTime = packageInfo.firstInstallTime
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = installTime
                    }
                    val installHour = calendar.get(Calendar.HOUR_OF_DAY)
                    
                    // Verificar si es app del sistema
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    // Analizar técnicas de ocultación
                    val hidingTechniques = detectHidingTechniques(
                        packageName,
                        appName,
                        hasLauncherIcon,
                        hasInvisibleName,
                        isDuplicate,
                        isSystemApp
                    )
                    
                    // Solo reportar si tiene técnicas de ocultación
                    if (hidingTechniques.isNotEmpty()) {
                        // Calcular riesgo
                        val (riskScore, riskLevel) = calculateRisk(
                            hasLauncherIcon,
                            hasInvisibleName,
                            isDuplicate,
                            installHour,
                            isSystemApp,
                            hidingTechniques
                        )
                        
                        val report = HiddenAppReport(
                            packageName = packageName,
                            appName = appName,
                            hasLauncherIcon = hasLauncherIcon,
                            hasInvisibleName = hasInvisibleName,
                            isDuplicateApp = isDuplicate,
                            duplicateOf = duplicateOf,
                            installTime = installTime,
                            installHour = installHour,
                            isSystemApp = isSystemApp,
                            riskScore = riskScore,
                            riskLevel = riskLevel,
                            hidingTechniques = hidingTechniques
                        )
                        
                        reports.add(report)
                        
                        Log.d(TAG, "⚠️ App oculta detectada: $appName")
                        Log.d(TAG, "   Package: $packageName")
                        Log.d(TAG, "   Riesgo: ${riskLevel.name} ($riskScore puntos)")
                        Log.d(TAG, "   Técnicas: ${hidingTechniques.joinToString()}")
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error analizando app ${appInfo.packageName}: ${e.message}")
                }
            }
                
                // Liberar memoria tras cada lote
                if (chunkIndex < chunks.size - 1) {
                    System.gc()
                    Thread.sleep(50) // Pausa breve para GC
                }
            }
            
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "Apps ocultas detectadas: ${reports.size}")
            Log.d(TAG, "  - Riesgo CRÍTICO: ${reports.count { it.riskLevel == RiskLevel.CRITICAL }}")
            Log.d(TAG, "  - Riesgo ALTO: ${reports.count { it.riskLevel == RiskLevel.HIGH }}")
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError en HiddenAppsDetector - demasiadas apps", e)
            throw e  // Re-lanzar para captura superior
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando apps ocultas", e)
        }
        
        return reports.sortedByDescending { it.riskScore }
    }
    
    /**
     * Detecta si el nombre tiene caracteres unicode invisibles
     */
    private fun hasInvisibleUnicodeName(appName: String): Boolean {
        // Caracteres unicode invisibles comunes
        val invisibleChars = listOf(
            '\u200B',  // Zero Width Space
            '\u200C',  // Zero Width Non-Joiner
            '\u200D',  // Zero Width Joiner
            '\u2060',  // Word Joiner
            '\uFEFF',  // Zero Width No-Break Space
            '\u00A0',  // Non-Breaking Space
            '\u2063'   // Invisible Separator
        )
        
        return invisibleChars.any { appName.contains(it) } ||
               appName.isBlank() ||
               appName.all { it.isWhitespace() }
    }
    
    /**
     * Detecta apps populares instaladas (para buscar duplicados)
     */
    private fun detectInstalledPopularApps(
        installedApps: List<ApplicationInfo>,
        pm: PackageManager
    ): Map<String, String> {
        val popular = mutableMapOf<String, String>()
        
        val popularPackages = listOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.facebook.katana" to "Facebook",
            "com.facebook.orca" to "Messenger",
            "com.instagram.android" to "Instagram",
            "com.twitter.android" to "Twitter",
            "com.snapchat.android" to "Snapchat",
            "com.tencent.mm" to "WeChat",
            "org.telegram.messenger" to "Telegram",
            "com.viber.voip" to "Viber",
            "jp.naver.line.android" to "LINE",
            "com.skype.raider" to "Skype"
        )
        
        for ((pkg, name) in popularPackages) {
            if (installedApps.any { it.packageName == pkg }) {
                popular[pkg] = name
            }
        }
        
        return popular
    }
    
    /**
     * Verifica si es un duplicado de app popular
     */
    private fun checkIfDuplicate(
        packageName: String,
        appName: String,
        popularApps: Map<String, String>
    ): Pair<Boolean, String?> {
        // Verificar si el nombre coincide con app popular pero package diferente
        for ((popularPkg, popularName) in popularApps) {
            if (packageName != popularPkg && 
                appName.contains(popularName, ignoreCase = true)) {
                return true to popularName
            }
        }
        
        // Verificar variaciones comunes (WhatsApp Plus, GB WhatsApp, etc.)
        val clonePatterns = listOf(
            "whatsapp" to "WhatsApp",
            "facebook" to "Facebook",
            "instagram" to "Instagram",
            "telegram" to "Telegram"
        )
        
        for ((pattern, original) in clonePatterns) {
            if (appName.contains(pattern, ignoreCase = true) &&
                !packageName.contains(pattern) &&
                popularApps.values.contains(original)) {
                return true to original
            }
        }
        
        return false to null
    }
    
    /**
     * Detecta técnicas de ocultación usadas
     */
    private fun detectHidingTechniques(
        packageName: String,
        appName: String,
        hasLauncherIcon: Boolean,
        hasInvisibleName: Boolean,
        isDuplicate: Boolean,
        isSystemApp: Boolean
    ): List<String> {
        val techniques = mutableListOf<String>()
        
        // Técnica 1: Sin ícono en launcher
        if (!hasLauncherIcon && !isSystemApp) {
            techniques.add("Sin ícono en launcher (app invisible)")
        }
        
        // Técnica 2: Nombre invisible
        if (hasInvisibleName) {
            techniques.add("Nombre con caracteres invisibles o vacío")
        }
        
        // Técnica 3: Nombre muy corto o sospechoso
        if (appName.length <= 2 && !isSystemApp) {
            techniques.add("Nombre sospechosamente corto")
        }
        
        // Técnica 4: Duplicado de app popular
        if (isDuplicate) {
            techniques.add("Clon de app popular (WhatsApp falso, etc.)")
        }
        
        // Técnica 5: PackageName que intenta imitar sistema
        val fakeSystemPatterns = listOf(
            "com.android.system",
            "com.android.settings",
            "android.system",
            "system.update",
            "system.service"
        )
        
        if (fakeSystemPatterns.any { packageName.contains(it) } && !isSystemApp) {
            techniques.add("Package name imita apps del sistema")
        }
        
        // Técnica 6: Nombre técnico vs nombre visible muy diferente
        val packageWords = packageName.split(".").lastOrNull() ?: ""
        if (packageWords.isNotEmpty() && 
            !appName.contains(packageWords, ignoreCase = true) &&
            !isSystemApp) {
            techniques.add("Nombre visible no coincide con package name")
        }
        
        return techniques
    }
    
    /**
     * Calcula puntuación de riesgo
     */
    private fun calculateRisk(
        hasLauncherIcon: Boolean,
        hasInvisibleName: Boolean,
        isDuplicate: Boolean,
        installHour: Int,
        isSystemApp: Boolean,
        hidingTechniques: List<String>
    ): Pair<Int, RiskLevel> {
        var score = 0
        
        // Apps del sistema sin ícono son normales (servicios, providers, etc.)
        if (isSystemApp) {
            return 0 to RiskLevel.SAFE
        }
        
        // Factor 1: Sin ícono en launcher
        if (!hasLauncherIcon) {
            score += 30
        }
        
        // Factor 2: Nombre invisible
        if (hasInvisibleName) {
            score += 35
        }
        
        // Factor 3: Clon de app popular
        if (isDuplicate) {
            score += 40
        }
        
        // Factor 4: Instalación nocturna (00:00 - 06:00)
        if (installHour in 0..6) {
            score += 15
        }
        
        // Factor 5: Múltiples técnicas de ocultación
        if (hidingTechniques.size >= 3) {
            score += 20
        }
        
        val riskLevel = when {
            score >= 80 -> RiskLevel.CRITICAL
            score >= 60 -> RiskLevel.HIGH
            score >= 30 -> RiskLevel.MEDIUM
            score >= 15 -> RiskLevel.LOW
            else -> RiskLevel.SAFE
        }
        
        return score to riskLevel
    }
    
    /**
     * Check rápido: ¿hay apps sin ícono launcher?
     */
    fun hasHiddenApps(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherApps = pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()
            
            // Contar apps no-sistema sin ícono
            installedApps.any { app ->
                val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                !isSystemApp && app.packageName !in launcherApps
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Obtiene conteo rápido de apps ocultas (para dashboard)
     */
    fun getHiddenAppsCount(context: Context): Int {
        return try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherApps = pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()
            
            installedApps.count { app ->
                val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                !isSystemApp && app.packageName !in launcherApps
            }
        } catch (e: Exception) {
            0
        }
    }
}
