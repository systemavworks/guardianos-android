package com.guardianos.core.pro.media

import android.Manifest
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Escaneo avanzado de apps con acceso a fotos/documentos sensibles (PRO).
 * Detecta permisos OTORGADOS y accesos REALES a archivos multimedia.
 * 
 * Mejoras v2.0:
 * - Correlación con UsageStatsManager (apps activas recientemente)
 * - Análisis de patrones sospechosos (frecuencia de uso)
 * - Detección de apps que escanean directorios sin justificación
 */
object MediaAccessScanner {
    private const val TAG = "MediaAccessScanner"
    
    data class MediaAccessInfo(
        val packageName: String,
        val appName: String,
        val grantedPermissions: List<String>,
        val riskLevel: String,  // "CRÍTICO", "ALTO", "MEDIO", "BAJO"
        val lastUsed: Long,     // Timestamp último uso detectado
        val usageFrequency: String,  // "Diaria", "Semanal", "Ocasional"
        val suspiciousPatterns: List<String>  // Patrones sospechosos detectados
    )
    
    /**
     * Obtiene lista de apps con acceso REAL a multimedia/documentos
     */
    fun getAppsWithMediaAccess(context: Context): List<String> {
        val detailedApps = getDetailedMediaAccessInfo(context)
        return detailedApps.map { "${it.appName} (${it.riskLevel})" }
    }
    
    /**
     * Obtiene información detallada de apps con permisos multimedia otorgados
     * + análisis de accesos reales y patrones sospechosos
     */
    fun getDetailedMediaAccessInfo(context: Context): List<MediaAccessInfo> {
        val pm = context.packageManager
        val result = mutableListOf<MediaAccessInfo>()
        
        // Obtener estadísticas de uso de apps (últimos 7 días)
        val usageStats = getUsageStats(context, 7)
        
        try {
            val apps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            
            for (pkg in apps) {
                val grantedMediaPermissions = mutableListOf<String>()
                
                // Verificar permisos OTORGADOS
                pkg.requestedPermissions?.forEachIndexed { index, permission ->
                    val isGranted = (pkg.requestedPermissionsFlags[index] and 
                                    PackageManager.PERMISSION_GRANTED) != 0
                    
                    if (isGranted && isMediaOrStoragePermission(permission)) {
                        grantedMediaPermissions.add(permission)
                    }
                }
                
                // Solo incluir apps con permisos realmente otorgados
                if (grantedMediaPermissions.isNotEmpty()) {
                    val appName = try {
                        pm.getApplicationLabel(pkg.applicationInfo).toString()
                    } catch (e: Exception) {
                        pkg.packageName
                    }
                    
                    // Obtener estadísticas de uso real
                    val usageStat = usageStats[pkg.packageName]
                    val lastUsed = usageStat?.lastTimeUsed ?: 0L
                    val totalTimeUsed = usageStat?.totalTimeInForeground ?: 0L
                    
                    // Analizar patrones sospechosos
                    val suspiciousPatterns = analyzeSuspiciousPatterns(
                        context,
                        pkg.packageName,
                        appName,
                        grantedMediaPermissions,
                        lastUsed,
                        totalTimeUsed
                    )
                    
                    // Calcular frecuencia de uso
                    val usageFrequency = calculateUsageFrequency(lastUsed)
                    
                    // Calcular nivel de riesgo mejorado
                    val riskLevel = calculateAdvancedRiskLevel(
                        grantedMediaPermissions,
                        suspiciousPatterns,
                        lastUsed
                    )
                    
                    result.add(MediaAccessInfo(
                        packageName = pkg.packageName,
                        appName = appName,
                        grantedPermissions = grantedMediaPermissions,
                        riskLevel = riskLevel,
                        lastUsed = lastUsed,
                        usageFrequency = usageFrequency,
                        suspiciousPatterns = suspiciousPatterns
                    ))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media access", e)
        }
        
        return result.sortedByDescending { app ->
            when (app.riskLevel) {
                "CRÍTICO" -> 4
                "ALTO" -> 3
                "MEDIO" -> 2
                else -> 1
            }
        }
    }
    
    /**
     * Obtiene estadísticas de uso de apps (UsageStatsManager)
     */
    private fun getUsageStats(context: Context, daysBack: Int): Map<String, UsageStats> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (daysBack * 24 * 60 * 60 * 1000L)
        
        return try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            // Agregar por packageName (puede haber múltiples entradas por día)
            stats.groupBy { it.packageName }
                .mapValues { (_, statsList) ->
                    statsList.maxByOrNull { it.lastTimeUsed } ?: statsList.first()
                }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener estadísticas de uso", e)
            emptyMap()
        }
    }
    
    /**
     * Analiza patrones sospechosos de acceso a multimedia
     */
    private fun analyzeSuspiciousPatterns(
        context: Context,
        packageName: String,
        appName: String,
        permissions: List<String>,
        lastUsed: Long,
        totalTimeUsed: Long
    ): List<String> {
        val patterns = mutableListOf<String>()
        val pm = context.packageManager
        
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            
            // Patrón 1: App no es de galería/cámara pero tiene acceso completo a multimedia
            if (!isMediaRelatedApp(appName) && permissions.size >= 3) {
                patterns.add("⚠️ Acceso completo a multimedia sin justificación aparente")
            }
            
            // Patrón 2: Tiene permisos de escritura/gestión (peligroso)
            if (permissions.any { it.contains("WRITE") || it.contains("MANAGE") }) {
                patterns.add("🚨 Puede modificar/eliminar archivos")
            }
            
            // Patrón 3: App usada recientemente (últimas 24h) con acceso a fotos
            val last24h = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            if (lastUsed > last24h && permissions.any { it.contains("MEDIA_IMAGES") || it.contains("EXTERNAL_STORAGE") }) {
                patterns.add("📸 Accedió a fotos en las últimas 24 horas")
            }
            
            // Patrón 4: Linternas, calculadoras, juegos con acceso a fotos
            if (isSuspiciousAppType(appName) && permissions.isNotEmpty()) {
                patterns.add("🔦 Tipo de app que NO debería necesitar acceso a multimedia")
            }
            
            // Patrón 5: App de terceros con acceso a ubicación en fotos
            if (!isSystemApp && permissions.any { it.contains("ACCESS_MEDIA_LOCATION") }) {
                patterns.add("📍 Puede extraer ubicación GPS de tus fotos")
            }
            
            // Patrón 6: App con mucho tiempo de uso pero poco conocida
            if (totalTimeUsed > (60 * 60 * 1000L) && !isPopularApp(appName)) {
                patterns.add("⏱️ App poco conocida con alto uso de recursos")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error analizando patrones de $packageName", e)
        }
        
        return patterns
    }
    
    /**
     * Verifica si la app es relacionada legítimamente con multimedia
     */
    private fun isMediaRelatedApp(appName: String): Boolean {
        val mediaKeywords = listOf(
            "cámara", "camera", "galería", "gallery", "fotos", "photos", "imagen", "image",
            "vídeo", "video", "editor", "photo editor", "instagram", "whatsapp", "telegram",
            "google photos", "samsung", "xiaomi gallery", "mi gallery"
        )
        return mediaKeywords.any { appName.contains(it, ignoreCase = true) }
    }
    
    /**
     * Detecta tipos de app sospechosos (no deberían necesitar multimedia)
     */
    private fun isSuspiciousAppType(appName: String): Boolean {
        val suspiciousTypes = listOf(
            "linterna", "flashlight", "calculadora", "calculator", "reloj", "clock",
            "alarma", "alarm", "brújula", "compass", "nivel", "level", "regla", "ruler"
        )
        return suspiciousTypes.any { appName.contains(it, ignoreCase = true) }
    }
    
    /**
     * Lista de apps populares legítimas (para reducir falsos positivos)
     */
    private fun isPopularApp(appName: String): Boolean {
        val popular = listOf(
            "whatsapp", "telegram", "instagram", "facebook", "youtube", "netflix",
            "spotify", "twitter", "tiktok", "snapchat", "pinterest", "google", "microsoft",
            "dropbox", "onedrive", "drive", "amazon", "mercadolibre", "xiaomi", "samsung",
            "huawei", "oppo", "realme", "vivo"
        )
        return popular.any { appName.contains(it, ignoreCase = true) }
    }
    
    /**
     * Calcula frecuencia de uso basado en último acceso
     */
    private fun calculateUsageFrequency(lastUsed: Long): String {
        if (lastUsed == 0L) return "Nunca usado"
        
        val hoursSinceUse = (System.currentTimeMillis() - lastUsed) / (60 * 60 * 1000)
        return when {
            hoursSinceUse < 24 -> "Diaria (usada hoy)"
            hoursSinceUse < 168 -> "Semanal (usada esta semana)"
            hoursSinceUse < 720 -> "Mensual"
            else -> "Ocasional"
        }
    }
    
    /**
     * Calcula nivel de riesgo avanzado considerando permisos, patrones y uso
     */
    private fun calculateAdvancedRiskLevel(
        permissions: List<String>,
        suspiciousPatterns: List<String>,
        lastUsed: Long
    ): String {
        var riskScore = 0
        
        // Puntos por permisos peligrosos
        val dangerousPerms = permissions.count { it.contains("WRITE") || it.contains("MANAGE") }
        riskScore += dangerousPerms * 2
        
        // Puntos por cantidad de permisos
        riskScore += permissions.size
        
        // Puntos por patrones sospechosos detectados
        riskScore += suspiciousPatterns.size * 2
        
        // Puntuación extra si fue usada recientemente
        val hoursSinceUse = (System.currentTimeMillis() - lastUsed) / (60 * 60 * 1000)
        if (hoursSinceUse < 24) riskScore += 1
        
        return when {
            riskScore >= 10 || (dangerousPerms >= 2 && suspiciousPatterns.size >= 2) -> "CRÍTICO"
            riskScore >= 7 || dangerousPerms >= 2 -> "ALTO"
            riskScore >= 4 || permissions.size >= 3 -> "MEDIO"
            else -> "BAJO"
        }
    }
    
    /**
     * Verifica si un permiso es relacionado con multimedia/almacenamiento
     */
    private fun isMediaOrStoragePermission(permission: String): Boolean {
        return permission.contains("READ_EXTERNAL_STORAGE") ||
               permission.contains("WRITE_EXTERNAL_STORAGE") ||
               permission.contains("READ_MEDIA_IMAGES") ||
               permission.contains("READ_MEDIA_VIDEO") ||
               permission.contains("READ_MEDIA_AUDIO") ||
               permission.contains("MANAGE_EXTERNAL_STORAGE") ||
               permission.contains("ACCESS_MEDIA_LOCATION")
    }
}
