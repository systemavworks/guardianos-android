package com.guardianos.core.domain.model

/**
 * Modelo para reporte de auditoría ISO 27001:2022.
 */
data class ISOAuditReport(
    val deviceInfo: DeviceInfo,
    val scanTimestamp: Long,
    val controls: List<ISOControl>,
    val overallCompliance: Float,
    val criticalFindings: Int,
    val highFindings: Int,
    val mediumFindings: Int,
    val lowFindings: Int
)

/**
 * Control individual ISO 27001:2022.
 */
data class ISOControl(
    val id: String,
    val name: String,
    val description: String,
    val compliant: Boolean,
    val severity: ControlSeverity,
    val findings: List<String>
)

/**
 * Severidad de control ISO.
 */
enum class ControlSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}
