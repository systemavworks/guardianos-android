// app/src/main/java/com/guardianos/core/audit/detector/TrackerDetector.kt
package com.guardianos.core.audit.detector

import com.guardianos.core.audit.api.ExodusClient
import com.guardianos.core.audit.model.AppTrackerReport
import com.guardianos.core.audit.model.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class TrackerDetector {
    suspend fun detectTrackers(packageName: String): AppTrackerReport {
        return try {
            withContext(Dispatchers.IO) {
                // Llamada a API pública de Exodus Privacy
                val report = ExodusClient.instance.getAppReport(packageName)
                
                // Mapeo de respuesta a modelo local
                val trackers = report.trackers.values.map { trackerData ->
                    Tracker(
                        name = trackerData.name,
                        description = trackerData.description,
                        categories = trackerData.categories
                    )
                }
                
                AppTrackerReport(
                    packageName = packageName,
                    trackerCount = trackers.size,
                    trackers = trackers
                )
            }
        } catch (e: UnknownHostException) {
            // Sin conexión a internet → offline-first
            AppTrackerReport(packageName, 0, emptyList())
        } catch (e: TimeoutException) {
            // Timeout → offline-first
            AppTrackerReport(packageName, 0, emptyList())
        } catch (e: Exception) {
            // Cualquier error → nunca romper la app
            AppTrackerReport(packageName, 0, emptyList())
        }
    }
    
    // Método rápido para UI (sin bloquear hilo principal)
    fun getTrackerCountOffline(packageName: String): Int {
        // En versión Pro: aquí iría caché Room
        return -1 // -1 = "desconocido" (mostrar "❓" en UI)
    }
}
