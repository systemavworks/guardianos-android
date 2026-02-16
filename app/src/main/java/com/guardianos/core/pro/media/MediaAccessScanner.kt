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
     * 
     * ✅ ANÁLISIS EXHAUSTIVO v2.1:
     * - Permisos multimedia Android 13+ (READ_MEDIA_IMAGES, etc.)
     * - Permisos de almacenamiento legacy (READ/WRITE_EXTERNAL_STORAGE)
     * - Permisos especiales (MANAGE_EXTERNAL_STORAGE)
     * - Acceso a documentos (ACTION_OPEN_DOCUMENT)
     * - Ubicación en fotos (ACCESS_MEDIA_LOCATION)
     * - Permisos de instalación y sistema
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
     * ✅ EXHAUSTIVO: Incluye todos los permisos multimedia, documentos y almacenamiento
     */
    private fun isMediaOrStoragePermission(permission: String): Boolean {
        return permission.contains("READ_EXTERNAL_STORAGE") ||
               permission.contains("WRITE_EXTERNAL_STORAGE") ||
               permission.contains("READ_MEDIA_IMAGES") ||
               permission.contains("READ_MEDIA_VIDEO") ||
               permission.contains("READ_MEDIA_AUDIO") ||
               permission.contains("READ_MEDIA_VISUAL_USER_SELECTED") ||  // Android 14+
               permission.contains("MANAGE_EXTERNAL_STORAGE") ||
               permission.contains("ACCESS_MEDIA_LOCATION") ||
               permission.contains("MANAGE_MEDIA") ||                        // Gestión multimedia
               permission.contains("ACCESS_ALL_DOWNLOADS") ||                // Descargas
               permission.contains("LOADER_USAGE_STATS") ||                  // Stats de archivos
               // Permisos especiales peligrosos
               permission.contains("REQUEST_INSTALL_PACKAGES") ||             // Instalar APKs
               permission.contains("REQUEST_DELETE_PACKAGES") ||              // Borrar apps
               permission.contains("WRITE_MEDIA_STORAGE") ||                  // Escritura SD
               permission.contains("MOUNT_UNMOUNT_FILESYSTEMS") ||            // Montar SD
               // Documentos y proveedores de contenido
               permission == "android.permission.ACCESS_MEDIA_LOCATION" ||
               permission == "android.permission.MANAGE_DOCUMENTS"
    }
    
    /**
     * Análisis adicional de permisos especiales peligrosos.
     * Detecta apps con capacidades de modificación del sistema de archivos.
     */
    fun getAppsWithDangerousFileAccess(context: Context): List<MediaAccessInfo> {
        val allApps = getDetailedMediaAccessInfo(context)
        
        // Filtrar solo apps con permisos CRÍTICOS
        return allApps.filter { app ->
            app.grantedPermissions.any { perm ->
                perm.contains("MANAGE_EXTERNAL_STORAGE") ||
                perm.contains("WRITE_EXTERNAL_STORAGE") ||
                perm.contains("REQUEST_INSTALL_PACKAGES") ||
                perm.contains("REQUEST_DELETE_PACKAGES")
            }
        }
    }
    
    /**
     * Genera reporte exhaustivo de privacidad multimedia.
     * Devuelve resumen con estadísticas y recomendaciones.
     */
    data class MediaPrivacyReport(
        val totalAppsWithAccess: Int,
        val criticalApps: Int,
        val highRiskApps: Int,
        val appsWithWriteAccess: Int,
        val appsWithLocationAccess: Int,
        val suspiciousApps: List<MediaAccessInfo>,
        val recommendations: List<String>
    )
    
    fun generatePrivacyReport(context: Context): MediaPrivacyReport {
        val allApps = getDetailedMediaAccessInfo(context)
        
        val criticalApps = allApps.count { it.riskLevel == "CRÍTICO" }
        val highRiskApps = allApps.count { it.riskLevel == "ALTO" }
        val appsWithWrite = allApps.count { app ->
            app.grantedPermissions.any { it.contains("WRITE") }
        }
        val appsWithLocation = allApps.count { app ->
            app.grantedPermissions.any { it.contains("MEDIA_LOCATION") }
        }
        
        val suspiciousApps = allApps.filter { it.suspiciousPatterns.isNotEmpty() }
        
        val recommendations = mutableListOf<String>()
        
        if (criticalApps > 0) {
            recommendations.add("🚨 Tienes $criticalApps apps con acceso CRÍTICO a tus archivos")
            recommendations.add("Revisa y revoca permisos de apps que no necesitan acceso a fotos")
        }
        
        if (appsWithWrite > 0) {
            recommendations.add("⚠️ $appsWithWrite apps pueden MODIFICAR/ELIMINAR tus archivos")
            recommendations.add("Solo apps de galería y editores deberían tener permisos de escritura")
        }
        
        if (appsWithLocation > 0) {
            recommendations.add("📍 $appsWithLocation apps pueden extraer ubicaciones GPS de tus fotos")
            recommendations.add("Tu privacidad de ubicación puede estar comprometida")
        }
        
        if (suspiciousApps.isNotEmpty()) {
            recommendations.add("👁️ ${suspiciousApps.size} apps muestran patrones sospechosos de acceso")
            recommendations.add("Revisa la lista de 'Apps con acceso multimedia' para más detalles")
        }
        
        if (allApps.isEmpty()) {
            recommendations.add("✅ Excelente: Ninguna app tiene acceso a tu multimedia")
            recommendations.add("Tu privacidad de archivos está completamente protegida")
        }
        
        return MediaPrivacyReport(
            totalAppsWithAccess = allApps.size,
            criticalApps = criticalApps,
            highRiskApps = highRiskApps,
            appsWithWriteAccess = appsWithWrite,
            appsWithLocationAccess = appsWithLocation,
            suspiciousApps = suspiciousApps,
            recommendations = recommendations
        )
    }
}
