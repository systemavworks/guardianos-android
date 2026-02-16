package com.guardianos.core.pro.consulting

import android.content.Context
import android.content.Intent

/**
 * Consultoría personalizada (PRO): permite enviar informe por email.
 */
object ConsultingManager {
    fun sendConsultingEmail(context: Context, pdfPath: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("info@guardianos.es"))
            putExtra(Intent.EXTRA_SUBJECT, "Consulta personalizada GuardianOS PRO")
            putExtra(Intent.EXTRA_TEXT, "Adjunto el informe para revisión personalizada.")
            putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(pdfPath))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar consulta"))
    }
}
