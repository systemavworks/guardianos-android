package com.guardianos.core.pro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Composable para mostrar CTA de upgrade a PRO en funciones avanzadas.
 */
@Composable
fun ProFeatureCTA(modifier: Modifier = Modifier, onUpgrade: () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Función exclusiva PRO", style = MaterialTheme.typography.titleMedium)
            Text("Activa PRO para desbloquear esta función avanzada.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onUpgrade, modifier = Modifier.padding(top = 8.dp)) {
                Text("Desbloquear PRO")
            }
        }
    }
}
