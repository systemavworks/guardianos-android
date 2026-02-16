package com.guardianos.core.monitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.guardianos.core.MainActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Servicio en primer plano que monitoriza accesos a permisos sensibles en tiempo real
 * y muestra notificaciones cuando apps sospechosas acceden a cámara, micrófono, ubicación, etc.
 * 
 * Solo disponible en versión PRO.
 */
class GuardianShieldService : Service() {
    
    companion object {
        private const val TAG = "GuardianShieldService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "guardian_shield_monitor"
        private const val ALERT_CHANNEL_ID = "guardian_shield_alerts"
        private const val CHECK_INTERVAL_MS = 5000L // 5 segundos (detección más rápida)
        
        // Apps del sistema que se ignoran para evitar spam de notificaciones
        private val SYSTEM_WHITELIST = setOf(
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.apps.maps",
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.settings",
            "com.android.providers.contacts"
        )
        
        fun isRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences("guardian_shield", Context.MODE_PRIVATE)
            return prefs.getBoolean("service_running", false)
        }
        
        fun start(context: Context) {
            try {
                val intent = Intent(context, GuardianShieldService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error al iniciar servicio Guardian Shield", e)
                // Resetear estado si falla
                context.getSharedPreferences("guardian_shield", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("service_running", false)
                    .apply()
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, GuardianShieldService::class.java)
            context.stopService(intent)
        }
        
        /**
         * Obtiene historial de accesos guardados.
         * @return Lista de strings con formato "dd/MM/yyyy HH:mm:ss - App usó permiso"
         */
        fun getPermissionAccessHistory(context: Context): List<String> {
            val prefs = context.getSharedPreferences("guardian_shield_log", Context.MODE_PRIVATE)
            val log = prefs.getString("access_log", "") ?: ""
            return log.lines()
                .filter { it.isNotBlank() }
                .reversed() // Más recientes primero
                .map { line ->
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val timestamp = parts[0]
                        val appName = parts[2]
                        val permission = parts[3]
                        val permissionName = when (permission) {
                            "android.permission-group.CAMERA" -> "cámara"
                            "android.permission-group.MICROPHONE" -> "micrófono"
                            "android.permission-group.LOCATION" -> "ubicación"
                            "android.permission-group.CONTACTS" -> "contactos"
                            "android.permission-group.SMS" -> "SMS"
                            "android.permission-group.CALL_LOG" -> "registro de llamadas"
                            "android.permission-group.PHONE" -> "teléfono"
                            else -> permission
                        }
                        "$timestamp - $appName usó $permissionName"
                    } else {
                        line // Fallback para formato antiguo
                    }
                }
        }
    }
    
    private lateinit var handler: Handler
    private lateinit var monitor: GuardianShieldMonitor
    private var lastCheckedTime = 0L
    private val seenAccesses = mutableSetOf<String>() // packageName:permission
    private val lastAppOpened = mutableMapOf<String, Long>() // packageName -> timestamp
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            createNotificationChannels()
            monitor = GuardianShieldMonitor(this)
            handler = Handler(Looper.getMainLooper())
            
            // Marcar servicio como ejecutándose
            getSharedPreferences("guardian_shield", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("service_running", true)
                .apply()
            
            startForeground(NOTIFICATION_ID, createForegroundNotification())
            startMonitoring()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error al iniciar Guardian Shield Service", e)
            // Si falla el inicio, detener el servicio limpiamente
            getSharedPreferences("guardian_shield", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("service_running", false)
                .apply()
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        
        // Marcar servicio como detenido
        getSharedPreferences("guardian_shield", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_running", false)
            .apply()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal para notificación permanente del servicio
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Guardian Shield Activo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación permanente mientras Guardian Shield está monitorizando"
                setShowBadge(false)
            }
            
            // Canal para información de accesos a permisos (silencioso)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Información de Permisos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones informativas sobre apps que usan permisos sensibles"
                setSound(null, null)  // Sin sonido
                enableVibration(false)  // Sin vibración
                enableLights(false)  // Sin luz LED
                setShowBadge(false)  // Sin badge en icono
            }
            
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Guardian Shield Activo")
            .setContentText("Monitorizando accesos a permisos sensibles")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                checkPermissionAccesses()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        })
    }
    
    private fun checkPermissionAccesses() {
        try {
            val prefs = getSharedPreferences("guardian_shield", Context.MODE_PRIVATE)
            val userWhitelist = prefs.getStringSet("whitelist_apps", emptySet()) ?: emptySet()
            
            android.util.Log.d(TAG, "🔍 Guardian Shield: Verificando apps abiertas recientemente...")
            
            val currentTime = System.currentTimeMillis()
            
            // Obtener apps que se abrieron RECIENTEMENTE (últimos 10 segundos)
            val recentlyOpenedApps = monitor.getRecentlyOpenedApps(10) // segundos
            
            android.util.Log.i(TAG, "📱 Apps abiertas detectadas: ${recentlyOpenedApps.size}")
            
            if (recentlyOpenedApps.isEmpty()) {
                android.util.Log.d(TAG, "   → Ninguna app detectada en los últimos 10 segundos")
                return
            }
            
            var notificationsSent = 0
            
            recentlyOpenedApps.forEach { appInfo ->
                val packageName = appInfo.packageName
                val appName = appInfo.appName
                
                android.util.Log.d(TAG, "  📲 Analizando: $appName ($packageName)")
                
                // Verificar si ya notificamos esta apertura
                val lastOpenTime = lastAppOpened[packageName] ?: 0L
                if (currentTime - lastOpenTime < 60000) { // No repetir en 1 minuto
                    android.util.Log.d(TAG, "     ⊗ Ya notificado hace menos de 1 minuto")
                    return@forEach
                }
                
                // Verificar whitelist del sistema
                if (SYSTEM_WHITELIST.contains(packageName)) {
                    android.util.Log.d(TAG, "     ⊗ En whitelist del sistema")
                    return@forEach
                }
                
                // Verificar whitelist del usuario
                if (userWhitelist.contains(packageName)) {
                    android.util.Log.d(TAG, "     ⊗ En whitelist del usuario")
                    return@forEach
                }
                
                // Obtener permisos SENSIBLES que tiene CONCEDIDOS
                val grantedSensitivePermissions = monitor.getGrantedSensitivePermissions(packageName)
                
                if (grantedSensitivePermissions.isEmpty()) {
                    android.util.Log.d(TAG, "     ✓ No tiene permisos sensibles concedidos")
                    return@forEach
                }
                
                android.util.Log.i(TAG, "     🚨 TIENE ${grantedSensitivePermissions.size} PERMISOS SENSIBLES:")
                grantedSensitivePermissions.forEach { perm ->
                    android.util.Log.i(TAG, "        - $perm")
                }
                
                // Registrar que ya notificamos
                lastAppOpened[packageName] = currentTime
                
                // Mostrar notificación POR CADA PERMISO
                grantedSensitivePermissions.forEach { permissionGroup ->
                    val access = PermissionAccessInfo(
                        appName = appName,
                        packageName = packageName,
                        permission = permissionGroup,
                        permissionGroup = permissionGroup,
                        lastAccessTime = currentTime,
                        accessCount = 1,
                        isRealAccess = true
                    )
                    
                    showPermissionAlert(access)
                    logPermissionAccess(access)
                    notificationsSent++
                }
            }
            
            lastCheckedTime = currentTime
            
            if (notificationsSent > 0) {
                android.util.Log.i(TAG, "✅ ${notificationsSent} notificaciones enviadas")
            } else {
                android.util.Log.d(TAG, "   → Sin notificaciones para enviar")
            }
            
            // Limpiar cache antiguo
            if (lastAppOpened.size > 50) {
                val twoMinutesAgo = currentTime - 120000
                lastAppOpened.entries.removeIf { it.value < twoMinutesAgo }
                android.util.Log.d(TAG, "Cache limpiado, quedan ${lastAppOpened.size} apps")
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking permissions", e)
        }
    }
    
    private fun isSensitivePermission(permissionGroup: String): Boolean {
        return when (permissionGroup) {
            "android.permission-group.CAMERA",
            "android.permission-group.MICROPHONE",
            "android.permission-group.LOCATION",
            "android.permission-group.CONTACTS",
            "android.permission-group.SMS",
            "android.permission-group.CALL_LOG",
            "android.permission-group.PHONE" -> true
            else -> false
        }
    }
    
    private fun showPermissionAlert(access: PermissionAccessInfo) {
        val permissionName = getPermissionDisplayName(access.permissionGroup)
        val icon = getPermissionIcon(access.permissionGroup)
        
        android.util.Log.d(TAG, "📢 Preparando notificación: ${access.appName} - $permissionName")
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            access.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Verificar permiso POST_NOTIFICATIONS antes de notificar (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w(TAG, "❌ No se puede notificar: permiso POST_NOTIFICATIONS denegado")
                return
            }
        }
        
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("$icon ${access.appName} puede usar $permissionName")
            .setContentText("App abierta con acceso a $permissionName")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)  // Visible pero no intrusiva
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(false)  // Permitir múltiples alertas
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${access.appName} se acaba de abrir y tiene permiso para usar $permissionName. " +
                        "Monitorizado por GuardianShield. Toca para más detalles."))
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(access.hashCode(), notification)
        
        android.util.Log.i(TAG, "✅ Notificación enviada: ${access.appName} - $permissionName")
    }
    
    private fun getPermissionDisplayName(permissionGroup: String): String {
        return when (permissionGroup) {
            "android.permission-group.CAMERA" -> "tu cámara"
            "android.permission-group.MICROPHONE" -> "tu micrófono"
            "android.permission-group.LOCATION" -> "tu ubicación"
            "android.permission-group.CONTACTS" -> "tus contactos"
            "android.permission-group.SMS" -> "tus SMS"
            "android.permission-group.CALL_LOG" -> "tu registro de llamadas"
            "android.permission-group.PHONE" -> "tu teléfono"
            else -> "un permiso sensible"
        }
    }
    
    private fun getPermissionIcon(permissionGroup: String): String {
        return when (permissionGroup) {
            "android.permission-group.CAMERA" -> "📷"
            "android.permission-group.MICROPHONE" -> "🎤"
            "android.permission-group.LOCATION" -> "📍"
            "android.permission-group.CONTACTS" -> "👥"
            "android.permission-group.SMS" -> "💬"
            "android.permission-group.CALL_LOG" -> "📞"
            "android.permission-group.PHONE" -> "☎️"
            else -> "⚠️"
        }
    }
    
    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60000 -> "menos de 1 minuto"
            diff < 3600000 -> "${diff / 60000} minutos"
            else -> "${diff / 3600000} horas"
        }
    }
    
    private fun logPermissionAccess(access: PermissionAccessInfo) {
        try {
            val prefs = getSharedPreferences("guardian_shield_log", Context.MODE_PRIVATE)
            val log = prefs.getString("access_log", "") ?: ""
            
            // Formato: timestamp|packageName|appName|permission
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date(access.lastAccessTime))
            val newEntry = "$timestamp|${access.packageName}|${access.appName}|${access.permissionGroup}"
            
            // Mantener últimas 200 entradas (historial más largo)
            val lines = log.lines().filter { it.isNotBlank() }.takeLast(199)
            val updatedLog = (lines + newEntry).joinToString("\n")
            
            prefs.edit()
                .putString("access_log", updatedLog)
                .apply()
                
            android.util.Log.d(TAG, "📝 Entrada guardada en historial: ${access.appName} - ${getPermissionDisplayName(access.permissionGroup)}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error guardando log: ${e.message}")
        }
    }
}
