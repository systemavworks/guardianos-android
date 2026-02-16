/*
 * GuardianOS - Ethical digital protection for minors
 * Copyright (C) 2026 Victor Shift Lara
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.guardianos.core.monitor

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Tipos de permisos monitorizables
 */
enum class PermissionType {
    CAMERA, MICROPHONE, LOCATION, CONTACTS, SMS, CALL_LOG,
    MEDIA_PHOTOS, MEDIA_VIDEO, MEDIA_AUDIO, CALENDAR,
    BLUETOOTH, NEARBY_DEVICES, ACCESSIBILITY, OVERLAY;
    
    fun humanReadable() = when (this) {
        CAMERA -> "la cámara"
        MICROPHONE -> "el micrófono"
        LOCATION -> "tu ubicación"
        CONTACTS -> "tus contactos"
        SMS -> "tus mensajes SMS"
        CALL_LOG -> "tu historial de llamadas"
        MEDIA_PHOTOS -> "tus fotos"
        MEDIA_VIDEO -> "tus vídeos"
        MEDIA_AUDIO -> "tu música"
        CALENDAR -> "tu calendario"
        BLUETOOTH -> "Bluetooth"
        NEARBY_DEVICES -> "dispositivos cercanos"
        ACCESSIBILITY -> "accesibilidad"
        OVERLAY -> "superposición de ventanas"
    }
    
    fun icon() = when (this) {
        CAMERA -> "📷"
        MICROPHONE -> "🎤"
        LOCATION -> "📍"
        CONTACTS -> "👥"
        SMS -> "💬"
        CALL_LOG -> "📞"
        MEDIA_PHOTOS -> "🖼️"
        MEDIA_VIDEO -> "🎬"
        MEDIA_AUDIO -> "🎵"
        CALENDAR -> "📅"
        BLUETOOTH -> "📶"
        NEARBY_DEVICES -> "📡"
        ACCESSIBILITY -> "♿"
        OVERLAY -> "🪟"
    }
}

/**
 * Registro de uso activo de permisos
 */
data class ActivePermissionUsage(
    val packageName: String,
    val appName: String,
    val permissionType: PermissionType,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val isForeground: Boolean = true,
    val isActive: Boolean = true
)

/**
 * Monitor de permisos en tiempo real con filosofía ética GuardianOS
 * 
 * Principios:
 * - Informa sin alarmar
 * - Muestra límites técnicos transparentemente
 * - Todo 100% local (nunca envía datos)
 * - Notificaciones de baja prioridad (no intrusivas)
 */
class RealTimePermissionMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "GuardianMonitor"
        private const val NOTIFICATION_CHANNEL_ID = "guardian_permissions"
        private const val SCAN_INTERVAL_MS = 2000L
        private const val SESSION_TIMEOUT_MS = 5000L
        private const val NOTIFICATION_COOLDOWN_MS = 30000L
    }
    
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    private val packageManager = context.packageManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val _activeUsages = MutableSharedFlow<ActivePermissionUsage>(replay = 50)
    val activeUsages = _activeUsages.asSharedFlow()
    
    private val activeSessions = ConcurrentHashMap<String, ActivePermissionUsage>()
    private val lastNotificationTime = ConcurrentHashMap<String, Long>()
    
    private var monitoringJob: Job? = null
    var isMonitoring = false
        private set
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Inicia monitorización en tiempo real (escaneo cada 2s)
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        Log.d(TAG, "Iniciando monitorización en tiempo real")
        isMonitoring = true
        
        monitoringJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            while (isMonitoring) {
                try {
                    scanActivePermissions()
                    detectSensorUsage()
                    cleanupInactiveSessions()
                    
                    delay(SCAN_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.w(TAG, "Error en monitorización", e)
                    delay(SCAN_INTERVAL_MS * 2) // Backoff en errores
                }
            }
        }
    }
    
    /**
     * Detiene monitorización
     */
    fun stopMonitoring() {
        Log.d(TAG, "Deteniendo monitorización")
        isMonitoring = false
        monitoringJob?.cancel()
        activeSessions.clear()
    }
    
    /**
     * Obtiene lista de sesiones activas actuales
     */
    fun getActiveSessions(): List<ActivePermissionUsage> {
        return activeSessions.values.toList()
    }
    
    /**
     * Escanea permisos activos usando AppOpsManager
     */
    private suspend fun scanActivePermissions() {
        if (appOpsManager == null) {
            Log.w(TAG, "AppOpsManager no disponible")
            return
        }
        
        val now = System.currentTimeMillis()
        
        // Lista de operaciones sensibles a monitorizar
        val sensitiveOps = mapOf(
            AppOpsManager.OPSTR_CAMERA to PermissionType.CAMERA,
            AppOpsManager.OPSTR_RECORD_AUDIO to PermissionType.MICROPHONE,
            AppOpsManager.OPSTR_FINE_LOCATION to PermissionType.LOCATION,
            AppOpsManager.OPSTR_COARSE_LOCATION to PermissionType.LOCATION,
            AppOpsManager.OPSTR_READ_CONTACTS to PermissionType.CONTACTS,
            AppOpsManager.OPSTR_READ_SMS to PermissionType.SMS,
            AppOpsManager.OPSTR_READ_CALL_LOG to PermissionType.CALL_LOG
        )
        
        // Nota: Android limita severamente qué se puede monitorizar sin root
        // Solo podemos detectar permisos de nuestra propia app de forma precisa
        // Para otras apps, necesitaríamos USAGE_STATS_SERVICE + activación manual
        
        for ((op, type) in sensitiveOps) {
            try {
                // Verificar si HAY actividad (limitado sin root)
                val packages = getActivePackagesForOp(op)
                
                for (pkg in packages) {
                    val sessionId = "$pkg-${type.name}"
                    val existing = activeSessions[sessionId]
                    
                    if (existing == null || !existing.isActive) {
                        // Nueva sesión detectada
                        val appName = getFriendlyAppName(pkg)
                        val usage = ActivePermissionUsage(
                            packageName = pkg,
                            appName = appName,
                            permissionType = type,
                            timestamp = now,
                            isForeground = isLikelyForeground(pkg)
                        )
                        
                        activeSessions[sessionId] = usage
                        withContext(Dispatchers.Main) {
                            _activeUsages.emit(usage)
                        }
                        notifyPermissionAccess(usage)
                        
                        Log.d(TAG, "Detectado: $appName usando ${type.humanReadable()}")
                    } else {
                        // Actualizar duración
                        val updated = existing.copy(durationMs = now - existing.timestamp)
                        activeSessions[sessionId] = updated
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error escaneando $op: ${e.message}")
            }
        }
    }
    
    /**
     * Detecta uso de sensores (limitado sin root)
     */
    private fun detectSensorUsage() {
        // Nota: Android no permite detectar qué app específica usa mic/cámara sin root
        // Solo podemos detectar que ALGUNA app está usando estos recursos
        // Esta es una limitación técnica transparente que mostramos al usuario
    }
    
    /**
     * Limpia sesiones inactivas (>5s sin detección)
     */
    private suspend fun cleanupInactiveSessions() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        activeSessions.forEach { (sessionId, usage) ->
            val age = now - usage.timestamp - usage.durationMs
            if (age > SESSION_TIMEOUT_MS) {
                // Marcar como finalizada
                val finalized = usage.copy(
                    durationMs = usage.durationMs + age,
                    isActive = false
                )
                withContext(Dispatchers.Main) {
                    _activeUsages.emit(finalized)
                }
                toRemove.add(sessionId)
                
                Log.d(TAG, "Sesión finalizada: ${usage.appName} - ${usage.permissionType.humanReadable()}")
            }
        }
        
        toRemove.forEach { activeSessions.remove(it) }
    }
    
    /**
     * Obtiene paquetes activos para una operación (limitado sin permisos especiales)
     */
    private fun getActivePackagesForOp(op: String): List<String> {
        // Sin USAGE_STATS_SERVICE activado manualmente, no podemos ver otras apps
        // Devolvemos lista vacía y mostramos transparentemente esta limitación
        return emptyList()
    }
    
    /**
     * Notificación ética (baja prioridad, sin alarmar)
     */
    private fun notifyPermissionAccess(usage: ActivePermissionUsage) {
        // Solo notificar permisos críticos configurados
        if (!shouldNotifyFor(usage.permissionType)) return
        
        // Cooldown para evitar spam
        val cooldownKey = "${usage.packageName}-${usage.permissionType.name}"
        val lastNotif = lastNotificationTime[cooldownKey] ?: 0L
        if (System.currentTimeMillis() - lastNotif < NOTIFICATION_COOLDOWN_MS) return
        
        lastNotificationTime[cooldownKey] = System.currentTimeMillis()
        
        try {
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("📱 ${usage.appName}")
                .setContentText("Está usando ${usage.permissionType.humanReadable()}")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("La app ${usage.appName} está accediendo a ${usage.permissionType.humanReadable()} en este momento.\n\n🔒 Esta información solo está en tu dispositivo."))
                .setPriority(NotificationCompat.PRIORITY_LOW) // No alarmista
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setColor(0xFF5D8BF4.toInt()) // Azul ético
                .setOnlyAlertOnce(true)
                .build()
            
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context)
                    .notify(cooldownKey.hashCode(), notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando notificación", e)
        }
    }
    
    /**
     * Crea canal de notificaciones (Android 8+)
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Guardian Shield - Monitorización de Permisos",
            NotificationManager.IMPORTANCE_LOW // Baja prioridad por defecto
        ).apply {
            description = "Notificaciones éticas cuando apps usan permisos sensibles (cámara, micrófono, ubicación)"
            setShowBadge(false)
            enableVibration(false) // Sin vibración (no alarmar)
            setSound(null, null) // Sin sonido (no alarmar)
        }
        
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Verifica si debe notificar para este tipo de permiso
     */
    private fun shouldNotifyFor(type: PermissionType): Boolean {
        // Por defecto: solo notificar para los más críticos
        return type in listOf(
            PermissionType.CAMERA,
            PermissionType.MICROPHONE,
            PermissionType.LOCATION
        )
    }
    
    /**
     * Obtiene nombre legible de una app
     */
    private fun getFriendlyAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
    
    /**
     * Heurística simple si app está en foreground (sin USAGE_STATS)
     */
    private fun isLikelyForeground(packageName: String): Boolean {
        // Sin USAGE_STATS_SERVICE, asumimos foreground si detectamos actividad
        // Limitación técnica transparente
        return true
    }
    
    /**
     * Verifica si tiene permisos necesarios para monitorización completa
     */
    fun hasRequiredPermissions(): Boolean {
        // USAGE_STATS_SERVICE requiere activación manual en ajustes
        // No podemos verificarlo programáticamente
        return false // Retornamos false para mostrar transparentemente la limitación
    }
}
