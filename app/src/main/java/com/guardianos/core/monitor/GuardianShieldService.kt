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
        private const val CHECK_INTERVAL_MS = 30000L // 30 segundos
        
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
    }
    
    private lateinit var handler: Handler
    private lateinit var monitor: GuardianShieldMonitor
    private var lastCheckedTime = 0L
    private val seenAccesses = mutableSetOf<String>() // packageName:permission
    
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
            
            // Obtener accesos recientes (últimos 2 minutos)
            val currentTime = System.currentTimeMillis()
            val recentAccesses = monitor.getRecentPermissionAccess(2)
            
            android.util.Log.d(TAG, "Guardian Shield check: ${recentAccesses.size} accesos detectados")
            
            recentAccesses.forEach { access ->
                val accessKey = "${access.packageName}:${access.permissionGroup}:${access.lastAccessTime}"
                
                // Evitar duplicados y apps del sistema/whitelist
                if (!seenAccesses.contains(accessKey) &&
                    !SYSTEM_WHITELIST.contains(access.packageName) &&
                    !userWhitelist.contains(access.packageName) &&
                    access.lastAccessTime > lastCheckedTime
                ) {
                    seenAccesses.add(accessKey)
                    
                    // Solo alertar de permisos sensibles
                    if (isSensitivePermission(access.permissionGroup)) {
                        android.util.Log.i(TAG, "🛡️ Detectado: ${access.appName} (${access.packageName}) usando ${access.permissionGroup}")
                        
                        // Siempre mostrar notificación silenciosa (sin sonido/vibración)
                        showPermissionAlert(access)
                        
                        // Guardar log del acceso
                        logPermissionAccess(access)
                    }
                }
            }
            
            lastCheckedTime = currentTime
            
            // Limpiar cache de accesos viejos (más de 5 minutos)
            if (seenAccesses.size > 100) {
                seenAccesses.clear()
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
        
        android.util.Log.d(TAG, "Preparando notificación para ${access.appName} - $permissionName")
        
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
            .setContentTitle("$icon ${access.appName} está usando $permissionName")
            .setContentText("Acceso detectado en segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Silenciosa
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(null)  // Sin sonido
            .setVibrate(null)  // Sin vibración
            .setOnlyAlertOnce(true)  // No repetir alert
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${access.appName} está usando $permissionName. " +
                        "Notificación informativa. Toca para ver detalles o revisar permisos en Ajustes."))
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
        val prefs = getSharedPreferences("guardian_shield_log", Context.MODE_PRIVATE)
        val log = prefs.getString("access_log", "") ?: ""
        val newEntry = "${System.currentTimeMillis()}|${access.packageName}|${access.permissionGroup}\n"
        
        // Mantener solo últimas 100 entradas
        val lines = log.lines().takeLast(99)
        prefs.edit()
            .putString("access_log", lines.joinToString("\n") + "\n" + newEntry)
            .apply()
    }
}
