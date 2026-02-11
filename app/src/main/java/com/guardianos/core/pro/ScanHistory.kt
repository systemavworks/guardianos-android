package com.guardianos.core.pro

import android.content.Context
import android.util.Log
import com.guardianos.core.domain.model.AppAudit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Gestor de historial de escaneos (funcionalidad PRO).
 * Mantiene un registro de hasta 30 escaneos para comparación temporal.
 */
object ScanHistory {
    private const val TAG = "ScanHistory"
    private const val HISTORY_FILE = "scan_history.json"
    private const val MAX_HISTORY_ENTRIES = 30
    private val gson = Gson()

    /**
     * Guarda un escaneo en el historial.
     * Mantiene un máximo de MAX_HISTORY_ENTRIES entradas.
     */
    fun saveScan(context: Context, apps: List<AppAudit>): Result<Unit> {
        return try {
            if (apps.isEmpty()) {
                Log.w(TAG, "Intento de guardar escaneo vacío")
                return Result.failure(IllegalArgumentException("El escaneo no puede estar vacío"))
            }
            
            val history = loadHistory(context).getOrDefault(emptyList()).toMutableList()
            history.add(0, ScanEntry(Date().time, apps))
            
            // Limitar el historial a MAX_HISTORY_ENTRIES
            if (history.size > MAX_HISTORY_ENTRIES) {
                history.subList(MAX_HISTORY_ENTRIES, history.size).clear()
            }
            
            val json = gson.toJson(history)
            File(context.filesDir, HISTORY_FILE).writeText(json)
            
            Log.d(TAG, "Escaneo guardado: ${apps.size} apps, historial: ${history.size} entradas")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Error de E/S al guardar historial", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al guardar historial", e)
            Result.failure(e)
        }
    }

    /**
     * Carga el historial de escaneos.
     */
    fun loadHistory(context: Context): Result<List<ScanEntry>> {
        return try {
            val file = File(context.filesDir, HISTORY_FILE)
            if (!file.exists()) {
                return Result.success(emptyList())
            }
            
            val json = file.readText()
            if (json.isBlank()) {
                return Result.success(emptyList())
            }
            
            val type = object : TypeToken<List<ScanEntry>>() {}.type
            val history = gson.fromJson<List<ScanEntry>>(json, type) ?: emptyList()
            
            Log.d(TAG, "Historial cargado: ${history.size} entradas")
            Result.success(history)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Error de sintaxis JSON al cargar historial", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e(TAG, "Error de E/S al cargar historial", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al cargar historial", e)
            Result.failure(e)
        }
    }
    
    /**
     * Elimina el historial completo.
     */
    fun clearHistory(context: Context): Result<Unit> {
        return try {
            val file = File(context.filesDir, HISTORY_FILE)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Historial eliminado")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar historial", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene estadísticas del historial.
     */
    fun getStats(context: Context): Result<HistoryStats> {
        return try {
            val history = loadHistory(context).getOrDefault(emptyList())
            if (history.isEmpty()) {
                return Result.success(HistoryStats(0, 0, null, null))
            }
            
            val stats = HistoryStats(
                totalScans = history.size,
                totalAppsScanned = history.sumOf { it.apps.size },
                firstScanDate = history.lastOrNull()?.timestamp,
                lastScanDate = history.firstOrNull()?.timestamp
            )
            Result.success(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener estadísticas", e)
            Result.failure(e)
        }
    }
}

data class ScanEntry(
    val timestamp: Long,
    val apps: List<AppAudit>
)

data class HistoryStats(
    val totalScans: Int,
    val totalAppsScanned: Int,
    val firstScanDate: Long?,
    val lastScanDate: Long?
)
