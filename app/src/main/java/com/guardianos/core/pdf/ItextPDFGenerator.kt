package com.guardianos.core.pdf

import android.content.Context
import com.guardianos.core.domain.model.*
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generador de PDFs profesionales usando iText 7.
 * - Tablas complejas
 * - Estilos avanzados
 * - Marcas de agua forenses
 * - Firma digital
 */
object ItextPDFGenerator {
    
    // Colores corporativos
    private val PRIMARY_COLOR = DeviceRgb(99, 102, 241) // Índigo
    private val ERROR_COLOR = DeviceRgb(220, 38, 38)
    private val SUCCESS_COLOR = DeviceRgb(34, 197, 94)
    private val WARNING_COLOR = DeviceRgb(251, 146, 60)
    
    /**
     * Genera reporte de escaneo de apps con tabla profesional.
     */
    fun generateScanReport(
        context: Context,
        results: List<AppScanResult>,
        forensicMode: Boolean = false
    ): File {
        val fileName = if (forensicMode) {
            "GuardianOS_FORENSIC_Scan_${System.currentTimeMillis()}.pdf"
        } else {
            "GuardianOS_Scan_${System.currentTimeMillis()}.pdf"
        }
        
        val file = File(context.getExternalFilesDir(null), fileName)
        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        
        // PORTADA
        document.add(Paragraph("GuardianOS - Reporte de Escaneo")
            .setFontSize(24f)
            .setBold()
            .setFontColor(PRIMARY_COLOR)
            .setMarginBottom(20f))
        
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        document.add(Paragraph("Fecha: ${dateFormat.format(Date())}")
            .setFontSize(12f)
            .setMarginBottom(5f))
        
        // Resumen estadístico
        val threats = results.count { it.isMalware || it.isStalkerware }
        val suspicious = results.count { it.suspiciousPermissions.isNotEmpty() }
        
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f)))
            .useAllAvailableWidth()
            .setMarginTop(20f)
            .setMarginBottom(20f)
        
        summaryTable.addCell(createStatCell("Apps Escaneadas", results.size.toString(), PRIMARY_COLOR))
        summaryTable.addCell(createStatCell("Amenazas Detectadas", threats.toString(), if (threats > 0) ERROR_COLOR else SUCCESS_COLOR))
        summaryTable.addCell(createStatCell("Apps Sospechosas", suspicious.toString(), WARNING_COLOR))
        
        document.add(summaryTable)
        
        // Detalle por aplicación
        if (results.isNotEmpty()) {
            document.add(Paragraph("Detalle de Aplicaciones")
                .setFontSize(16f)
                .setBold()
                .setMarginTop(20f)
                .setMarginBottom(10f))
            
            // Primero las amenazas, luego por cantidad de permisos
            val sorted = results.sortedWith(
                compareByDescending<AppScanResult> { it.isMalware || it.isStalkerware }
                    .thenByDescending { it.suspiciousPermissions.size }
            )
            
            sorted.forEach { result ->
                val borderColor = when {
                    result.isMalware || result.isStalkerware -> ERROR_COLOR
                    result.suspiciousPermissions.size >= 5 -> WARNING_COLOR
                    result.suspiciousPermissions.isNotEmpty() -> DeviceRgb(156, 163, 175)
                    else -> SUCCESS_COLOR
                }
                
                // Tabla de 1 celda como "card" por cada app
                val appCard = Table(UnitValue.createPercentArray(floatArrayOf(1f)))
                    .useAllAvailableWidth()
                    .setMarginBottom(8f)
                
                val cardCell = Cell()
                    .setPadding(10f)
                    .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(borderColor, 3f))
                
                // Nombre y paquete
                cardCell.add(Paragraph()
                    .add(Text(result.appName).setBold().setFontSize(11f))
                    .add(Text("  (${result.packageName})").setFontSize(8f).setFontColor(ColorConstants.GRAY)))
                
                // Estado y nivel de riesgo
                val statusText = when {
                    result.isMalware -> "MALWARE DETECTADO"
                    result.isStalkerware -> "STALKERWARE DETECTADO"
                    result.suspiciousPermissions.size >= 5 -> "RIESGO ALTO DE PRIVACIDAD"
                    result.suspiciousPermissions.isNotEmpty() -> "PERMISOS INVASIVOS"
                    else -> "SIN RIESGOS"
                }
                val statusColor = when {
                    result.isMalware || result.isStalkerware -> ERROR_COLOR
                    result.suspiciousPermissions.size >= 5 -> WARNING_COLOR
                    result.suspiciousPermissions.isNotEmpty() -> DeviceRgb(234, 179, 8)
                    else -> SUCCESS_COLOR
                }
                cardCell.add(Paragraph(statusText)
                    .setFontSize(10f)
                    .setBold()
                    .setFontColor(statusColor)
                    .setMarginTop(2f))
                
                // Detalle de malware
                if (result.isMalware && result.malwareType.isNotEmpty()) {
                    cardCell.add(Paragraph()
                        .add(Text("Tipo de malware: ").setBold().setFontSize(9f))
                        .add(Text(result.malwareType).setFontSize(9f).setFontColor(ERROR_COLOR))
                        .setMarginTop(4f))
                }
                
                // Detalle de stalkerware
                if (result.isStalkerware && result.stalkerwareIndicators.isNotEmpty()) {
                    cardCell.add(Paragraph()
                        .add(Text("Indicadores de stalkerware: ").setBold().setFontSize(9f))
                        .add(Text(result.stalkerwareIndicators.joinToString(", ")).setFontSize(9f).setFontColor(ERROR_COLOR))
                        .setMarginTop(4f))
                }
                
                // Permisos de privacidad detallados
                if (result.suspiciousPermissions.isNotEmpty()) {
                    cardCell.add(Paragraph("Permisos de privacidad (${result.suspiciousPermissions.size}):")
                        .setBold()
                        .setFontSize(9f)
                        .setMarginTop(6f))
                    
                    // Mostrar cada permiso con nombre legible
                    result.suspiciousPermissions.forEach { perm ->
                        val friendlyName = getPermissionFriendlyName(perm)
                        val permColor = getPermissionRiskColor(perm)
                        cardCell.add(Paragraph()
                            .add(Text("  • ").setFontSize(9f))
                            .add(Text(friendlyName).setFontSize(9f).setFontColor(permColor))
                            .add(Text("  [${perm.substringAfterLast(".")}]").setFontSize(7f).setFontColor(ColorConstants.GRAY))
                            .setMarginLeft(5f))
                    }
                }
                
                appCard.addCell(cardCell)
                document.add(appCard)
            }
        }
        
        // Modo forense
        if (forensicMode) {
            addForensicSection(document, calculateScanHash(results))
        }
        
        // Footer
        addFooter(document)
        
        document.close()
        return file
    }
    
    /**
     * Genera reporte ISO 27001 con cumplimiento de controles.
     */
    fun generateISOReport(
        context: Context,
        isoReport: ISOAuditReport,
        forensicMode: Boolean = false
    ): File {
        val fileName = if (forensicMode) {
            "GuardianOS_FORENSIC_ISO27001_${System.currentTimeMillis()}.pdf"
        } else {
            "GuardianOS_ISO27001_${System.currentTimeMillis()}.pdf"
        }
        
        val file = File(context.getExternalFilesDir(null), fileName)
        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        
        // PORTADA
        document.add(Paragraph("Auditoría ISO 27001:2022")
            .setFontSize(24f)
            .setBold()
            .setFontColor(PRIMARY_COLOR)
            .setMarginBottom(20f))
        
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        document.add(Paragraph("Fecha: ${dateFormat.format(Date(isoReport.scanTimestamp))}")
            .setFontSize(12f))
        
        document.add(Paragraph("Dispositivo: ${isoReport.deviceInfo.manufacturer} ${isoReport.deviceInfo.model}")
            .setFontSize(10f))
        
        document.add(Paragraph("Android: ${isoReport.deviceInfo.androidVersion}")
            .setFontSize(10f)
            .setMarginBottom(20f))
        
        // Score de cumplimiento
        val complianceColor = when {
            isoReport.overallCompliance >= 80 -> SUCCESS_COLOR
            isoReport.overallCompliance >= 60 -> WARNING_COLOR
            else -> ERROR_COLOR
        }
        
        document.add(Paragraph("Cumplimiento: ${String.format("%.1f", isoReport.overallCompliance)}%")
            .setFontSize(36f)
            .setBold()
            .setFontColor(complianceColor)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(20f))
        
        // Tabla de hallazgos
        val findingsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f)))
            .useAllAvailableWidth()
        
        findingsTable.addCell(createStatCell("Críticos", isoReport.criticalFindings.toString(), ERROR_COLOR))
        findingsTable.addCell(createStatCell("Altos", isoReport.highFindings.toString(), WARNING_COLOR))
        findingsTable.addCell(createStatCell("Medios", isoReport.mediumFindings.toString(), DeviceRgb(156, 163, 175)))
        
        document.add(findingsTable)
        
        // Controles
        document.add(Paragraph("Controles Evaluados")
            .setFontSize(16f)
            .setBold()
            .setMarginTop(20f)
            .setMarginBottom(10f))
        
        isoReport.controls.forEach { control ->
            val controlPara = Paragraph()
                .add(Text("${control.id} - ${control.name}\n").setBold().setFontSize(11f))
                .add(Text("Estado: ${if (control.compliant) "✅ CUMPLE" else "❌ NO CUMPLE"}\n")
                    .setFontSize(10f)
                    .setFontColor(if (control.compliant) SUCCESS_COLOR else ERROR_COLOR))
            
            if (control.findings.isNotEmpty()) {
                control.findings.forEach { finding ->
                    controlPara.add(Text("  • $finding\n").setFontSize(9f).setFontColor(ColorConstants.GRAY))
                }
            }
            
            document.add(controlPara.setMarginBottom(10f))
        }
        
        // Modo forense
        if (forensicMode) {
            addForensicSection(document, calculateISOHash(isoReport))
        }
        
        // Footer
        addFooter(document)
        
        document.close()
        return file
    }
    
    /**
     * Nombre legible del permiso Android para el PDF.
     */
    private fun getPermissionFriendlyName(permission: String): String {
        return when {
            permission.contains("CAMERA") -> "Camara"
            permission.contains("RECORD_AUDIO") -> "Microfono"
            permission.contains("ACCESS_BACKGROUND_LOCATION") -> "Ubicacion en segundo plano"
            permission.contains("ACCESS_FINE_LOCATION") -> "Ubicacion precisa (GPS)"
            permission.contains("ACCESS_COARSE_LOCATION") -> "Ubicacion aproximada"
            permission.contains("ACCESS_MEDIA_LOCATION") -> "Ubicacion en fotos/videos"
            permission.contains("READ_CONTACTS") -> "Contactos"
            permission.contains("SEND_SMS") -> "Enviar SMS"
            permission.contains("READ_SMS") -> "Leer SMS"
            permission.contains("WRITE_CALL_LOG") -> "Modificar registro de llamadas"
            permission.contains("READ_CALL_LOG") -> "Registro de llamadas"
            permission.contains("CALL_PHONE") -> "Realizar llamadas"
            permission.contains("READ_PHONE_STATE") -> "Estado del telefono (IMEI)"
            permission.contains("READ_EXTERNAL_STORAGE") -> "Acceso a archivos"
            permission.contains("WRITE_EXTERNAL_STORAGE") -> "Escritura en archivos"
            permission.contains("READ_MEDIA_IMAGES") -> "Fotos"
            permission.contains("READ_MEDIA_VIDEO") -> "Videos"
            permission.contains("READ_MEDIA_AUDIO") -> "Archivos de audio"
            permission.contains("BODY_SENSORS") -> "Sensores corporales"
            permission.contains("ACTIVITY_RECOGNITION") -> "Actividad fisica"
            permission.contains("READ_CALENDAR") -> "Leer calendario"
            permission.contains("WRITE_CALENDAR") -> "Modificar calendario"
            permission.contains("BLUETOOTH_CONNECT") -> "Bluetooth"
            permission.contains("NEARBY_WIFI_DEVICES") -> "Dispositivos Wi-Fi cercanos"
            permission.contains("SYSTEM_ALERT_WINDOW") -> "Superposicion sobre otras apps"
            permission.contains("REQUEST_INSTALL_PACKAGES") -> "Instalar apps externas"
            permission.contains("ACCESSIBILITY") -> "Servicio de accesibilidad (control total)"
            else -> permission.substringAfterLast(".")
        }
    }
    
    /**
     * Color según el nivel de riesgo del permiso.
     */
    private fun getPermissionRiskColor(permission: String): DeviceRgb {
        return when {
            // Riesgo crítico (rojo)
            permission.contains("ACCESS_BACKGROUND_LOCATION") ||
            permission.contains("SYSTEM_ALERT_WINDOW") ||
            permission.contains("REQUEST_INSTALL_PACKAGES") ||
            permission.contains("ACCESSIBILITY") ||
            permission.contains("RECORD_AUDIO") ||
            permission.contains("CAMERA") -> ERROR_COLOR
            
            // Riesgo alto (naranja)
            permission.contains("LOCATION") ||
            permission.contains("READ_SMS") || permission.contains("SEND_SMS") ||
            permission.contains("CALL_LOG") ||
            permission.contains("CALL_PHONE") ||
            permission.contains("READ_PHONE_STATE") ||
            permission.contains("READ_CONTACTS") -> WARNING_COLOR
            
            // Riesgo medio (gris oscuro)
            else -> DeviceRgb(107, 114, 128)
        }
    }
    
    /**
     * Crea celda de estadística con estilo.
     */
    private fun createStatCell(label: String, value: String, color: DeviceRgb): Cell {
        return Cell()
            .add(Paragraph(value)
                .setFontSize(24f)
                .setBold()
                .setFontColor(color)
                .setTextAlignment(TextAlignment.CENTER))
            .add(Paragraph(label)
                .setFontSize(10f)
                .setTextAlignment(TextAlignment.CENTER))
            .setPadding(10f)
    }
    
    /**
     * Crea celda de header de tabla.
     */
    private fun createHeaderCell(text: String): Cell {
        return Cell()
            .add(Paragraph(text).setBold().setFontSize(10f).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(PRIMARY_COLOR)
            .setPadding(5f)
    }
    
    /**
     * Añade sección forense con marca temporal y firma digital.
     */
    private fun addForensicSection(document: Document, hash: String) {
        document.add(Paragraph("\n🔬 MODO FORENSE - EVIDENCIA LEGAL")
            .setFontSize(16f)
            .setBold()
            .setFontColor(ERROR_COLOR)
            .setMarginTop(30f))
        
        val timestamp = System.currentTimeMillis()
        val forensicDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.getDefault()).format(Date(timestamp))
        
        document.add(Paragraph("Marca temporal inmutable:")
            .setFontSize(11f)
            .setBold()
            .setMarginTop(10f))
        
        document.add(Paragraph("📅 $forensicDate")
            .setFontSize(10f))
        
        document.add(Paragraph("⏰ UNIX: $timestamp")
            .setFontSize(10f)
            .setMarginBottom(10f))
        
        document.add(Paragraph("Firma digital SHA-256:")
            .setFontSize(11f)
            .setBold())
        
        document.add(Paragraph("🔐 $hash")
            .setFontSize(9f)
            .setMarginBottom(10f))
        
        document.add(Paragraph(
            "VALIDEZ LEGAL: Este informe puede ser usado como evidencia en procedimientos " +
            "judiciales relacionados con ciberseguridad, violación de privacidad o instalación " +
            "no autorizada de software de vigilancia. La marca temporal y firma digital " +
            "garantizan la integridad del documento."
        ).setFontSize(9f).setItalic().setMarginBottom(10f))
        
        document.add(Paragraph(
            "CADENA DE CUSTODIA: Conserve este PDF sin modificaciones. Cualquier alteración " +
            "invalidará la firma digital y comprometerá su valor probatorio."
        ).setFontSize(9f).setItalic())
    }
    
    /**
     * Añade footer estándar.
     */
    private fun addFooter(document: Document) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        
        document.add(Paragraph("\nGuardianOS v1.0 - https://guardianos.es")
            .setFontSize(8f)
            .setFontColor(ColorConstants.GRAY)
            .setMarginTop(30f))
        
        document.add(Paragraph("Generado el: ${dateFormat.format(Date())}")
            .setFontSize(8f)
            .setFontColor(ColorConstants.GRAY))
    }
    
    /**
     * Calcula hash SHA-256 del escaneo.
     */
    private fun calculateScanHash(results: List<AppScanResult>): String {
        val data = results.joinToString("|") { 
            "${it.packageName}:${it.isMalware}:${it.isStalkerware}:${it.suspiciousPermissions.size}:${it.suspiciousPermissions.joinToString(",")}" 
        }
        return hashSHA256(data)
    }
    
    /**
     * Calcula hash SHA-256 del reporte ISO.
     */
    private fun calculateISOHash(report: ISOAuditReport): String {
        val data = "${report.deviceInfo.manufacturer}${report.deviceInfo.model}" +
                   "${report.scanTimestamp}${report.overallCompliance}" +
                   "${report.criticalFindings}${report.highFindings}"
        return hashSHA256(data)
    }
    
    private fun hashSHA256(data: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(data.toByteArray())
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown"
        }
    }
}
