package com.guardianos.core.pro

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guardianos.core.domain.model.AppAudit
import java.io.File

/**
 * Comparativa de escaneos - Función PRO.
 * 
 * Permite comparar dos escaneos para detectar:
 * - Apps nuevas instaladas
 * - Apps eliminadas
 * - Cambios en permisos
 * - Nuevos trackers detectados
 * - Cambios en puntuación de riesgo
 */
object ScanComparator {
    
    /**
     * Compara dos escaneos y devuelve los cambios detectados.
     */
    fun compareScan(oldScan: List<AppAudit>, newScan: List<AppAudit>): ScanComparison {
        val oldMap = oldScan.associateBy { it.packageName }
        val newMap = newScan.associateBy { it.packageName }
        
        // Apps nuevas
        val newApps = newScan.filter { it.packageName !in oldMap.keys }
        
        // Apps eliminadas
        val removedApps = oldScan.filter { it.packageName !in newMap.keys }
        
        // Apps modificadas
        val modifiedApps = mutableListOf<AppChange>()
        
        newScan.forEach { newApp ->
            val oldApp = oldMap[newApp.packageName]
            if (oldApp != null) {
                val changes = detectChanges(oldApp, newApp)
                if (changes.isNotEmpty()) {
                    modifiedApps.add(AppChange(
                        packageName = newApp.packageName,
                        appName = newApp.appName,
                        changes = changes
                    ))
                }
            }
        }
        
        return ScanComparison(
            newApps = newApps,
            removedApps = removedApps,
            modifiedApps = modifiedApps,
            totalChanges = newApps.size + removedApps.size + modifiedApps.size
        )
    }
    
    /**
     * Detecta cambios específicos entre dos versiones de la misma app.
     */
    private fun detectChanges(oldApp: AppAudit, newApp: AppAudit): List<String> {
        val changes = mutableListOf<String>()
        
        // Cambios en permisos
        val oldPerms = oldApp.permissions.map { it.name }.toSet()
        val newPerms = newApp.permissions.map { it.name }.toSet()
        
        val addedPerms = newPerms - oldPerms
        val removedPerms = oldPerms - newPerms
        
        if (addedPerms.isNotEmpty()) {
            changes.add("➕ ${addedPerms.size} nuevos permisos: ${addedPerms.take(3).joinToString()}")
        }
        
        if (removedPerms.isNotEmpty()) {
            changes.add("➖ ${removedPerms.size} permisos eliminados")
        }
        
        // Cambios en findings (trackers, malware, etc.)
        val oldFindingsCount = oldApp.findings.size
        val newFindingsCount = newApp.findings.size
        
        if (newFindingsCount > oldFindingsCount) {
            changes.add("⚠️ Nuevos problemas detectados: $oldFindingsCount → $newFindingsCount")
        } else if (newFindingsCount < oldFindingsCount) {
            changes.add("✅ Problemas reducidos: $oldFindingsCount → $newFindingsCount")
        }
        
        // Cambios en puntuación de riesgo
        if (newApp.riskScore > oldApp.riskScore) {
            changes.add("🔴 Riesgo aumentado: ${oldApp.riskScore} → ${newApp.riskScore}")
        } else if (newApp.riskScore < oldApp.riskScore) {
            changes.add("🟢 Riesgo reducido: ${oldApp.riskScore} → ${newApp.riskScore}")
        }
        
        // Cambio en fuente de instalación
        if (oldApp.installSource != newApp.installSource) {
            changes.add("📦 Fuente cambió: ${oldApp.installSource} → ${newApp.installSource}")
        }
        
        return changes
    }
    
    /**
     * Genera un resumen textual de la comparación.
     */
    fun generateComparisonSummary(comparison: ScanComparison): String {
        return buildString {
            appendLine("📊 COMPARATIVA DE ESCANEOS")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            
            if (comparison.totalChanges == 0) {
                appendLine("✅ No se detectaron cambios")
                return@buildString
            }
            
            appendLine("📈 Total de cambios: ${comparison.totalChanges}")
            appendLine()
            
            if (comparison.newApps.isNotEmpty()) {
                appendLine("➕ APPS NUEVAS (${comparison.newApps.size}):")
                comparison.newApps.take(5).forEach { app ->
                    appendLine("   • ${app.appName}")
                    appendLine("     Riesgo: ${app.riskScore}/100")
                }
                if (comparison.newApps.size > 5) {
                    appendLine("   ... y ${comparison.newApps.size - 5} más")
                }
                appendLine()
            }
            
            if (comparison.removedApps.isNotEmpty()) {
                appendLine("➖ APPS ELIMINADAS (${comparison.removedApps.size}):")
                comparison.removedApps.take(5).forEach { app ->
                    appendLine("   • ${app.appName}")
                }
                if (comparison.removedApps.size > 5) {
                    appendLine("   ... y ${comparison.removedApps.size - 5} más")
                }
                appendLine()
            }
            
            if (comparison.modifiedApps.isNotEmpty()) {
                appendLine("🔄 APPS MODIFICADAS (${comparison.modifiedApps.size}):")
                comparison.modifiedApps.take(5).forEach { change ->
                    appendLine("   • ${change.appName}")
                    change.changes.forEach { detail ->
                        appendLine("     $detail")
                    }
                }
                if (comparison.modifiedApps.size > 5) {
                    appendLine("   ... y ${comparison.modifiedApps.size - 5} más")
                }
            }
        }
    }
    
    data class ScanComparison(
        val newApps: List<AppAudit>,
        val removedApps: List<AppAudit>,
        val modifiedApps: List<AppChange>,
        val totalChanges: Int
    )
    
    data class AppChange(
        val packageName: String,
        val appName: String,
        val changes: List<String>
    )
}
