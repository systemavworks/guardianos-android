package com.guardianos.core.pro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardianos.core.pro.network.NetworkAnalyzer

@Composable
fun NetworkAnalyzerScreen(onBack: () -> Unit) {
    var connections by remember { mutableStateOf<List<com.guardianos.core.pro.network.NetworkConnectionInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        connections = com.guardianos.core.pro.network.NetworkAnalyzer.getActiveConnections()
    }
    Column(Modifier.padding(16.dp)) {
        Text("Análisis de Red (PRO)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (connections.isEmpty()) {
            Text("No se detectaron conexiones sospechosas.")
        } else {
            connections.forEach {
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("IP remota: ${it.remoteIp} (${it.remoteCountry ?: "-"})")
                        Text("Reputación: ${it.remoteReputation ?: "-"}")
                        Text("Puerto: ${it.port}")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Volver") }
    }
}
