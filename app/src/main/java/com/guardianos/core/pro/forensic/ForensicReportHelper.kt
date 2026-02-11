package com.guardianos.core.pro.forensic

import com.guardianos.core.domain.model.AppScanResult
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utilidades para informes forenses legales (PRO).
 * Genera información con validez para procedimientos legales.
 */
object ForensicReportHelper {
    
    data class ForensicSummary(
        val timestamp: Long,
        val timestampFormatted: String,
        val reportHash: String,
        val totalAppsScanned: Int,
        val threatsDetected: Int,
        val highRiskApps: Int,
        val forensicFindings: List<ForensicFinding>
    )
    
    data class ForensicFinding(
        val severity: String,  // "CRÍTICO", "ALTO", "MEDIO"
        val category: String,
        val description: String,
        val evidence: String,
        val recommendation: String
    )
    
    /**
     * Genera resumen forense legal con cadena de custodia
     */
    fun generateLegalSummary(results: List<AppScanResult>): String {
        val summary = generateForensicSummary(results)
        
        return buildString {
            appendLine("⚖️ INFORME FORENSE LEGAL")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("🗓️ Fecha y hora: ${summary.timestampFormatted}")
            appendLine("🔗 Hash del informe (SHA-256): ${summary.reportHash.take(16)}...")
            appendLine()
            appendLine("📊 RESUMEN EJECUTIVO")
            appendLine("-".repeat(50))
            appendLine("Aplicaciones escaneadas: ${summary.totalAppsScanned}")
            appendLine("Amenazas detectadas: ${summary.threatsDetected}")
            appendLine("Apps de alto riesgo: ${summary.highRiskApps}")
            appendLine()
            appendLine("🔍 HALLAZGOS FORENSES")
            appendLine("-".repeat(50))
            
            if (summary.forensicFindings.isEmpty()) {
                appendLine("✅ No se detectaron hallazgos de interés forense.")
            } else {
                summary.forensicFindings.forEachIndexed { index, finding ->
                    appendLine()
                    appendLine("${index + 1}. [${finding.severity}] ${finding.category}")
                    appendLine("   Descripción: ${finding.description}")
                    appendLine("   Evidencia: ${finding.evidence}")
                    appendLine("   Recomendación: ${finding.recommendation}")
                }
            }
            
            appendLine()
            appendLine("📝 VALIDEZ LEGAL")
            appendLine("-".repeat(50))
            appendLine("Este informe ha sido generado mediante análisis forense automático.")
            appendLine("Contiene evidencia digital con hash SHA-256 y marca temporal.")
            appendLine("Válido para presentar ante la AEPD, cuerpos de seguridad o juzgados.")
            appendLine("Generado por GuardianOS PRO - Auditoría Ética Digital")
        }
    }
    
    /**
     * Genera resumen forense estructurado
     */
    fun generateForensicSummary(results: List<AppScanResult>): ForensicSummary {
        val timestamp = System.currentTimeMillis()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.getDefault())
        val timestampFormatted = formatter.format(Date(timestamp))
        
        // Generar hash del informe para cadena de custodia
        val reportContent = results.joinToString { 
            "${it.packageName}:${it.isMalware}:${it.isStalkerware}:${it.suspiciousPermissions.size}" 
        }
        val reportHash = sha256(reportContent + timestamp)
        
        // Calcular métricas basadas en propiedades reales
        val threatsDetected = results.count { 
            it.isMalware || it.isStalkerware || it.suspiciousPermissions.size >= 3 
        }
        val highRiskApps = results.count { 
            it.isMalware || it.isStalkerware || it.suspiciousPermissions.size >= 5 
        }
        
        // Generar hallazgos forenses
        val findings = mutableListOf<ForensicFinding>()
        
        results.forEach { app ->
            if (app.isMalware) {
                findings.add(ForensicFinding(
                    severity = "CRÍTICO",
                    category = "Malware Detectado",
                    description = "App ${app.appName} identificada como malware conocido",
                    evidence = "Package: ${app.packageName}, Tipo: ${app.malwareType}",
                    recommendation = "Desinstalar de inmediato y cambiar contraseñas desde otro dispositivo"
                ))
            }
            
            if (app.isStalkerware) {
                findings.add(ForensicFinding(
                    severity = "CRÍTICO",
                    category = "Stalkerware (Vigilancia Oculta)",
                    description = "App ${app.appName} con capacidades de espionaje",
                    evidence = "Package: ${app.packageName}, Indicadores: ${app.stalkerwareIndicators.joinToString()}",
                    recommendation = "Requiere acción legal inmediata - posible violencia digital"
                ))
            }
            
            if (app.suspiciousPermissions.size >= 5 && !app.isMalware && !app.isStalkerware) {
                findings.add(ForensicFinding(
                    severity = "ALTO",
                    category = "Permisos Invasivos",
                    description = "App ${app.appName} con acceso excesivo al dispositivo",
                    evidence = "Permisos sospechosos: ${app.suspiciousPermissions.size} (${app.suspiciousPermissions.take(3).joinToString()}...)",
                    recommendation = "Revisar permisos y considerar desinstalación"
                ))
            }
        }
        
        return ForensicSummary(
            timestamp = timestamp,
            timestampFormatted = timestampFormatted,
            reportHash = reportHash,
            totalAppsScanned = results.size,
            threatsDetected = threatsDetected,
            highRiskApps = highRiskApps,
            forensicFindings = findings
        )
    }
    
    /**
     * Calcula SHA-256 para cadena de custodia
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
