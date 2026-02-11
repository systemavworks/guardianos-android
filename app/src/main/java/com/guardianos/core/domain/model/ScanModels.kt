package com.guardianos.core.domain.model

/**
 * Resultado del escaneo de una aplicación individual.
 * Utilizado en el flujo de escaneo de malware/stalkerware.
 */
data class AppScanResult(
    val appName: String,
    val packageName: String,
    val isMalware: Boolean = false,
    val malwareType: String = "",
    val isStalkerware: Boolean = false,
    val stalkerwareIndicators: List<String> = emptyList(),
    val suspiciousPermissions: List<String> = emptyList()
)

/**
 * Estadísticas de red agregadas.
 */
data class NetworkStats(
    val totalConnections: Int = 0,
    val suspiciousConnections: Int = 0,
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0
)

/**
 * Conexión de red activa.
 */
data class NetworkConnection(
    val appName: String,
    val packageName: String,
    val remoteAddress: String,
    val remotePort: Int,
    val isSuspicious: Boolean = false,
    val suspiciousReason: String? = null
)

/**
 * Reporte de análisis de privacidad (Pro).
 */
data class PrivacyReport(
    val privacyScore: Int,
    val riskyApps: List<RiskyApp>,
    val excessivePermissions: List<ExcessivePermission>
)

/**
 * App con riesgo de privacidad.
 */
data class RiskyApp(
    val appName: String,
    val packageName: String,
    val riskLevel: String, // "high", "medium", "low"
    val concerns: List<String>
)

/**
 * Permiso excesivo detectado.
 */
data class ExcessivePermission(
    val packageName: String,
    val permission: String,
    val reason: String
)

/**
 * Violación de ISO 27001.
 */
data class ISOViolation(
    val control: String,
    val description: String,
    val severity: String, // "critical", "high", "medium", "low"
    val recommendation: String = ""
)
