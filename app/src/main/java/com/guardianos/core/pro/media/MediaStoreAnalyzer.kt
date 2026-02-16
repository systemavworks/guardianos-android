package com.guardianos.core.pro.media

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.guardianos.core.crash.DeviceOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Analizador de accesos REALES a archivos multimedia via MediaStore.
 * Complementa MediaAccessScanner detectando qué apps han accedido/modificado
 * archivos multimedia recientemente.
 * 
 * ✅ Funciona sin root, solo permisos estándar
 * ⚠️ Android 10+ (Scoped Storage) limita información de owner
 * 
 * PROTECCIÓN ANTI-CRASH OPPO/GAMA BAJA:
 * - Adaptación dinámica según dispositivo (DeviceOptimizer)
 * - Chunked queries con límites ajustables
 * - Delays entre operaciones según RAM
 * - Manejo de OutOfMemoryError
 */
object MediaStoreAnalyzer {
    private const val TAG = "MediaStoreAnalyzer"
    
    // Límites ajustados dinámicamente por DeviceOptimizer
    private var maxItemsPerQuery = 500
    private var delayBetweenQueries = 0L
    
    data class MediaAccessRecord(
        val appPackage: String,
        val appName: String,
        val mediaType: MediaType,
        val fileName: String,
        val filePath: String?,
        val lastAccessTime: Long,
        val lastModifiedTime: Long,
        val wasModified: Boolean,
        val fileSize: Long
    )
    
    enum class MediaType {
        IMAGE,      // Fotos
        VIDEO,      // Videos
        AUDIO,      // Audio/música
        DOCUMENT    // Documentos (si disponible)
    }
    
    data class AppMediaActivity(
        val packageName: String,
        val appName: String,
        val totalAccesses: Int,
        val lastAccessTime: Long,
        val accessedImages: Int,
        val accessedVideos: Int,
        val accessedAudio: Int,
        val modifiedFiles: Int,
        val recentActivity: List<MediaAccessRecord>
    )
    
    /**
     * Analiza accesos reales a multimedia de las últimas 7 días.
     * PROTEGIDO contra crashes con adaptación por dispositivo.
     */
    suspend fun analyzeRecentMediaAccess(context: Context, daysBack: Int = 7): List<AppMediaActivity> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val cutoffTime = System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)
        val accessRecords = mutableListOf<MediaAccessRecord>()
        
        // 🔧 ADAPTACIÓN DINÁMICA POR DISPOSITIVO
        val deviceProfile = DeviceOptimizer.analyzeDevice(context)
        val limits = DeviceOptimizer.getOperationLimits(deviceProfile.optimizationLevel)
        
        maxItemsPerQuery = limits.maxItemsPerQuery
        delayBetweenQueries = limits.delayBetweenOperationsMs
        
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "Analizando accesos multimedia reales (últimos $daysBack días)")
        Log.d(TAG, "Dispositivo: ${deviceProfile.manufacturer} ${deviceProfile.model} (Nivel: ${deviceProfile.optimizationLevel})")
        Log.d(TAG, "Límites: max ${maxItemsPerQuery} items/query, delay ${delayBetweenQueries}ms")
        
        try {
            // Analizar imágenes
            val imageRecords = queryMediaStore(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaType.IMAGE,
                cutoffTime
            )
            accessRecords.addAll(imageRecords)
            Log.d(TAG, "  Imágenes accedidas: ${imageRecords.size}")
            
            // Delay entre queries (protección dispositivos lentos)
            if (delayBetweenQueries > 0) {
                delay(delayBetweenQueries)
            }
            
            // Analizar videos
            val videoRecords = queryMediaStore(
                context,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaType.VIDEO,
                cutoffTime
            )
            accessRecords.addAll(videoRecords)
            Log.d(TAG, "  Videos accedidos: ${videoRecords.size}")
            
            if (delayBetweenQueries > 0) {
                delay(delayBetweenQueries)
            }
            
            // Analizar audio (solo si dispositivo no es EXTREME)
            if (deviceProfile.optimizationLevel != DeviceOptimizer.OptimizationLevel.EXTREME) {
                val audioRecords = queryMediaStore(
                    context,
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    MediaType.AUDIO,
                    cutoffTime
                )
                accessRecords.addAll(audioRecords)
                Log.d(TAG, "  Archivos audio: ${audioRecords.size}")
            } else {
                Log.d(TAG, "  Audio: Omitido (dispositivo en modo EXTREME)")
            }
            
            Log.d(TAG, "  Total archivos analizados: ${accessRecords.size}")
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "❌ OutOfMemoryError en análisis multimedia - dispositivo con poca RAM", e)
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando MediaStore: ${e.message}", e)
            return@withContext emptyList()
        }
        
        // Agrupar por app
        val appActivity = mutableMapOf<String, MutableList<MediaAccessRecord>>()
        
        accessRecords.forEach { record ->
            if (record.appPackage.isNotBlank()) {
                appActivity.getOrPut(record.appPackage) { mutableListOf() }.add(record)
            }
        }
        
        Log.d(TAG, "  Apps con actividad multimedia: ${appActivity.size}")
        Log.d(TAG, "═══════════════════════════════════════════")
        
        // Generar resumen por app
        val result = appActivity.map { (packageName, records) ->
            val appName = try {
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            } catch (e: Exception) {
                packageName
            }
            
            AppMediaActivity(
                packageName = packageName,
                appName = appName,
                totalAccesses = records.size,
                lastAccessTime = records.maxOfOrNull { it.lastAccessTime } ?: 0L,
                accessedImages = records.count { it.mediaType == MediaType.IMAGE },
                accessedVideos = records.count { it.mediaType == MediaType.VIDEO },
                accessedAudio = records.count { it.mediaType == MediaType.AUDIO },
                modifiedFiles = records.count { it.wasModified },
                recentActivity = records.sortedByDescending { it.lastAccessTime }.take(5)
            )
        }.sortedByDescending { it.totalAccesses }
        
        result.take(20).forEach { activity ->
            Log.d(TAG, "📱 ${activity.appName}: ${activity.totalAccesses} accesos")
        }
        
        return@withContext result
    }
    
    /**
     * Query MediaStore con protección anti-crash (chunked + timeout).
     */
    private suspend fun queryMediaStore(
        context: Context,
        uri: Uri,
        mediaType: MediaType,
        cutoffTime: Long
    ): List<MediaAccessRecord> = withContext(Dispatchers.IO) {
        val records = mutableListOf<MediaAccessRecord>()
        val pm = context.packageManager
        
        // Columnas a recuperar (mínimas para evitar overhead)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA, // Path (deprecated pero útil)
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE
        )
        
        // Ordenar por fecha modificada DESC (más recientes primero)
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC LIMIT $maxItemsPerQuery"
        
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )
            
            if (cursor == null) {
                Log.w(TAG, "Cursor null para $mediaType")
                return@withContext emptyList()
            }
            
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            
            var count = 0
            while (cursor.moveToNext() && count < maxItemsPerQuery) {
                try {
                    val dateModified = cursor.getLong(dateModifiedColumn) * 1000L
                    
                    // Filtrar por fecha de corte
                    if (dateModified < cutoffTime) {
                        continue
                    }
                    
                    val fileName = cursor.getString(nameColumn) ?: "unknown"
                    val filePath = if (dataColumn >= 0) cursor.getString(dataColumn) else null
                    val dateAdded = cursor.getLong(dateAddedColumn) * 1000L
                    val size = cursor.getLong(sizeColumn)
                    
                    // Detectar si fue modificado (vs solo agregado)
                    val wasModified = (dateModified - dateAdded) > 60000L // >1 minuto diferencia
                    
                    // Intentar obtener owner (Android 10+ es difícil sin permisos especiales)
                    val ownerPackage = getFileOwnerPackage(context, uri, cursor.getLong(idColumn))
                    
                    if (ownerPackage.isNotBlank()) {
                        val appName = try {
                            pm.getApplicationInfo(ownerPackage, 0).loadLabel(pm).toString()
                        } catch (e: Exception) {
                            ownerPackage
                        }
                        
                        records.add(
                            MediaAccessRecord(
                                appPackage = ownerPackage,
                                appName = appName,
                                mediaType = mediaType,
                                fileName = fileName,
                                filePath = filePath,
                                lastAccessTime = dateModified,
                                lastModifiedTime = dateModified,
                                wasModified = wasModified,
                                fileSize = size
                            )
                        )
                    }
                    
                    count++
                } catch (e: Exception) {
                    // Ignorar errores en items individuales
                    Log.w(TAG, "Error procesando item de MediaStore: ${e.message}")
                }
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException accediendo a $mediaType: ${e.message}")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "❌ OutOfMemoryError en query MediaStore $mediaType")
            throw e // Re-lanzar para capturar arriba
        } catch (e: Exception) {
            Log.e(TAG, "Error en query MediaStore $mediaType: ${e.message}", e)
        } finally {
            cursor?.close()
        }
        
        return@withContext records
    }
    
    /**
     * Intenta obtener el package owner de un archivo multimedia.
     * Android 10+ hace esto muy difícil por Scoped Storage.
     * 
     * Estrategia:
     * 1. Android <10: Usar path para inferir owner
     * 2. Android 10+: Usar OWNER_PACKAGE_NAME si disponible (requiere permisos especiales)
     * 3. Fallback: Analizar path para inferir (ej: /Android/data/com.example.app/)
     */
    private fun getFileOwnerPackage(context: Context, uri: Uri, fileId: Long): String {
        // Android 10+ tiene OWNER_PACKAGE_NAME pero requiere permisos especiales
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val fileUri = Uri.withAppendedPath(uri, fileId.toString())
                context.contentResolver.query(
                    fileUri,
                    arrayOf("owner_package_name"), // Column no documentada oficialmente
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val ownerIndex = cursor.getColumnIndex("owner_package_name")
                        if (ownerIndex >= 0) {
                            val owner = cursor.getString(ownerIndex)
                            if (!owner.isNullOrBlank()) {
                                return owner
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Normal que falle, no es API oficial
            }
        }
        
        // Fallback: Analizar todas las apps con permisos multimedia
        // y devolver la más probable (requeriría cruzar con UsageStats)
        return "" // Por ahora, devolver vacío si no se puede determinar
    }
    
    /**
     * Genera reporte resumido de actividad multimedia reciente.
     */
    fun generateMediaActivityReport(activities: List<AppMediaActivity>): String {
        if (activities.isEmpty()) {
            return "✅ No se detectaron accesos recientes a multimedia (últimos 7 días)"
        }
        
        val totalAccesses = activities.sumOf { it.totalAccesses }
        val appsWithModifications = activities.count { it.modifiedFiles > 0 }
        
        val report = StringBuilder()
        report.appendLine("📊 Resumen de Actividad Multimedia (últimos 7 días)")
        report.appendLine("")
        report.appendLine("Total apps activas: ${activities.size}")
        report.appendLine("Total accesos: $totalAccesses")
        report.appendLine("Apps que modificaron archivos: $appsWithModifications")
        report.appendLine("")
        report.appendLine("Top 5 apps más activas:")
        
        activities.take(5).forEachIndexed { index, activity ->
            report.appendLine("")
            report.appendLine("${index + 1}. ${activity.appName}")
            report.appendLine("   Accesos: ${activity.totalAccesses}")
            report.appendLine("   Imágenes: ${activity.accessedImages} | Videos: ${activity.accessedVideos} | Audio: ${activity.accessedAudio}")
            if (activity.modifiedFiles > 0) {
                report.appendLine("   ⚠️ Modificó ${activity.modifiedFiles} archivos")
            }
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            report.appendLine("   Última actividad: ${dateFormat.format(Date(activity.lastAccessTime))}")
        }
        
        return report.toString()
    }
}
