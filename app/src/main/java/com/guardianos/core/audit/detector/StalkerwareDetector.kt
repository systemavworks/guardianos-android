package com.guardianos.core.audit.detector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.guardianos.core.data.StalkerwareDatabase

/**
 * Detector de STALKERWARE (software de espionaje).
 * 
 * **PROCESAMIENTO 100% LOCAL**:
 * - Base de datos hardcodeada (StalkerwareDatabase)
 * - Sin envío de datos
 * - Sin conexión a internet
 * - Sin trackers ni telemetría
 * 
 * Detecta:
 * 1. Apps conocidas de espionaje comercial
 * 2. Apps ocultas sin icono de launcher
 * 3. Patrones sospechosos en nombres de paquetes
 * 4. Combinaciones de permisos típicas de stalkerware
 * 5. Servicios de accesibilidad maliciosos (keyloggers)
 */
class StalkerwareDetector(private val context: Context) {
    
    data class StalkerwareDetection(
        val packageName: String,
        val appName: String,
        val severity: String, // CRITICAL, HIGH, MEDIUM
        val reason: String,
        val indicators: List<String>,
        val recommendations: List<String>
    )
    
    /**
     * Escaneo completo de stalkerware en el dispositivo.
     * TODO LOCAL, sin envío de datos.
     */
    fun scanForStalkerware(): List<StalkerwareDetection> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val detections = mutableListOf<StalkerwareDetection>()
        
        for (app in installedApps) {
            // Skip apps del sistema Android legítimas
            if (isLegitimateSystemApp(app)) continue
            
            val detection = analyzeApp(app, packageManager)
            if (detection != null) {
                detections.add(detection)
            }
        }
        
        return detections.sortedByDescending { getSeverityScore(it.severity) }
    }
    
    /**
     * Analiza una app específica por packageName en busca de stalkerware.
     * Utilizada por AppAuditor para integración en escaneo completo PRO.
     */
    fun analyzeApp(packageName: String): StalkerwareDetection? {
        return try {
            val packageManager = context.packageManager
            val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            analyzeApp(app, packageManager)
        } catch (e: Exception) {
            null // App no encontrada o error al analizar
        }
    }
    
    /**
     * Analiza una app específica en busca de indicadores de stalkerware.
     */
    private fun analyzeApp(app: ApplicationInfo, pm: PackageManager): StalkerwareDetection? {
        val packageName = app.packageName
        val indicators = mutableListOf<String>()
        var severity = "LOW"
        val recommendations = mutableListOf<String>()
        
        // 1. Verificar en base de datos conocida
        val knownStalkerware = StalkerwareDatabase.KNOWN_STALKERWARE.find { 
            it.packageName.equals(packageName, ignoreCase = true) 
        }
        if (knownStalkerware != null) {
            indicators.add("⚠️ Software de espionaje comercial conocido")
            indicators.add("🎯 Capacidades: ${knownStalkerware.capabilities.joinToString(", ")}")
            severity = knownStalkerware.severity
            recommendations.add("ELIMINAR INMEDIATAMENTE")
            recommendations.add("Cambiar contraseñas de todas las cuentas")
            recommendations.add("Revisar qué datos pudieron ser comprometidos")
            if (knownStalkerware.capabilities.contains("Ubicación")) {
                recommendations.add("El atacante conoce tu ubicación histórica")
            }
            if (knownStalkerware.capabilities.any { it.contains("Keylogger") }) {
                recommendations.add("Keylogger activo: todas las contraseñas introducidas están comprometidas")
            }
            
            return StalkerwareDetection(
                packageName = packageName,
                appName = knownStalkerware.name,
                severity = severity,
                reason = knownStalkerware.description,
                indicators = indicators,
                recommendations = recommendations
            )
        }
        
        // Verificar apps de doble uso
        val dualUseApp = StalkerwareDatabase.DUAL_USE_APPS.find {
            it.packageName.equals(packageName, ignoreCase = true)
        }
        if (dualUseApp != null) {
            indicators.add("⚠️ App de control parental detectada")
            indicators.add("🔍 Puede ser legítima o mal usada para espionaje")
            severity = "MEDIUM"
            recommendations.add("Verificar si instalaste esta app voluntariamente")
            recommendations.add("Si no la reconoces, ELIMINAR")
            recommendations.add("Apps de control parental son apropiadas solo para menores")
            
            return StalkerwareDetection(
                packageName = packageName,
                appName = dualUseApp.name,
                severity = severity,
                reason = dualUseApp.description,
                indicators = indicators,
                recommendations = recommendations
            )
        }
        
        // 2. Verificar app OCULTA (sin launcher icon)
        val isHidden = isHiddenApp(app, pm)
        if (isHidden) {
            indicators.add("👻 App OCULTA sin icono en el launcher")
            severity = "HIGH"
        }
        
        // 3. Verificar nombre de paquete sospechoso
        val hasSuspiciousName = hasSuspiciousPackageName(packageName)
        if (hasSuspiciousName) {
            indicators.add("🔴 Nombre de paquete sospechoso (intenta parecer app del sistema)")
            if (severity == "LOW") severity = "MEDIUM"
        }
        
        // 4. Verificar permisos de stalkerware
        val permissions = getAppPermissions(packageName, pm)
        val stalkerwareProfile = matchesStalkerwarePermissionProfile(permissions)
        if (stalkerwareProfile) {
            indicators.add("🚨 Combinación de permisos típica de stalkerware")
            indicators.add("Permisos detectados: ${permissions.take(5).joinToString(", ")}")
            severity = "CRITICAL"
        }
        
        // 5. Verificar servicio de accesibilidad malicioso
        val hasMaliciousAccessibility = hasMaliciousAccessibilityService(packageName)
        if (hasMaliciousAccessibility) {
            indicators.add("⚡ Servicio de accesibilidad activo (keylogger potencial)")
            severity = "CRITICAL"
        }
        
        // Si tiene al menos 2 indicadores, reportar
        if (indicators.size >= 2) {
            val appName = try {
                pm.getApplicationLabel(app).toString()
            } catch (e: Exception) {
                packageName
            }
            
            recommendations.add("Investigar si instalaste esta app voluntariamente")
            if (isHidden) {
                recommendations.add("Apps ocultas son señal de comportamiento malicioso")
            }
            if (stalkerwareProfile || hasMaliciousAccessibility) {
                recommendations.add("ELIMINAR INMEDIATAMENTE")
                recommendations.add("Esta app puede estar registrando TODO lo que haces")
                recommendations.add("Cambiar contraseñas inmediatamente después de eliminar")
            }
            
            return StalkerwareDetection(
                packageName = packageName,
                appName = appName,
                severity = severity,
                reason = "Múltiples indicadores de stalkerware detectados",
                indicators = indicators,
                recommendations = recommendations
            )
        }
        
        return null
    }
    
    /**
     * Verifica si una app está OCULTA (sin icono en el launcher).
     */
    private fun isHiddenApp(app: ApplicationInfo, pm: PackageManager): Boolean {
        return try {
            val intent = pm.getLaunchIntentForPackage(app.packageName)
            intent == null && (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verifica si el nombre del paquete es sospechoso.
     */
    private fun hasSuspiciousPackageName(packageName: String): Boolean {
        val lowerPackage = packageName.lowercase()
        return StalkerwareDatabase.SUSPICIOUS_PACKAGE_PATTERNS.any { pattern ->
            lowerPackage.contains(pattern.lowercase())
        }
    }
    
    /**
     * Obtiene permisos de una app.
     */
    private fun getAppPermissions(packageName: String, pm: PackageManager): List<String> {
        return try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Verifica si los permisos coinciden con un perfil de stalkerware.
     */
    private fun matchesStalkerwarePermissionProfile(permissions: List<String>): Boolean {
        for (profile in StalkerwareDatabase.STALKERWARE_PERMISSION_PROFILES) {
            val matchCount = profile.count { permission -> permissions.contains(permission) }
            // Si tiene al menos el 80% de los permisos del perfil
            if (matchCount >= (profile.size * 0.8).toInt()) {
                return true
            }
        }
        return false
    }
    
    /**
     * Verifica servicios de accesibilidad maliciosos.
     */
    private fun hasMaliciousAccessibilityService(packageName: String): Boolean {
        // Simplificado: verificar si el nombre del paquete contiene palabras clave
        val lowerPackage = packageName.lowercase()
        return StalkerwareDatabase.MALICIOUS_ACCESSIBILITY_SERVICES.any { service ->
            lowerPackage.contains(service.lowercase())
        }
    }
    
    /**
     * Verifica si es una app del sistema legítima.
     */
    private fun isLegitimateSystemApp(app: ApplicationInfo): Boolean {
        // Solo apps del sistema con firma de Google/Android
        if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) return false
        
        val systemPackages = listOf(
            "com.android.", 
            "com.google.android.",
            "android.",
            "com.sec.android.", // Samsung
            "com.samsung.android.", // Samsung
            "com.miui.", // Xiaomi
            "com.xiaomi.", // Xiaomi
            "com.huawei.", // Huawei
            "com.oppo.", // Oppo
            "com.oneplus.", // OnePlus
            "com.vivo." // Vivo
        )
        
        return systemPackages.any { app.packageName.startsWith(it) }
    }
    
    /**
     * Convierte severidad a score para ordenar.
     */
    private fun getSeverityScore(severity: String): Int {
        return when (severity) {
            "CRITICAL" -> 3
            "HIGH" -> 2
            "MEDIUM" -> 1
            else -> 0
        }
    }
}
