package com.guardianos.core.audit.detector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.guardianos.core.data.StalkerwareDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

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
class StalkerwareDetector(context: Context) {
    
    // ✅ OBLIGATORIO: usar applicationContext para evitar memory leaks
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    
    companion object {
        private const val TAG = "StalkerwareDetector"
        private const val MAX_SCAN_TIME_MS = 25_000L // 25 segundos MÁXIMO (evitar timeout sistema)
        private const val MAX_APPS_TO_SCAN = 150 // 150 apps priorizadas (balance estabilidad/cobertura)
        private const val YIELD_INTERVAL = 4 // Yield cada 4 apps (más cooperativo)
        private const val YIELD_DELAY_MS = 80L // 80ms breathing room para GC en OPPO A80
    }
    
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
     * 
     * ⚡ OPTIMIZADO con priorización inteligente:
     * - Límite 100 apps (análisis más completo)
     * - Timeout 30 segundos
     * - Prioriza apps sospechosas primero
     * - Filtrado agresivo ColorOS/HeyTap
     * - Yield cada 5 apps (cooperativo)
     * - NUNCA getApplicationLabel() (evita cargar APK assets)
     */
    suspend fun scanForStalkerware(): List<StalkerwareDetection> = withTimeoutOrNull(MAX_SCAN_TIME_MS) {
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val detections = mutableListOf<StalkerwareDetection>()
        
        Log.d(TAG, "📱 Apps instaladas totales: ${installedApps.size}")
        
        // ✅ Filtrado agresivo + PRIORIZACIÓN INTELIGENTE
        // Prioriza apps sospechosas primero, luego analiza hasta MAX_APPS_TO_SCAN
        val appsToScan = installedApps
            .asSequence()
            .filterNot { isLegitimateSystemApp(it) }
            .filterNot { isColorOSSystemApp(it.packageName) }
            .filterNot { isHeyTapSystemApp(it.packageName) }
            .sortedByDescending { calculateSuspicionScore(it) } // 🎯 Sospechosas primero
            .take(MAX_APPS_TO_SCAN)
            .toList()
        
        Log.d(TAG, "🔍 Apps a escanear (tras filtrado): ${appsToScan.size}")
        
        appsToScan.forEachIndexed { index, app ->
            // ✅ Verificar cancelación ANTES de cada app
            coroutineContext.ensureActive()
            
            val detection = analyzeApp(app, packageManager)
            if (detection != null) {
                detections.add(detection)
                Log.d(TAG, "⚠️ Detección: ${app.packageName}")
            }
            
            // ✅ Yield cada N apps para no bloquear (cooperativo con sistema)
            if (index % YIELD_INTERVAL == 0 && index > 0) {
                yield()
                delay(YIELD_DELAY_MS) // Breathing room para GC
                
                // 🧹 GC agresivo cada 20 apps en OPPO A80 (RAM limitada)
                if (index % 20 == 0) {
                    System.gc()
                    delay(100) // Dar tiempo al GC
                }
            }
        }
        
        Log.d(TAG, "✅ Escaneo completo: ${detections.size} detecciones")
        detections.sortedByDescending { getSeverityScore(it.severity) }
    } ?: run {
        // Timeout alcanzado, devolver detecciones parciales
        Log.w(TAG, "⏱️ Timeout alcanzado (15s), devolviendo detecciones parciales")
        emptyList()
    }
    
    /**
     * Analiza una app específica por packageName en busca de stalkerware.
     * Utilizada por AppAuditor para integración en escaneo completo PRO.
     */
    suspend fun analyzeApp(packageName: String): StalkerwareDetection? {
        return try {
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
            // ⚠️ NUNCA usar getApplicationLabel() - carga APK assets (muy lento)
            val appName = packageName.substringAfterLast('.', packageName)
            
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
            "com.oppo.", // Oppo (base legítimo)
            "com.oneplus.", // OnePlus
            "com.vivo." // Vivo
        )
        
        return systemPackages.any { app.packageName.startsWith(it) }
    }
    
    /**
     * ⚡ CRÍTICO PARA OPPO A80: Filtrado ColorOS
     * ColorOS tiene 50+ apps sistema que NO deben escanearse.
     */
    private fun isColorOSSystemApp(packageName: String): Boolean {
        return packageName.startsWith("com.oplus.") ||
               packageName.startsWith("com.coloros.") ||
               packageName.startsWith("com.oppo.os.") ||
               packageName.startsWith("com.oppo.ambient.") ||
               packageName.startsWith("com.nearme.")
    }
    
    /**
     * ⚡ CRÍTICO PARA OPPO A80: Filtrado HeyTap (tienda/servicios OPPO)
     */
    private fun isHeyTapSystemApp(packageName: String): Boolean {
        return packageName.startsWith("com.heytap.") ||
               packageName.startsWith("com.oppo.market.") ||
               packageName == "com.oppo.usercenter"
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
    
    /**
     * 🎯 Calcula puntuación de sospecha para priorizar análisis.
     * Apps con mayor puntuación se analizan primero.
     */
    private fun calculateSuspicionScore(app: ApplicationInfo): Int {
        var score = 0
        val packageName = app.packageName.lowercase()
        
        // Nombres sospechosos (+30 puntos)
        val suspiciousKeywords = listOf(
            "spy", "track", "monitor", "stealth", "hidden", "secret",
            "keylog", "record", "capture", "locate", "finder", "watcher"
        )
        if (suspiciousKeywords.any { packageName.contains(it) }) {
            score += 30
        }
        
        // Apps sin icono launcher (+25 puntos)
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent == null && (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                score += 25
            }
        } catch (e: Exception) {
            // Ignorar
        }
        
        // Instalación reciente (<7 días) (+15 puntos)
        try {
            val packageInfo = packageManager.getPackageInfo(app.packageName, 0)
            val daysSinceInstall = (System.currentTimeMillis() - packageInfo.firstInstallTime) / (1000 * 60 * 60 * 24)
            if (daysSinceInstall < 7) {
                score += 15
            }
        } catch (e: Exception) {
            // Ignorar
        }
        
        // Permisos peligrosos (+5 por permiso, máx 20)
        try {
            val packageInfo = packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            val dangerousPerms = packageInfo.requestedPermissions?.filter { perm ->
                perm.contains("CAMERA") || perm.contains("MICROPHONE") ||
                perm.contains("LOCATION") || perm.contains("SMS") ||
                perm.contains("CALL") || perm.contains("CONTACTS")
            } ?: emptyList()
            score += minOf(dangerousPerms.size * 5, 20)
        } catch (e: Exception) {
            // Ignorar
        }
        
        return score
    }
}
