package com.guardianos.core.pro.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.guardianos.core.domain.model.AppScanResult

/**
 * Asistente de soluciones automatizadas para apps con riesgo.
 * Solo disponible en PRO.
 */
object SolutionAssistant {
    fun openAppSettings(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun getRemediationSteps(result: AppScanResult): List<String> {
        val steps = mutableListOf<String>()
        if (result.isMalware) steps.add("Desinstala la app inmediatamente.")
        if (result.isStalkerware) steps.add("Desinstala y revisa permisos de acceso a ubicación, micrófono y SMS.")
        if (result.suspiciousPermissions.isNotEmpty()) steps.add("Revoca permisos innecesarios desde Ajustes > Apps > Permisos.")
        steps.add("Si tienes dudas, solicita consultoría personalizada desde la app.")
        return steps
    }
}
