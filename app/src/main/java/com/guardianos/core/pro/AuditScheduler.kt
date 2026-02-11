package com.guardianos.core.pro

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object AuditScheduler {
    fun scheduleWeeklyAudit(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<AuditWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "weekly_audit",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

class AuditWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            // Lógica: lanzar escaneo y guardar historial
            val auditor = com.guardianos.core.audit.AppAuditor()
            val apps = auditor.auditApps(applicationContext, com.guardianos.core.domain.model.AuditMode.QUICK)
            
            val saveResult = com.guardianos.core.pro.ScanHistory.saveScan(applicationContext, apps)
            
            if (saveResult.isSuccess) {
                // Aquí podrías notificar al usuario
                Result.success()
            } else {
                android.util.Log.e("AuditWorker", "Error guardando historial: ${saveResult.exceptionOrNull()?.message}")
                Result.failure()
            }
        } catch (e: Exception) {
            android.util.Log.e("AuditWorker", "Error en auditoría programada", e)
            Result.failure()
        }
    }
}
