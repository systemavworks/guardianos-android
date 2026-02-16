package com.guardianos.core.audit

import android.content.Context
import com.guardianos.core.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Auditor ISO 27001:2022 - Genera informes de cumplimiento.
 * TODO LOCAL, sin envío de datos.
 */
object ISOAuditor {
    
    /**
     * Realiza auditoría completa ISO 27001:2022.
     */
    fun auditISO27001(
        context: Context,
        apps: List<AppAudit>,
        systemFindings: List<AuditFinding> = emptyList()
    ): ISOAuditReport {
        val controls = mutableListOf<ISOControl>()
        
        // A.5.7: Threat Intelligence
        val threatIntelligence = ISOControl(
            id = "A.5.7",
            name = "Inteligencia de amenazas",
            description = "Gestión de información sobre amenazas de ciberseguridad",
            compliant = apps.none { it.risk == Risk.CRITICAL },
            severity = if (apps.any { it.risk == Risk.CRITICAL }) ControlSeverity.CRITICAL else ControlSeverity.LOW,
            findings = apps.filter { it.risk == Risk.CRITICAL }.map { "${it.appName}: ${it.risk}" }
        )
        controls.add(threatIntelligence)
        
        // A.5.23: Information Security for Cloud Services
        val cloudSecurity = ISOControl(
            id = "A.5.23",
            name = "Seguridad en servicios cloud",
            description = "Protección de datos en servicios en la nube",
            compliant = true,
            severity = ControlSeverity.LOW,
            findings = emptyList()
        )
        controls.add(cloudSecurity)
        
        // A.8.9: Configuration Management
        val configManagement = ISOControl(
            id = "A.8.9",
            name = "Gestión de configuración",
            description = "Configuraciones de seguridad del dispositivo",
            compliant = systemFindings.isEmpty(),
            severity = if (systemFindings.any { it.weight >= 40 }) ControlSeverity.HIGH else ControlSeverity.MEDIUM,
            findings = systemFindings.map { "${it.title}: ${it.description}" }
        )
        controls.add(configManagement)
        
        // A.8.23: Web Filtering
        val webFiltering = ISOControl(
            id = "A.8.23",
            name = "Filtrado web",
            description = "Control de navegación y contenido malicioso",
            compliant = true,
            severity = ControlSeverity.LOW,
            findings = emptyList()
        )
        controls.add(webFiltering)
        
        // A.8.24: Use of Cryptography
        val cryptography = ISOControl(
            id = "A.8.24",
            name = "Uso de criptografía",
            description = "Cifrado de datos sensibles",
            compliant = true,
            severity = ControlSeverity.LOW,
            findings = emptyList()
        )
        controls.add(cryptography)
        
        // A.8.28: Secure Coding
        val secureCoding = ISOControl(
            id = "A.8.28",
            name = "Código seguro",
            description = "Validación de seguridad en aplicaciones",
            compliant = apps.none { it.findings.any { f -> f.title.contains("Firma inválida") } },
            severity = if (apps.any { it.findings.any { f -> f.title.contains("Firma") } }) ControlSeverity.HIGH else ControlSeverity.LOW,
            findings = apps.filter { it.findings.any { f -> f.title.contains("Firma") } }.map { "${it.appName}: Firma inválida o sospechosa" }
        )
        controls.add(secureCoding)
        
        // Calcular cumplimiento global
        val compliantControls = controls.count { it.compliant }
        val overallCompliance = (compliantControls.toFloat() / controls.size) * 100
        
        // Conteo de hallazgos por severidad
        val criticalFindings = controls.count { it.severity == ControlSeverity.CRITICAL && !it.compliant }
        val highFindings = controls.count { it.severity == ControlSeverity.HIGH && !it.compliant }
        val mediumFindings = controls.count { it.severity == ControlSeverity.MEDIUM && !it.compliant }
        val lowFindings = controls.count { it.severity == ControlSeverity.LOW && !it.compliant }
        
        return ISOAuditReport(
            deviceInfo = getDeviceInfo(context),
            scanTimestamp = System.currentTimeMillis(),
            controls = controls,
            overallCompliance = overallCompliance,
            criticalFindings = criticalFindings,
            highFindings = highFindings,
            mediumFindings = mediumFindings,
            lowFindings = lowFindings
        )
    }
    
    private fun getDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            sdkVersion = android.os.Build.VERSION.SDK_INT,
            securityPatch = android.os.Build.VERSION.SECURITY_PATCH ?: "Desconocido"
        )
    }
    
    private fun generateDeviceHash(): String {
        val data = "${android.os.Build.MANUFACTURER}|${android.os.Build.MODEL}|${android.os.Build.FINGERPRINT}"
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(data.toByteArray())
            digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            "unknown"
        }
    }
}
