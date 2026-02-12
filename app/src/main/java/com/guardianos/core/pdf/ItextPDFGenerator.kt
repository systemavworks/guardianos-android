package com.guardianos.core.pdf

import android.content.Context
import android.os.Build
import android.provider.Settings
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
        
        // Información del dispositivo
        document.add(Paragraph("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            .setFontSize(10f)
            .setMarginBottom(3f))
        
        document.add(Paragraph("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            .setFontSize(10f)
            .setMarginBottom(if (forensicMode) 3f else 15f))
        
        // ID único del dispositivo (solo en modo forense)
        if (forensicMode) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            document.add(Paragraph("Device ID: $androidId")
                .setFontSize(9f)
                .setFontColor(DeviceRgb(100, 100, 100))
                .setMarginBottom(15f))
        }
        
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
     * Genera reporte completo de detección de stalkerware (PRO).
     * Incluye análisis de servicios de accesibilidad, apps ocultas y servicios persistentes.
     */
    fun generateStalkerwareReport(
        context: Context,
        reports: List<com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskReport>,
        forensicMode: Boolean = false
    ): File {
        val fileName = if (forensicMode) {
            "GuardianOS_FORENSIC_Stalkerware_${System.currentTimeMillis()}.pdf"
        } else {
            "GuardianOS_PRO_Stalkerware_${System.currentTimeMillis()}.pdf"
        }
        
        val file = File(context.getExternalFilesDir(null), fileName)
        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        
        // PORTADA
        document.add(Paragraph("GuardianOS PRO")
            .setFontSize(24f)
            .setBold()
            .setFontColor(PRIMARY_COLOR)
            .setMarginBottom(10f))
        
        document.add(Paragraph("Reporte de Detección de Stalkerware")
            .setFontSize(20f)
            .setBold()
            .setFontColor(ERROR_COLOR)
            .setMarginBottom(20f))
        
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        document.add(Paragraph("Fecha: ${dateFormat.format(Date())}")
            .setFontSize(12f)
            .setMarginBottom(5f))
        
        // Información del dispositivo
        document.add(Paragraph("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            .setFontSize(10f)
            .setMarginBottom(3f))
        
        document.add(Paragraph("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            .setFontSize(10f)
            .setMarginBottom(if (forensicMode) 3f else 20f))
        
        // ID único del dispositivo (solo en modo forense)
        if (forensicMode) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            document.add(Paragraph("Device ID: $androidId")
                .setFontSize(9f)
                .setFontColor(DeviceRgb(100, 100, 100))
                .setMarginBottom(20f))
        }
        
        // Banner de información
        val infoPara = Paragraph()
            .add(Text("🔒 ANÁLISIS 100% LOCAL\n").setBold().setFontSize(11f))
            .add(Text("Este reporte se generó sin enviar datos a servidores externos. " +
                     "Todos los análisis se ejecutaron localmente en el dispositivo.\n\n").setFontSize(9f))
            .setBackgroundColor(DeviceRgb(16, 185, 129), 0.1f)
            .setPadding(10f)
            .setMarginBottom(20f)
        document.add(infoPara)
        
        // Resumen estadístico
        val criticalCount = reports.count { 
            it.riskLevel == com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED 
        }
        val highCount = reports.count { 
            it.riskLevel == com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION 
        }
        val mediumCount = reports.count { 
            it.riskLevel == com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.MEDIUM 
        }
        
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f, 1f)))
            .useAllAvailableWidth()
            .setMarginBottom(20f)
        
        summaryTable.addCell(createStatCell("Apps Analizadas", reports.size.toString(), PRIMARY_COLOR))
        summaryTable.addCell(createStatCell("CRÍTICO", criticalCount.toString(), ERROR_COLOR))
        summaryTable.addCell(createStatCell("ALTO", highCount.toString(), WARNING_COLOR))
        summaryTable.addCell(createStatCell("MEDIO", mediumCount.toString(), DeviceRgb(59, 130, 246)))
        
        document.add(summaryTable)
        
        // Resumen de riesgo
        if (criticalCount > 0) {
            document.add(Paragraph("🚨 ALERTA CRÍTICA")
                .setFontSize(16f)
                .setBold()
                .setFontColor(ERROR_COLOR)
                .setMarginBottom(10f))
            
            document.add(Paragraph(
                "Se detectaron $criticalCount app(s) con comportamiento confirmado de stalkerware. " +
                "Estas aplicaciones tienen múltiples indicadores de espionaje y deben ser desinstaladas " +
                "inmediatamente."
            ).setFontSize(11f).setMarginBottom(20f))
        } else if (highCount > 0) {
            document.add(Paragraph("⚠️ SOSPECHA ALTA")
                .setFontSize(16f)
                .setBold()
                .setFontColor(WARNING_COLOR)
                .setMarginBottom(10f))
            
            document.add(Paragraph(
                "Se detectaron $highCount app(s) con comportamiento sospechoso. " +
                "Se recomienda revisión manual y verificar quién instaló estas aplicaciones."
            ).setFontSize(11f).setMarginBottom(20f))
        } else if (mediumCount > 0) {
            document.add(Paragraph("ℹ️ APPS CON RIESGO MEDIO")
                .setFontSize(16f)
                .setBold()
                .setFontColor(DeviceRgb(59, 130, 246))
                .setMarginBottom(10f))
            
            document.add(Paragraph(
                "Se detectaron $mediumCount app(s) con algunos comportamientos inusuales. " +
                "Vigilar estas aplicaciones."
            ).setFontSize(11f).setMarginBottom(20f))
        } else {
            document.add(Paragraph("✅ DISPOSITIVO SEGURO")
                .setFontSize(16f)
                .setBold()
                .setFontColor(SUCCESS_COLOR)
                .setMarginBottom(10f))
            
            document.add(Paragraph(
                "No se detectaron aplicaciones con comportamientos de stalkerware."
            ).setFontSize(11f).setMarginBottom(20f))
        }
        
        // Detalle de cada app
        if (reports.isNotEmpty()) {
            document.add(Paragraph("Detalle de Aplicaciones")
                .setFontSize(16f)
                .setBold()
                .setMarginTop(20f)
                .setMarginBottom(10f))
            
            reports.sortedByDescending { it.totalScore }.forEach { report ->
                val borderColor = when (report.riskLevel) {
                    com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> ERROR_COLOR
                    com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> WARNING_COLOR
                    com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.MEDIUM -> DeviceRgb(59, 130, 246)
                    else -> DeviceRgb(156, 163, 175)
                }
                
                val appCard = Table(UnitValue.createPercentArray(floatArrayOf(1f)))
                    .useAllAvailableWidth()
                    .setMarginBottom(12f)
                
                val cardCell = Cell()
                    .setPadding(12f)
                    .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(borderColor, 4f))
                
                // Header con nombre y puntuación
                val headerPara = Paragraph()
                    .add(Text(report.appName).setBold().setFontSize(13f))
                    .add(Text("  (${report.packageName})").setFontSize(9f).setFontColor(ColorConstants.GRAY))
                    .add(Text("\nPuntuación: ${report.totalScore}/100").setBold().setFontSize(11f).setFontColor(borderColor))
                cardCell.add(headerPara)
                
                // Nivel de riesgo
                val riskText = when (report.riskLevel) {
                    com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.STALKERWARE_CONFIRMED -> "🚨 STALKERWARE CONFIRMADO"
                    com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.HIGH_SUSPICION -> "⚠️ SOSPECHA ALTA"
                    com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskLevel.MEDIUM -> "👀 RIESGO MEDIO"
                    else -> "ℹ️ RIESGO BAJO"
                }
                cardCell.add(Paragraph(riskText)
                    .setFontSize(10f)
                    .setBold()
                    .setFontColor(borderColor)
                    .setMarginTop(6f))
                
                // Comportamientos detectados
                if (report.behaviorFlags.isNotEmpty()) {
                    cardCell.add(Paragraph("Comportamientos detectados:")
                        .setBold()
                        .setFontSize(10f)
                        .setMarginTop(8f))
                    
                    report.behaviorFlags.forEach { flag ->
                        cardCell.add(Paragraph("  • ${flag.replace("🔴", "").replace("⚠️", "").replace("👀", "").replace("🕐", "").trim()}")
                            .setFontSize(9f)
                            .setMarginLeft(5f))
                    }
                }
                
                // Desglose de puntuación
                if (report.scoringBreakdown.isNotEmpty()) {
                    cardCell.add(Paragraph("Desglose de puntuación:")
                        .setBold()
                        .setFontSize(10f)
                        .setMarginTop(8f))
                    
                    val scoreTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 1f)))
                        .useAllAvailableWidth()
                        .setMarginTop(4f)
                    
                    report.scoringBreakdown.forEach { (factor, points) ->
                        scoreTable.addCell(Cell()
                            .add(Paragraph(factor).setFontSize(9f))
                            .setBorder(Border.NO_BORDER))
                        scoreTable.addCell(Cell()
                            .add(Paragraph("+$points pts").setBold().setFontSize(9f)
                                .setFontColor(if (points >= 30) ERROR_COLOR else if (points >= 15) WARNING_COLOR else ColorConstants.GRAY))
                            .setBorder(Border.NO_BORDER)
                            .setTextAlignment(TextAlignment.RIGHT))
                    }
                    
                    cardCell.add(scoreTable)
                }
                
                // Recomendación
                cardCell.add(Paragraph("Recomendación:")
                    .setBold()
                    .setFontSize(10f)
                    .setMarginTop(8f))
                
                cardCell.add(Paragraph(report.recommendedAction.replace("🚨", "").replace("⚠️", "").replace("👀", "").replace("✓", "").trim())
                    .setFontSize(9f)
                    .setItalic()
                    .setMarginLeft(5f))
                
                appCard.addCell(cardCell)
                document.add(appCard)
            }
        }
        
        // Explicación de metodología
        document.add(Paragraph("Metodología de Detección")
            .setFontSize(14f)
            .setBold()
            .setMarginTop(30f)
            .setMarginBottom(10f))
        
        document.add(Paragraph(
            "Este análisis utiliza un sistema multi-factor que evalúa:\n\n" +
            "1. Servicios de Accesibilidad: Apps que pueden leer pantalla, contraseñas y notificaciones.\n" +
            "2. Apps Ocultas: Aplicaciones sin ícono en launcher o con nombres invisibles (caracteres Unicode).\n" +
            "3. Servicios Persistentes: Apps activas 24/7 en segundo plano con técnicas anti-hibernación.\n" +
            "4. Permisos Críticos: Combinaciones de SMS + Ubicación + Cámara + Contactos.\n" +
            "5. Instalación Nocturna: Apps instaladas entre 00:00-06:00h (típico de stalkerware).\n" +
            "6. Clones de Apps Populares: WhatsApp falso, Facebook falso, etc.\n\n" +
            "La puntuación combina estos factores con umbrales científicamente validados."
        ).setFontSize(9f).setMarginBottom(20f))
        
        // Modo forense
        if (forensicMode) {
            val forensicHash = calculateStalkerwareHash(reports)
            addForensicSection(document, forensicHash)
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
     * Calcula hash SHA-256 del reporte stalkerware.
     */
    private fun calculateStalkerwareHash(reports: List<com.guardianos.core.audit.detector.RiskScorer.StalkerwareRiskReport>): String {
        val data = reports.joinToString("|") { 
            "${it.packageName}:${it.totalScore}:${it.riskLevel}:${it.hasAccessibilityService}:${it.isHidden}:${it.hasPersistentService}" 
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
