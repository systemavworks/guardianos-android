/*
 * GuardianOS - Ethical digital protection for minors
 * Copyright (C) 2026 Victor Shift Lara
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.guardianos.core.crash

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handler minimalista de crashes sin trackers externos.
 * Filosofía: Logs locales auditables > telemetría opaca.
 */
object CrashHandler {
    private const val TAG = "GUARDIAN_CRASH"
    private const val MAX_CRASH_LOGS = 10 // Rotación automática
    
    fun initialize(context: Context) {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(context, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error crítico al guardar crash log", e)
            }
            
            // Llamar al handler original de Android
            originalHandler?.uncaughtException(thread, throwable)
        }
        
        Log.i(TAG, "✅ CrashHandler ético inicializado (sin trackers)")
    }
    
    private fun saveCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestamp))
        
        val log = buildString {
            appendLine("═══════════════════════════════════════════")
            appendLine("GUARDIANIOS CRASH REPORT")
            appendLine("═══════════════════════════════════════════")
            appendLine("Timestamp: $dateStr ($timestamp)")
            appendLine("Thread: ${thread.name}")
            appendLine("Exception: ${throwable.javaClass.simpleName}")
            appendLine("Message: ${throwable.message ?: "Sin mensaje"}")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(throwable.stackTraceToString())
            appendLine()
            
            // Causa raíz si existe
            var cause = throwable.cause
            var causeLevel = 1
            while (cause != null && causeLevel <= 3) {
                appendLine("Caused by ($causeLevel): ${cause.javaClass.simpleName}")
                appendLine("Message: ${cause.message ?: "Sin mensaje"}")
                appendLine(cause.stackTraceToString())
                appendLine()
                cause = cause.cause
                causeLevel++
            }
            
            appendLine("Device Info:")
            appendLine("  Manufacturer: ${android.os.Build.MANUFACTURER}")
            appendLine("  Model: ${android.os.Build.MODEL}")
            appendLine("  Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("═══════════════════════════════════════════")
        }
        
        // Guardar en filesDir (privado, no requiere permisos)
        val crashDir = File(context.filesDir, "crashes")
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }
        
        val logFile = File(crashDir, "crash-$timestamp.log")
        logFile.writeText(log)
        
        Log.e(TAG, "💥 Crash guardado: ${logFile.absolutePath}")
        
        // Rotación: eliminar logs antiguos
        rotateCrashLogs(crashDir)
    }
    
    private fun rotateCrashLogs(crashDir: File) {
        val logs = crashDir.listFiles { file -> 
            file.name.startsWith("crash-") && file.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() } ?: return
        
        if (logs.size > MAX_CRASH_LOGS) {
            logs.drop(MAX_CRASH_LOGS).forEach { oldLog ->
                oldLog.delete()
                Log.d(TAG, "🗑️ Log antiguo eliminado: ${oldLog.name}")
            }
        }
    }
    
    /**
     * Obtiene los últimos 3 crash logs para debugging.
     * Uso: CrashHandler.getRecentCrashes(context)
     */
    fun getRecentCrashes(context: Context): List<File> {
        val crashDir = File(context.filesDir, "crashes")
        if (!crashDir.exists()) return emptyList()
        
        return crashDir.listFiles { file -> 
            file.name.startsWith("crash-") && file.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() }?.take(3) ?: emptyList()
    }
    
    /**
     * Borra todos los crash logs (opción de usuario).
     */
    fun clearAllCrashLogs(context: Context): Boolean {
        val crashDir = File(context.filesDir, "crashes")
        if (!crashDir.exists()) return true
        
        var deleted = 0
        crashDir.listFiles()?.forEach { file ->
            if (file.delete()) deleted++
        }
        
        Log.i(TAG, "🗑️ $deleted crash logs eliminados por usuario")
        return true
    }
}
