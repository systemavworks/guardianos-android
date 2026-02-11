package com.guardianos.core.monitor

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.text.SimpleDateFormat
import java.util.*

/**
 * Guardian Shield - Monitor de acceso REAL a permisos en tiempo real.
 * 
 * **100% LOCAL**:
 * - Usa AppOpsManager para verificar permisos REALMENTE CONCEDIDOS (no solo declarados)
 * - Usa UsageStatsManager para detectar apps activas
 * - Sin envío de datos
 * - Almacenamiento local
 * 
 * MEJORA vs versión anterior:
 * - Antes: detectaba apps con permisos DECLARADOS en manifest → muchos falsos positivos
 * - Ahora: verifica con AppOpsManager si el permiso está REALMENTE CONCEDIDO en runtime
 *   y solo alerta sobre apps de terceros (no sistema) con acceso efectivo
 */
class GuardianShieldMonitor(private val context: Context) {
    
    private val appOps: AppOpsManager by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    }
    
    data class PermissionStats(
        val permission: String,
        val accessCount: Int,
        val lastAccess: Long,
        val apps: List<String>
    )
    
    // Apps legítimas del sistema que generarían spam de notificaciones
    private val WHITELIST_PACKAGES = setOf(
        "com.android.systemui",
        "com.google.android.gms",           // Google Play Services
        "com.google.android.gsf",           // Google Services Framework
        "com.android.vending",              // Play Store
        "com.google.android.apps.maps",     // Google Maps (ubicación esperada)
        "com.google.android.dialer",        // Teléfono
        "com.google.android.apps.messaging",// Mensajes
        "com.android.settings",             // Ajustes
        "com.android.providers.contacts",   // Proveedor de contactos
        "com.android.phone",                // Teléfono del sistema
        "com.android.mms",                  // SMS del sistema
        "com.android.camera",               // Cámara del sistema
        "com.android.camera2",
        "com.google.android.GoogleCamera",  // Google Camera
        "com.android.providers.media",      // Media provider
        "com.android.providers.telephony",  // Telephony provider
        "com.android.launcher",             // Launcher
        "com.android.launcher3",
        // Marcas específicas
        "com.coloros.launcher",             // OPPO/ColorOS
        "com.oplus.camera",                 // OPPO Camera
        "com.coloros.safecenter",           // OPPO Security
        "com.heytap.browser",              // OPPO Browser
        "com.samsung.android.app.camera",   // Samsung Camera
        "com.sec.android.app.launcher",     // Samsung Launcher
        "com.miui.home",                    // Xiaomi Launcher
        "com.huawei.camera",               // Huawei Camera
    )
    
    /**
     * Mapa de operaciones AppOps para permisos sensibles.
     * Permite verificar si un permiso está REALMENTE concedido a nivel de sistema.
     */
    private val SENSITIVE_OPS = mapOf(
        AppOpsManager.OPSTR_CAMERA to "android.permission-group.CAMERA",
        AppOpsManager.OPSTR_RECORD_AUDIO to "android.permission-group.MICROPHONE",
        AppOpsManager.OPSTR_FINE_LOCATION to "android.permission-group.LOCATION",
        AppOpsManager.OPSTR_COARSE_LOCATION to "android.permission-group.LOCATION",
        AppOpsManager.OPSTR_READ_CONTACTS to "android.permission-group.CONTACTS",
        AppOpsManager.OPSTR_READ_SMS to "android.permission-group.SMS",
        AppOpsManager.OPSTR_READ_CALL_LOG to "android.permission-group.CALL_LOG",
    )
    
    /**
     * Obtiene apps con accesos REALES a permisos sensibles.
     * 
     * Flujo mejorado:
     * 1. Usa UsageStatsManager para ver qué apps estuvieron activas
     * 2. Para cada app activa, usa AppOpsManager para verificar qué permisos 
     *    tiene REALMENTE CONCEDIDOS (no solo declarados en manifest)
     * 3. Filtra apps del sistema y whitelist
     * 4. Solo reporta permisos que están MODE_ALLOWED en AppOpsManager
     * 
     * @param hoursBack Horas hacia atrás a consultar (por defecto 24h)
     * @return Lista de accesos a permisos verificados
     */
    fun getRecentPermissionAccess(hoursBack: Int = 24): List<PermissionAccessInfo> {
        if (!hasUsageStatsPermission()) {
            return emptyList()
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (hoursBack * 60 * 60 * 1000L)
        
        val accesses = mutableListOf<PermissionAccessInfo>()
        val pm = context.packageManager
        val processedApps = mutableSetOf<String>() // Evitar duplicados por app
        
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    val packageName = event.packageName
                    
                    // Saltar si ya procesamos esta app o está en whitelist
                    if (processedApps.contains(packageName) || isWhitelisted(packageName)) continue
                    
                    // Saltar apps del sistema
                    if (isSystemApp(packageName)) continue
                    
                    processedApps.add(packageName)
                    
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val uid = appInfo.uid
                        
                        // VERIFICAR CON AppOpsManager qué permisos tiene REALMENTE CONCEDIDOS
                        val grantedSensitiveOps = getGrantedSensitiveOps(packageName, uid)
                        
                        grantedSensitiveOps.forEach { (opStr, permissionGroup) ->
                            accesses.add(
                                PermissionAccessInfo(
                                    appName = appName,
                                    packageName = packageName,
                                    permission = opStr,
                                    permissionGroup = permissionGroup,
                                    lastAccessTime = event.timeStamp,
                                    accessCount = 1,
                                    isRealAccess = true // Verificado con AppOpsManager
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // App no accesible
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GuardianShieldMonitor", "Error querying usage events", e)
        }
        
        return accesses.sortedByDescending { it.lastAccessTime }.take(100)
    }
    
    /**
     * Verifica con AppOpsManager qué permisos sensibles tiene REALMENTE
     * concedidos una app (no solo declarados en manifest).
     * 
     * Esto es la MEJORA CLAVE:
     * - checkOpNoThrow() devuelve MODE_ALLOWED solo si el usuario concedió el permiso
     * - Si el permiso está denegado o nunca se pidió, devuelve MODE_IGNORED/MODE_ERRORED
     */
    private fun getGrantedSensitiveOps(packageName: String, uid: Int): List<Pair<String, String>> {
        val granted = mutableListOf<Pair<String, String>>()
        
        SENSITIVE_OPS.forEach { (opStr, permGroup) ->
            try {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(opStr, uid, packageName)
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(opStr, uid, packageName)
                }
                
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    // El permiso está REALMENTE concedido → reportar
                    granted.add(opStr to permGroup)
                }
                // MODE_IGNORED, MODE_ERRORED, MODE_DEFAULT → no alertar
            } catch (e: Exception) {
                // Algunos ops pueden no ser soportados en todas las versiones
            }
        }
        
        return granted
    }
    
    /**
     * Verifica si una app está en la whitelist.
     */
    private fun isWhitelisted(packageName: String): Boolean {
        return WHITELIST_PACKAGES.contains(packageName) ||
               packageName.startsWith("com.android.") ||
               packageName.startsWith("com.google.android.providers.")
    }
    
    /**
     * Verifica si es una app del sistema (preinstalada).
     */
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Estadísticas agregadas de permisos.
     */
    fun getPermissionStatistics(hoursBack: Int = 24): Map<String, Int> {
        val accesses = getRecentPermissionAccess(hoursBack)
        return accesses
            .groupBy { it.permissionGroup }
            .mapValues { it.value.size }
    }
    
    /**
     * Verifica si tenemos permiso de UsageStats.
     */
    fun hasUsageStatsPermission(): Boolean {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 60000 // Último minuto (más fiable que 1s)
            
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            stats != null && stats.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Nombre amigable del permiso/operación.
     */
    fun getFriendlyPermissionName(opOrPermission: String): String {
        return when {
            opOrPermission.contains("camera", ignoreCase = true) -> "Cámara 📷"
            opOrPermission.contains("audio", ignoreCase = true) ||
            opOrPermission.contains("microphone", ignoreCase = true) -> "Micrófono 🎤"
            opOrPermission.contains("location", ignoreCase = true) -> "Ubicación 📍"
            opOrPermission.contains("contacts", ignoreCase = true) -> "Contactos 👥"
            opOrPermission.contains("sms", ignoreCase = true) -> "Mensajes SMS 💬"
            opOrPermission.contains("call", ignoreCase = true) -> "Llamadas 📞"
            opOrPermission.contains("storage", ignoreCase = true) ||
            opOrPermission.contains("media", ignoreCase = true) -> "Almacenamiento 💾"
            opOrPermission.contains("sensor", ignoreCase = true) -> "Sensores corporales 💓"
            else -> opOrPermission.substringAfterLast(":").substringAfterLast(".")
        }
    }
    
    /**
     * Carga accesos conocidos (para detectar primera vez).
     */
    private fun loadKnownAccesses(): MutableSet<String> {
        val prefs = context.getSharedPreferences("guardian_shield", Context.MODE_PRIVATE)
        val json = prefs.getString("known_accesses", "[]") ?: "[]"
        return try {
            com.google.gson.Gson().fromJson(json, Array<String>::class.java).toMutableSet()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }
    
    /**
     * Guarda accesos conocidos.
     */
    private fun saveKnownAccesses(accesses: Set<String>) {
        val prefs = context.getSharedPreferences("guardian_shield", Context.MODE_PRIVATE)
        val json = com.google.gson.Gson().toJson(accesses.toList())
        prefs.edit().putString("known_accesses", json).apply()
    }
    
    /**
     * Genera mensaje de tiempo relativo.
     */
    fun getRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Hace ${diff / 1000} seg"
            diff < 3600000 -> "Hace ${diff / 60000} min"
            diff < 86400000 -> "Hace ${diff / 3600000} h"
            else -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

/**
 * Información sobre un acceso a permiso detectado.
 * 
 * @param isRealAccess true si fue verificado con AppOpsManager (permiso realmente concedido),
 *                     false si solo fue detectado como declarado en manifest
 */
data class PermissionAccessInfo(
    val appName: String,
    val packageName: String,
    val permission: String,
    val permissionGroup: String,
    val lastAccessTime: Long,
    val accessCount: Int,
    val isRealAccess: Boolean = false
)
