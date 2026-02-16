package com.guardianos.core.pro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardianos.core.domain.model.AppScanResult
import com.guardianos.core.pro.assistant.SolutionAssistant

@Composable
fun SolutionAssistantScreen(result: AppScanResult, onBack: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("Asistente de Solución", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("App: ${result.appName}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text("Riesgos detectados:", style = MaterialTheme.typography.bodyMedium)
        if (result.isMalware) Text("• Malware detectado", color = MaterialTheme.colorScheme.error)
        if (result.isStalkerware) Text("• Stalkerware detectado", color = MaterialTheme.colorScheme.error)
        if (result.suspiciousPermissions.isNotEmpty()) Text("• Permisos invasivos: ${result.suspiciousPermissions.size}", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Pasos recomendados:", style = MaterialTheme.typography.bodyMedium)
        SolutionAssistant.getRemediationSteps(result).forEach {
            Text("- $it", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Volver") }
    }
}
