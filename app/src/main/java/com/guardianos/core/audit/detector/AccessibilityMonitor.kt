package com.guardianos.core.audit.detector

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager

/**
 * Accessibility Services Monitor - Detector de servicios de accesibilidad sospechosos
 * 
 * **VECTOR #1 DE STALKERWARE MODERNO (2020+)**
 * 
 * Apps con AccessibilityService activo pueden:
 * - Leer TODA la pantalla (keylogger universal)
 * - Capturar contraseñas, mensajes, conversaciones
 * - Controlar el dispositivo remotamente
 * - Leer notificaciones sin permiso explícito
 * 
 * **SIN ROOT - 100% LOCAL**
 * Usa: AccessibilityManager.getEnabledAccessibilityServiceList()
 */
class AccessibilityMonitor(context: Context) {
    
    // ✅ OBLIGATORIO: usar applicationContext para evitar memory leaks
    private val appContext = context.applicationContext
    
    companion object {
        private const val TAG = "AccessibilityMonitor"
    }
    
    data class AccessibilityServiceReport(
        val packageName: String,
        val appName: String,
        val serviceName: String,
        val serviceDescription: String?,
        val capabilities: List<String>,
        val captureCapabilities: List<String>,
        val isSystemApp: Boolean,
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val riskReasons: List<String>,
        val isWhitelisted: Boolean,
        val settingsActivityName: String? // Para desactivación manual
    )
    
    enum class RiskLevel {
        SAFE,           // App legítima con uso justificado (TalkBack, LastPass)
        LOW,            // Sistema o app conocida
        MEDIUM,         // App sin justificación clara pero sin señales críticas
        HIGH,           // App sospechosa con capacidades peligrosas
        CRITICAL        // Stalkerware confirmado (combinación de factores)
    }
    
    /**
     * Escanea todos los servicios de accesibilidad activos en el dispositivo
     * 
     * ⚡ OPTIMIZADO: Servicios de accesibilidad son muy pocos (~3-5 típicamente)
     * No necesita límites ni timeouts como otros scanners.
     */
    fun scanAccessibilityServices(): List<AccessibilityServiceReport> {
        val reports = mutableListOf<AccessibilityServiceReport>()
        
        try {
            val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) 
                as? AccessibilityManager ?: return emptyList()
            
            // Obtener servicios activos
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            
            Log.d(TAG, "🔍 Servicios de accesibilidad activos: ${enabledServices.size}")
            
            for (serviceInfo in enabledServices) {
                try {
                    val packageName = serviceInfo.resolveInfo.serviceInfo.packageName
                    
                    // ⚡ Skip ColorOS/HeyTap (sistema legítimo)
                    if (isColorOSSystemApp(packageName) || isHeyTapSystemApp(packageName)) {
                        Log.d(TAG, "  Skipping ColorOS/HeyTap: $packageName")
                        continue
                    }
                    
                    val serviceName = serviceInfo.resolveInfo.serviceInfo.name
                    
                    // Obtener información de la app
                    val pm = appContext.packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    // ⚠️ NUNCA usar getApplicationLabel() - carga APK assets (muy lento)
                    val appName = packageName.substringAfterLast('.', "UnknownApp")
                    
                    // Verificar si es app del sistema
                    val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    // Analizar capacidades del servicio
                    val capabilities = getServiceCapabilities(serviceInfo)
                    val captureCapabilities = getCaptureCapabilities(serviceInfo)
                    
                    // Calcular puntuación de riesgo
                    val (riskScore, riskLevel, riskReasons) = calculateRisk(
                        packageName,
                        appName,
                        serviceName,
                        isSystemApp,
                        capabilities,
                        captureCapabilities
                    )
                    
                    // Verificar whitelist
                    val isWhitelisted = isLegitimateAccessibilityApp(packageName, serviceName)
                    
                    // Intentar obtener activity de configuración
                    val settingsActivity = try {
                        serviceInfo.settingsActivityName
                    } catch (e: Exception) {
                        null
                    }
                    
                    val report = AccessibilityServiceReport(
                        packageName = packageName,
                        appName = appName,
                        serviceName = serviceName,
                        serviceDescription = serviceInfo.description,
                        capabilities = capabilities,
                        captureCapabilities = captureCapabilities,
                        isSystemApp = isSystemApp,
                        riskScore = riskScore,
                        riskLevel = if (isWhitelisted) RiskLevel.SAFE else riskLevel,
                        riskReasons = if (isWhitelisted) listOf("App legítima verificada") else riskReasons,
                        isWhitelisted = isWhitelisted,
                        settingsActivityName = settingsActivity
                    )
                    
                    reports.add(report)
                    
                    Log.d(TAG, "✅ Servicio: $appName ($packageName)")
                    Log.d(TAG, "  - Riesgo: ${report.riskLevel.name} (${report.riskScore} puntos)")
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error analizando servicio: ${e.message}")
                }
            }
            
            Log.d(TAG, "✅ Análisis completado: ${reports.size} servicios")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando servicios de accesibilidad", e)
        }
        
        return reports.sortedByDescending { it.riskScore }
    }
    
    /**
     * Obtiene las capacidades del servicio de accesibilidad
     */
    private fun getServiceCapabilities(info: AccessibilityServiceInfo): List<String> {
        val capabilities = mutableListOf<String>()
        
        // Event types que puede capturar
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED != 0) {
            capabilities.add("Leer texto ingresado (KEYLOGGER)")
        }
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED != 0) {
            capabilities.add("Detectar clics en pantalla")
        }
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED != 0) {
            capabilities.add("Detectar elementos enfocados")
        }
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED != 0) {
            capabilities.add("Monitorizar cambios de ventana")
        }
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED != 0) {
            capabilities.add("Leer notificaciones")
        }
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED != 0) {
            capabilities.add("Monitorizar contenido en pantalla")
        }
        
        // Feedback types
        if (info.feedbackType and AccessibilityServiceInfo.FEEDBACK_SPOKEN != 0) {
            capabilities.add("Feedback hablado")
        }
        if (info.feedbackType and AccessibilityServiceInfo.FEEDBACK_HAPTIC != 0) {
            capabilities.add("Feedback háptico")
        }
        if (info.feedbackType and AccessibilityServiceInfo.FEEDBACK_AUDIBLE != 0) {
            capabilities.add("Feedback audible")
        }
        if (info.feedbackType and AccessibilityServiceInfo.FEEDBACK_VISUAL != 0) {
            capabilities.add("Feedback visual")
        }
        if (info.feedbackType and AccessibilityServiceInfo.FEEDBACK_GENERIC != 0) {
            capabilities.add("Feedback genérico")
        }
        
        // Flags
        if (info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS != 0) {
            capabilities.add("Acceso a ventanas interactivas")
        }
        if (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0) {
            capabilities.add("Modo de exploración táctil")
        }
        if (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY != 0) {
            capabilities.add("Accesibilidad web mejorada")
        }
        
        return capabilities
    }
    
    /**
     * Analiza capacidades de captura peligrosas
     */
    private fun getCaptureCapabilities(info: AccessibilityServiceInfo): List<String> {
        val dangerous = mutableListOf<String>()
        
        // Capacidades peligrosas para stalkerware
        if (info.canRetrieveWindowContent) {
            dangerous.add("🚨 Puede leer TODO el contenido de la pantalla")
        }
        
        // Detectar interceptación de texto (passwords, mensajes)
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED != 0) {
            dangerous.add("🔑 Puede capturar texto ingresado (contraseñas)")
        }
        
        // Notificaciones (SMS, WhatsApp, etc.)
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED != 0) {
            dangerous.add("💬 Puede leer todas las notificaciones (incluidos mensajes)")
        }
        
        // Cambio de ventanas (saber qué apps usas)
        if (info.eventTypes and android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED != 0) {
            dangerous.add("👁️ Puede rastrear qué apps abres")
        }
        
        // Realizar acciones (control remoto)
        if (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0) {
            dangerous.add("🎮 Puede controlar el dispositivo remotamente")
        }
        
        return dangerous
    }
    
    /**
     * Calcula puntuación de riesgo basada en múltiples factores
     */
    private fun calculateRisk(
        packageName: String,
        appName: String,
        serviceName: String,
        isSystemApp: Boolean,
        capabilities: List<String>,
        captureCapabilities: List<String>
    ): Triple<Int, RiskLevel, List<String>> {
        var score = 0
        val reasons = mutableListOf<String>()
        
        // Factor 1: Apps no-sistema con accesibilidad son sospechosas por defecto
        if (!isSystemApp) {
            score += 10
        }
        
        // Factor 2: Capacidades de captura peligrosas
        val dangerousCount = captureCapabilities.size
        when {
            dangerousCount >= 4 -> {
                score += 40
                reasons.add("Puede capturar texto, notificaciones y controlar el dispositivo")
            }
            dangerousCount >= 2 -> {
                score += 25
                reasons.add("Tiene múltiples capacidades de captura")
            }
            dangerousCount >= 1 -> {
                score += 15
                reasons.add("Tiene capacidades de captura sensibles")
            }
        }
        
        // Factor 3: Tipos de app que NO deberían usar accesibilidad
        val suspiciousTypes = listOf(
            "linterna", "flashlight", "calculadora", "calculator", "wallpaper", "fondo",
            "limpiador", "cleaner", "boost", "battery", "batería", "manager", "optimizer",
            "vpn", "proxy", "anti", "security", "master", "super", "pro", "free"
        )
        
        if (suspiciousTypes.any { appName.contains(it, ignoreCase = true) }) {
            score += 30
            reasons.add("Tipo de app que NO justifica usar accesibilidad")
        }
        
        // Factor 4: Nombre técnico sospechoso en el servicio
        val suspiciousServiceNames = listOf(
            "monitor", "tracker", "logger", "spy", "stealth", "hidden", "remote",
            "control", "admin", "system", "background", "service"
        )
        
        if (suspiciousServiceNames.any { serviceName.contains(it, ignoreCase = true) }) {
            score += 20
            reasons.add("Nombre del servicio es sospechoso: $serviceName")
        }
        
        // Factor 5: PackageName sospechoso
        val suspiciousPackageNames = listOf(
            "spy", "track", "monitor", "stealth", "hidden", "remote", "control",
            "logger", "keylog", "parent", "family", "locate", "finder", "guard"
        )
        
        if (suspiciousPackageNames.any { packageName.contains(it, ignoreCase = true) }) {
            score += 25
            reasons.add("Package name sospechoso: $packageName")
        }
        
        // Determinar nivel de riesgo
        val riskLevel = when {
            score >= 80 -> RiskLevel.CRITICAL
            score >= 60 -> RiskLevel.HIGH
            score >= 30 -> RiskLevel.MEDIUM
            score >= 15 -> RiskLevel.LOW
            else -> RiskLevel.SAFE
        }
        
        return Triple(score, riskLevel, reasons)
    }
    
    /**
     * Whitelist de apps legítimas que usan AccessibilityService justificadamente
     */
    private fun isLegitimateAccessibilityApp(packageName: String, serviceName: String): Boolean {
        // Apps de accesibilidad oficial de Google
        val googleAccessibility = listOf(
            "com.google.android.marvin.talkback",     // TalkBack
            "com.google.android.apps.accessibility.voiceaccess",  // Voice Access
            "com.google.android.accessibility.switchaccess"  // Switch Access
        )
        
        if (packageName in googleAccessibility) return true
        
        // Gestores de contraseñas legítimos
        val passwordManagers = listOf(
            "com.lastpass.lpandroid",                 // LastPass
            "com.dashlane",                          // Dashlane
            "com.onepassword.android",               // 1Password
            "com.agilebits.onepassword",             // 1Password (alt)
            "com.bitwarden.app",                     // Bitwarden
            "keepass2android.keepass2android",       // KeePass2Android
            "com.microsoft.autofill"                 // Microsoft Autofill
        )
        
        if (packageName in passwordManagers) return true
        
        // Launchers alternativos (necesitan accesibilidad)
        val launchers = listOf(
            "com.teslacoilsw.launcher",              // Nova Launcher
            "com.microsoft.launcher",                // Microsoft Launcher
            "com.actionlauncher.playstore"           // Action Launcher
        )
        
        if (packageName in launchers) return true
        
        // Automatización legítima
        val automation = listOf(
            "net.dinglisch.android.taskerm",         // Tasker
            "com.llamalab.automate",                 // Automate
            "com.joaomgcd.join"                      // Join by Joaomgcd
        )
        
        if (packageName in automation) return true
        
        // Samsung/Xiaomi/Huawei servicios propios
        if (packageName.startsWith("com.samsung.accessibility")) return true
        if (packageName.startsWith("com.miui.accessibility")) return true
        if (packageName.startsWith("com.huawei.accessibility")) return true
        
        return false
    }
    
    /**
     * Verifica si hay servicios de accesibilidad activos (check rápido)
     */
    fun hasActiveAccessibilityServices(): Boolean {
        return try {
            val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) 
                as? AccessibilityManager
            
            val enabledServices = accessibilityManager?.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            
            !enabledServices.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Obtiene string con nombres de servicios activos (para logs/UI)
     */
    fun getEnabledServiceNames(): List<String> {
        return try {
            val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) 
                as? AccessibilityManager ?: return emptyList()
            
            accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).mapNotNull { service ->
                try {
                    val packageName = service.resolveInfo.serviceInfo.packageName
                    // ⚠️ NUNCA usar getApplicationLabel() - carga APK assets (muy lento)
                    packageName.substringAfterLast('.', "UnknownApp")
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * ⚡ CRÍTICO PARA OPPO A80: Filtrado ColorOS
     * ColorOS tiene servicios de accesibilidad del sistema que NO deben reportarse.
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
}
