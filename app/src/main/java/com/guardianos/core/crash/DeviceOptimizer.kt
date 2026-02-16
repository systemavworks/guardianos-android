package com.guardianos.core.crash

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Optimizador específico por fabricante y modelo.
 * Detecta dispositivos problemáticos (OPPO, Xiaomi, etc.) y ajusta
 * límites de operaciones para prevenir crashes.
 * 
 * DISPOSITIVOS CONOCIDOS CON PROBLEMAS:
 * - OPPO A80 (Android 15, ColorOS): Agresivo battery killer, limita RAM
 * - Xiaomi con MIUI: Restricciones de background
 * - Samsung < 2GB RAM: OutOfMemoryError frecuentes
 */
object DeviceOptimizer {
    private const val TAG = "DeviceOptimizer"
    
    data class DeviceProfile(
        val manufacturer: String,
        val model: String,
        val androidVersion: Int,
        val totalRamMB: Int,
        val availableRamMB: Int,
        val isLowEndDevice: Boolean,
        val isProblematicModel: Boolean,
        val optimizationLevel: OptimizationLevel,
        val recommendations: List<String>
    )
    
    enum class OptimizationLevel {
        NONE,           // Dispositivo moderno, sin restricciones
        LOW,            // Ligeras optimizaciones
        MEDIUM,         // Reducir operaciones concurrentes
        HIGH,           // Escaneos chunked, delays entre operaciones
        EXTREME         // Mínimo absoluto, deshabilitar funciones pesadas
    }
    
    /**
     * Analiza el dispositivo actual y devuelve perfil de optimización.
     */
    fun analyzeDevice(context: Context): DeviceProfile {
        val manufacturer = Build.MANUFACTURER.uppercase()
        val model = Build.MODEL.uppercase()
        val androidVersion = Build.VERSION.SDK_INT
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamMB = (memoryInfo.totalMem / 1024 / 1024).toInt()
        val availableRamMB = (memoryInfo.availMem / 1024 / 1024).toInt()
        val isLowMemory = memoryInfo.lowMemory
        
        // Detectar dispositivos < 2GB RAM (bajo rendimiento)
        val isLowEndDevice = totalRamMB < 2048
        
        // Detectar modelos problemáticos conocidos
        val isProblematicModel = isProblematicDevice(manufacturer, model, androidVersion)
        
        // Calcular nivel de optimización necesario
        val optimizationLevel = calculateOptimizationLevel(
            totalRamMB,
            availableRamMB,
            isLowMemory,
            isLowEndDevice,
            isProblematicModel
        )
        
        // Generar recomendaciones
        val recommendations = generateRecommendations(
            manufacturer,
            model,
            totalRamMB,
            isLowEndDevice,
            isProblematicModel,
            optimizationLevel
        )
        
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "Análisis de dispositivo:")
        Log.d(TAG, "  Fabricante: $manufacturer")
        Log.d(TAG, "  Modelo: $model")
        Log.d(TAG, "  Android: $androidVersion (${Build.VERSION.RELEASE})")
        Log.d(TAG, "  RAM Total: ${totalRamMB}MB")
        Log.d(TAG, "  RAM Disponible: ${availableRamMB}MB")
        Log.d(TAG, "  Bajo rendimiento: $isLowEndDevice")
        Log.d(TAG, "  Modelo problemático: $isProblematicModel")
        Log.d(TAG, "  Nivel optimización: $optimizationLevel")
        Log.d(TAG, "═══════════════════════════════════════════")
        
        return DeviceProfile(
            manufacturer = manufacturer,
            model = model,
            androidVersion = androidVersion,
            totalRamMB = totalRamMB,
            availableRamMB = availableRamMB,
            isLowEndDevice = isLowEndDevice,
            isProblematicModel = isProblematicModel,
            optimizationLevel = optimizationLevel,
            recommendations = recommendations
        )
    }
    
    /**
     * Detecta modelos conocidos con problemas.
     */
    private fun isProblematicDevice(manufacturer: String, model: String, androidVersion: Int): Boolean {
        // OPPO con ColorOS (especialmente A series)
        if (manufacturer.contains("OPPO")) {
            // OPPO A80, A78, A77, etc. con Android 15
            if (model.startsWith("CPH") || model.contains("OPPO A")) {
                if (androidVersion >= 35) { // Android 15+
                    Log.w(TAG, "⚠️ OPPO A-series con Android 15 detectado - aplicar optimizaciones EXTREME")
                    return true
                }
                return true // Cualquier OPPO A-series es problemático
            }
        }
        
        // Xiaomi con MIUI agresivo
        if (manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI")) {
            // Redmi Note series < 4GB RAM
            if (model.contains("REDMI") && !model.contains("NOTE 12")) {
                return true
            }
        }
        
        // Samsung Galaxy A series antiguos (A10, A20, A30)
        if (manufacturer.contains("SAMSUNG")) {
            if (model.contains("SM-A105") || model.contains("SM-A205") || model.contains("SM-A305")) {
                return true
            }
        }
        
        // Realme C series (bajo costo)
        if (manufacturer.contains("REALME")) {
            if (model.contains("RMX") && model.contains("C")) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Calcula nivel de optimización necesario.
     */
    private fun calculateOptimizationLevel(
        totalRamMB: Int,
        availableRamMB: Int,
        isLowMemory: Boolean,
        isLowEndDevice: Boolean,
        isProblematicModel: Boolean
    ): OptimizationLevel {
        // EXTREME: RAM crítica o modelo muy problemático
        if (isLowMemory || availableRamMB < 256 || (isProblematicModel && totalRamMB < 3072)) {
            return OptimizationLevel.EXTREME
        }
        
        // HIGH: Dispositivo de gama baja o modelo problemático
        if (isLowEndDevice || isProblematicModel) {
            return OptimizationLevel.HIGH
        }
        
        // MEDIUM: RAM limitada pero aceptable
        if (totalRamMB < 4096 || availableRamMB < 512) {
            return OptimizationLevel.MEDIUM
        }
        
        // LOW: Dispositivo decente pero con alguna limitación
        if (totalRamMB < 6144) {
            return OptimizationLevel.LOW
        }
        
        // NONE: Dispositivo moderno sin restricciones
        return OptimizationLevel.NONE
    }
    
    /**
     * Genera recomendaciones específicas por dispositivo.
     */
    private fun generateRecommendations(
        manufacturer: String,
        model: String,
        totalRamMB: Int,
        isLowEndDevice: Boolean,
        isProblematicModel: Boolean,
        optimizationLevel: OptimizationLevel
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (manufacturer.contains("OPPO")) {
            recommendations.add("⚙️ OPPO detectado: Ve a Ajustes → Batería → Optimización de batería → GuardianOS → No optimizar")
            recommendations.add("🔓 ColorOS puede matar apps en background. Considera añadir GuardianOS a apps protegidas")
        }
        
        if (manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI")) {
            recommendations.add("⚙️ MIUI detectado: Ve a Ajustes → Batería → Ahorro de energía → GuardianOS → Sin restricciones")
            recommendations.add("🔐 Activa 'Inicio automático' para GuardianOS en Configuración → Permisos")
        }
        
        if (isLowEndDevice) {
            recommendations.add("📉 Dispositivo con RAM limitada (${totalRamMB}MB): Cierra apps en background antes de escaneos")
        }
        
        if (optimizationLevel >= OptimizationLevel.HIGH) {
            recommendations.add("🐌 Los escaneos serán más lentos pero más seguros en este dispositivo")
        }
        
        if (optimizationLevel == OptimizationLevel.EXTREME) {
            recommendations.add("⚠️ Algunas funciones avanzadas pueden estar limitadas para prevenir crashes")
        }
        
        return recommendations
    }
    
    /**
     * Obtiene límites de operaciones según el dispositivo.
     */
    data class OperationLimits(
        val maxConcurrentScans: Int,
        val maxItemsPerQuery: Int,
        val delayBetweenOperationsMs: Long,
        val enableHeavyFeatures: Boolean,
        val chunkSize: Int
    )
    
    fun getOperationLimits(optimizationLevel: OptimizationLevel): OperationLimits {
        return when (optimizationLevel) {
            OptimizationLevel.NONE -> OperationLimits(
                maxConcurrentScans = 5,
                maxItemsPerQuery = 1000,
                delayBetweenOperationsMs = 0,
                enableHeavyFeatures = true,
                chunkSize = 100
            )
            OptimizationLevel.LOW -> OperationLimits(
                maxConcurrentScans = 4,
                maxItemsPerQuery = 750,
                delayBetweenOperationsMs = 100,
                enableHeavyFeatures = true,
                chunkSize = 75
            )
            OptimizationLevel.MEDIUM -> OperationLimits(
                maxConcurrentScans = 3,
                maxItemsPerQuery = 500,
                delayBetweenOperationsMs = 250,
                enableHeavyFeatures = true,
                chunkSize = 50
            )
            OptimizationLevel.HIGH -> OperationLimits(
                maxConcurrentScans = 2,
                maxItemsPerQuery = 300,
                delayBetweenOperationsMs = 500,
                enableHeavyFeatures = false,
                chunkSize = 25
            )
            OptimizationLevel.EXTREME -> OperationLimits(
                maxConcurrentScans = 1,
                maxItemsPerQuery = 150,
                delayBetweenOperationsMs = 1000,
                enableHeavyFeatures = false,
                chunkSize = 10
            )
        }
    }
}
