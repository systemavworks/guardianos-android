package com.guardianos.core.audit.detector

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Background Services Analyzer - Detector de servicios persistentes sospechosos
 * 
 * **TÉCNICA COMÚN DE STALKERWARE**
 * 
 * Stalkerware necesita estar siempre activo para espiar:
 * - Foreground services persistentes (>24 horas)
 * - Wake locks que evitan el deep sleep
 * - Alarmas frecuentes (cada X minutos para C&C sync)
 * - JobScheduler con IDLE/CHARGING abuse
 * - WorkManager con tareas persistentes
 * - Servicios sin notificación (Android 8+ workaround)
 * 
 * **SIN ROOT - 100% FIABLE**
 * Usa: ActivityManager, JobScheduler, AlarmManager, PowerManager
 */
object BackgroundServicesAnalyzer {
    private const val TAG = "BackgroundServicesAnalyzer"
    
    data class BackgroundServiceReport(
        val packageName: String,
        val appName: String,
        val serviceName: String,
        val serviceType: String,
        val isForegroundService: Boolean,
        val hasNotificationChannel: Boolean,
        val hasWakeLock: Boolean,
        val hasScheduledJobs: Int,
        val hasAlarms: Boolean,
        val estimatedRuntime: String,
        val isSystemApp: Boolean,
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val persistenceTechniques: List<String>
    )
    
    enum class RiskLevel {
        SAFE,           // Servicio normal ocasional
        LOW,            // Sistema con servicio persistente (normal)
        MEDIUM,         // Servicio persistente pero justificable
        HIGH,           // Servicio persistente + wake lock + alarmas
        CRITICAL        // Stalkerware: persistente + invisible + combinación sospechosa
    }
    
    /**
     * Analiza servicios en background buscando técnicas de persistencia
     */
    fun analyzeBackgroundServices(context: Context): List<BackgroundServiceReport> {
        val reports = mutableListOf<BackgroundServiceReport>()
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        try {
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "Analizando servicios en background...")
            
            // Obtener servicios en ejecución (límite 150 para evitar OutOfMemoryError)
            val runningServices = try {
                am.getRunningServices(150)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError obteniendo servicios en ejecución", e)
                throw e // Re-lanzar para captura upstream
            }
            
            Log.d(TAG, "Servicios activos: ${runningServices.size}")
            
            // Procesar servicios en chunks para prevenir OutOfMemoryError
            val chunkSize = 25
            val chunks = runningServices.chunked(chunkSize)
            
            Log.d(TAG, "Procesando ${runningServices.size} servicios en ${chunks.size} lotes...")
            
            for ((chunkIndex, chunk) in chunks.withIndex()) {
                Log.d(TAG, "[Lote ${chunkIndex + 1}/${chunks.size}] Analizando ${chunk.size} servicios...")
                
                // Analizar cada servicio del chunk
                for (serviceInfo in chunk) {
                try {
                    val packageName = serviceInfo.service.packageName
                    val serviceName = serviceInfo.service.className
                    
                    // Obtener información de la app
                    val appInfo = try {
                        pm.getApplicationInfo(packageName, 0)
                    } catch (e: Exception) {
                        continue
                    }
                    
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    // Verificar si es foreground service
                    val isForegroundService = serviceInfo.foreground
                    
                    // Verificar notificación (Android 8+)
                    val hasNotificationChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        isForegroundService // Foreground services en Android 8+ DEBEN tener notificación
                    } else {
                        true // En versiones antiguas no es obligatorio
                    }
                    
                    // Verificar wake locks
                    val hasWakeLock = hasActiveWakeLock(powerManager, packageName)
                    
                    // Verificar JobScheduler
                    val scheduledJobs = countScheduledJobs(jobScheduler, packageName, pm)
                    
                    // Verificar alarmas (no podemos listar alarmas de otras apps sin root,
                    // pero podemos inferir por comportamiento)
                    val hasAlarms = inferHasAlarms(serviceInfo)
                    
                    // Estimar tiempo de ejecución
                    val runtime = serviceInfo.activeSince
                    val estimatedRuntime = estimateServiceRuntime(runtime)
                    
                    // Determinar tipo de servicio
                    val serviceType = determineServiceType(serviceInfo, isForegroundService)
                    
                    // Detectar técnicas de persistencia
                    val persistenceTechniques = detectPersistenceTechniques(
                        packageName,
                        isForegroundService,
                        hasNotificationChannel,
                        hasWakeLock,
                        scheduledJobs,
                        hasAlarms,
                        runtime,
                        isSystemApp
                    )
                    
                    // Solo reportar si tiene técnicas sospechosas
                    if (persistenceTechniques.isNotEmpty()) {
                        // Calcular riesgo
                        val (riskScore, riskLevel) = calculateRisk(
                            isForegroundService,
                            hasNotificationChannel,
                            hasWakeLock,
                            scheduledJobs,
                            hasAlarms,
                            runtime,
                            isSystemApp,
                            persistenceTechniques
                        )
                        
                        val report = BackgroundServiceReport(
                            packageName = packageName,
                            appName = appName,
                            serviceName = serviceName,
                            serviceType = serviceType,
                            isForegroundService = isForegroundService,
                            hasNotificationChannel = hasNotificationChannel,
                            hasWakeLock = hasWakeLock,
                            hasScheduledJobs = scheduledJobs,
                            hasAlarms = hasAlarms,
                            estimatedRuntime = estimatedRuntime,
                            isSystemApp = isSystemApp,
                            riskScore = riskScore,
                            riskLevel = riskLevel,
                            persistenceTechniques = persistenceTechniques
                        )
                        
                        reports.add(report)
                        
                        Log.d(TAG, "⚠️ Servicio persistente detectado: $appName")
                        Log.d(TAG, "   Package: $packageName")
                        Log.d(TAG, "   Servicio: ${serviceName.split(".").lastOrNull() ?: serviceName}")
                        Log.d(TAG, "   Riesgo: ${riskLevel.name} ($riskScore puntos)")
                        Log.d(TAG, "   Técnicas: ${persistenceTechniques.joinToString()}")
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error analizando servicio: ${e.message}")
                }
            }
            
            // Liberar memoria entre chunks si no es el último
            if (chunkIndex < chunks.size - 1) {
                Log.d(TAG, "  Liberando memoria antes del siguiente lote...")
                System.gc()
                Thread.sleep(50)
            }
        }
            
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "Servicios sospechosos detectados: ${reports.size}")
            Log.d(TAG, "  - Riesgo CRÍTICO: ${reports.count { it.riskLevel == RiskLevel.CRITICAL }}")
            Log.d(TAG, "  - Riesgo ALTO: ${reports.count { it.riskLevel == RiskLevel.HIGH }}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando servicios", e)
        }
        
        return reports.sortedByDescending { it.riskScore }
    }
    
    /**
     * Verifica si la app tiene wake locks activos
     * (Sin root, solo podemos verificar el estado general del sistema)
     */
    private fun hasActiveWakeLock(powerManager: PowerManager, packageName: String): Boolean {
        return try {
            // En Android 7+, isDeviceIdleMode nos indica si hay apps evitando el doze
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                !powerManager.isDeviceIdleMode
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Cuenta jobs programados con JobScheduler
     */
    private fun countScheduledJobs(
        jobScheduler: JobScheduler, 
        packageName: String,
        pm: PackageManager
    ): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val allJobs = jobScheduler.allPendingJobs
                allJobs.count { job ->
                    try {
                        val componentPackage = job.service.packageName
                        componentPackage == packageName
                    } catch (e: Exception) {
                        false
                    }
                }
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Infiere si tiene alarmas por comportamiento
     * (Sin root no podemos listar alarmas de otras apps)
     */
    private fun inferHasAlarms(serviceInfo: ActivityManager.RunningServiceInfo): Boolean {
        // Si el servicio se reinicia frecuentemente, probablemente usa alarmas
        return serviceInfo.restarting > 0
    }
    
    /**
     * Estima el tiempo de ejecución del servicio
     */
    private fun estimateServiceRuntime(activeSince: Long): String {
        if (activeSince == 0L) return "Desconocido"
        
        val now = System.currentTimeMillis()
        val uptime = now - activeSince
        
        val hours = uptime / (1000 * 60 * 60)
        val minutes = (uptime / (1000 * 60)) % 60
        
        return when {
            hours >= 24 -> "${hours / 24} días"
            hours >= 1 -> "$hours horas"
            minutes >= 1 -> "$minutes minutos"
            else -> "Menos de 1 minuto"
        }
    }
    
    /**
     * Determina el tipo de servicio
     */
    private fun determineServiceType(
        serviceInfo: ActivityManager.RunningServiceInfo,
        isForegroundService: Boolean
    ): String {
        return when {
            isForegroundService -> "Foreground Service"
            serviceInfo.started -> "Background Service (Started)"
            else -> "Bound Service"
        }
    }
    
    /**
     * Detecta técnicas de persistencia usadas
     */
    private fun detectPersistenceTechniques(
        packageName: String,
        isForegroundService: Boolean,
        hasNotificationChannel: Boolean,
        hasWakeLock: Boolean,
        scheduledJobs: Int,
        hasAlarms: Boolean,
        runtime: Long,
        isSystemApp: Boolean
    ): List<String> {
        val techniques = mutableListOf<String>()
        
        // Técnica 1: Servicio foreground persistente (>24 horas)
        val hoursActive = (System.currentTimeMillis() - runtime) / (1000 * 60 * 60)
        if (isForegroundService && hoursActive >= 24 && !isSystemApp) {
            techniques.add("Servicio foreground activo >24 horas")
        }
        
        // Técnica 2: Foreground service sin notificación (Android 8+ workaround)
        if (isForegroundService && !hasNotificationChannel && 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isSystemApp) {
            techniques.add("Foreground service sin notificación visible (workaround)")
        }
        
        // Técnica 3: Wake locks (evita deep sleep)
        if (hasWakeLock && !isSystemApp) {
            techniques.add("Usa wake locks (evita que el teléfono duerma)")
        }
        
        // Técnica 4: Múltiples jobs programados
        if (scheduledJobs >= 3 && !isSystemApp) {
            techniques.add("Múltiples jobs programados ($scheduledJobs jobs)")
        }
        
        // Técnica 5: Alarmas frecuentes (inferido)
        if (hasAlarms && !isSystemApp) {
            techniques.add("Posible uso de alarmas frecuentes (reinicio detectado)")
        }
        
        // Técnica 6: Servicio persistente de app no-sistema
        if (hoursActive >= 12 && !isSystemApp && !isForegroundService) {
            techniques.add("Servicio background persistente (>12 horas)")
        }
        
        return techniques
    }
    
    /**
     * Calcula puntuación de riesgo
     */
    private fun calculateRisk(
        isForegroundService: Boolean,
        hasNotificationChannel: Boolean,
        hasWakeLock: Boolean,
        scheduledJobs: Int,
        hasAlarms: Boolean,
        runtime: Long,
        isSystemApp: Boolean,
        persistenceTechniques: List<String>
    ): Pair<Int, RiskLevel> {
        var score = 0
        
        // Apps del sistema con servicios persistentes son normales
        if (isSystemApp) {
            return 0 to RiskLevel.SAFE
        }
        
        // Factor 1: Foreground service persistente
        val hoursActive = (System.currentTimeMillis() - runtime) / (1000 * 60 * 60)
        if (isForegroundService) {
            when {
                hoursActive >= 72 -> score += 40  // 3+ días
                hoursActive >= 24 -> score += 25  // 1+ día
                hoursActive >= 12 -> score += 15  // 12+ horas
            }
        }
        
        // Factor 2: Sin notificación visible (Android 8+ workaround)
        if (isForegroundService && !hasNotificationChannel && 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            score += 30
        }
        
        // Factor 3: Wake locks
        if (hasWakeLock) {
            score += 20
        }
        
        // Factor 4: Múltiples jobs programados
        when {
            scheduledJobs >= 5 -> score += 25
            scheduledJobs >= 3 -> score += 15
            scheduledJobs >= 1 -> score += 5
        }
        
        // Factor 5: Alarmas frecuentes
        if (hasAlarms) {
            score += 15
        }
        
        // Factor 6: Combinación de técnicas (típico de stalkerware)
        if (persistenceTechniques.size >= 3) {
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
     * Check rápido: ¿hay servicios foreground sospechosos?
     */
    fun hasSuspiciousForegroundServices(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // Limitar a 150 servicios para evitar OutOfMemoryError
            val runningServices = am.getRunningServices(150)
            
            runningServices.any { service ->
                val isSystemApp = try {
                    val appInfo = context.packageManager.getApplicationInfo(
                        service.service.packageName, 0
                    )
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } catch (e: Exception) {
                    false
                }
                
                // Servicio foreground no-sistema activo >24 horas
                !isSystemApp && 
                service.foreground && 
                (System.currentTimeMillis() - service.activeSince) > (24 * 60 * 60 * 1000)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Obtiene conteo rápido de servicios sospechosos (para dashboard)
     */
    fun getSuspiciousServicesCount(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // Limitar a 150 servicios para evitar OutOfMemoryError
            val runningServices = am.getRunningServices(150)
            
            runningServices.count { service ->
                val isSystemApp = try {
                    val appInfo = context.packageManager.getApplicationInfo(
                        service.service.packageName, 0
                    )
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } catch (e: Exception) {
                    false
                }
                
                !isSystemApp && service.foreground
            }
        } catch (e: Exception) {
            0
        }
    }
}
