package com.guardianos.core.pro

import android.content.Context
import com.guardianos.vault.security.VaultSecurityManager

/**
 * Modo Pánico - Función PRO para emergencias.
 * 
 * Permite al usuario destruir todos los datos sensibles instantáneamente
 * o activar un "modo señuelo" que muestre datos falsos.
 */
object PanicMode {
    
    private const val PREFS_NAME = "panic_settings"
    private const val KEY_PANIC_MODE_ENABLED = "panic_enabled"
    private const val KEY_DECOY_MODE = "decoy_mode"
    private const val KEY_PANIC_PIN = "panic_pin"
    
    /**
     * Configura el código PIN de pánico.
     */
    fun setPanicPin(context: Context, pin: String): Result<Unit> {
        return try {
            if (pin.length != 4 || !pin.all { it.isDigit() }) {
                return Result.failure(IllegalArgumentException("PIN debe ser 4 dígitos"))
            }
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_PANIC_PIN, pin)
                .putBoolean(KEY_PANIC_MODE_ENABLED, true)
                .apply()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Verifica si un PIN es el código de pánico.
     */
    fun isPanicPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_PANIC_MODE_ENABLED, false)
        val panicPin = prefs.getString(KEY_PANIC_PIN, null)
        
        return enabled && pin == panicPin
    }
    
    /**
     * Activa el modo señuelo (muestra datos falsos en lugar de destruir).
     */
    fun enableDecoyMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DECOY_MODE, enabled).apply()
    }
    
    /**
     * Verifica si está en modo señuelo.
     */
    fun isDecoyMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DECOY_MODE, false)
    }
    
    /**
     * Ejecuta la acción de pánico con confirmación adicional.
     */
    fun executePanicAction(context: Context): PanicResult {
        return try {
            val decoyMode = isDecoyMode(context)
            
            if (decoyMode) {
                // Modo señuelo: No borrar, solo marcar para mostrar datos falsos
                val prefs = context.getSharedPreferences("panic_active", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("active", true).apply()
                android.util.Log.i("PanicMode", "Modo señuelo activado")
                PanicResult.DECOY_ACTIVATED
            } else {
                // Modo destrucción: Borrar TODO
                android.util.Log.w("PanicMode", "Iniciando destrucción de datos sensibles")
                
                var success = true
                
                // Destruir vault
                try {
                    VaultSecurityManager.destroyVault(context)
                } catch (e: Exception) {
                    android.util.Log.e("PanicMode", "Error destruyendo vault", e)
                    success = false
                }
                
                // Borrar historial de escaneos
                try {
                    context.getSharedPreferences("scan_history", Context.MODE_PRIVATE).edit().clear().apply()
                    context.filesDir.listFiles()?.forEach { file ->
                        if (file.name.contains("scan_history")) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PanicMode", "Error eliminando historial", e)
                    success = false
                }
                
                // Borrar configuración de pánico (excepto el PIN para poder reactivar)
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val panicPin = prefs.getString(KEY_PANIC_PIN, null)
                    prefs.edit().clear().apply()
                    // Restaurar PIN para poder reactivar
                    if (panicPin != null) {
                        prefs.edit().putString(KEY_PANIC_PIN, panicPin).apply()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PanicMode", "Error limpiando configuración", e)
                }
                
                if (success) {
                    android.util.Log.i("PanicMode", "Datos sensibles destruidos exitosamente")
                    PanicResult.DATA_DESTROYED
                } else {
                    android.util.Log.w("PanicMode", "Destrucción parcial de datos")
                    PanicResult.ERROR
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PanicMode", "Error crítico en modo pánico", e)
            PanicResult.ERROR
        }
    }
    
    /**
     * Verifica si el modo pánico está activo (señuelo).
     */
    fun isPanicActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences("panic_active", Context.MODE_PRIVATE)
        return prefs.getBoolean("active", false)
    }
    
    /**
     * Desactiva el modo pánico (requiere reautenticación).
     */
    fun deactivatePanic(context: Context) {
        val prefs = context.getSharedPreferences("panic_active", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("active", false).apply()
    }
    
    enum class PanicResult {
        DATA_DESTROYED,     // Datos eliminados completamente
        DECOY_ACTIVATED,    // Modo señuelo activado
        ERROR               // Error en la operación
    }
}
