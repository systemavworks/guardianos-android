package com.guardianos.core.pro.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.domain.model.Risk
import com.guardianos.core.pro.ScanComparator
import com.guardianos.core.pro.ScanEntry
import com.guardianos.core.pro.ScanHistory
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla de historial de escaneos PRO.
 * Muestra lista de escaneos y permite comparación temporal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistoryScreen(
    context: Context,
    onBack: () -> Unit
) {
    var history by remember { mutableStateOf<List<ScanEntry>>(emptyList()) }
    var selectedScans by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showComparison by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    
    // Cargar historial
    LaunchedEffect(Unit) {
        ScanHistory.loadHistory(context)
            .onSuccess { 
                history = it
                isLoading = false
            }
            .onFailure { 
                error = it.message ?: "Error al cargar historial"
                isLoading = false
            }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TopAppBar
        TopAppBar(
            title = { Text("📅 Historial de Escaneos") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Volver")
                }
            },
            actions = {
                if (history.isNotEmpty()) {
                    IconButton(onClick = {
                        ScanHistory.clearHistory(context)
                            .onSuccess { history = emptyList() }
                    }) {
                        Icon(Icons.Default.Delete, "Borrar historial")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // Contenido
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (error.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Red
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(error, color = Color.Red, textAlign = TextAlign.Center)
                }
            }
        } else if (history.isEmpty()) {
            // Estado vacío
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("📋", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Sin Escaneos Guardados",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Los escaneos PRO se guardan automáticamente aquí para comparación temporal",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Mostrar comparación si se seleccionaron 2 escaneos
            if (showComparison && selectedScans.size == 2) {
                ScanComparisonView(
                    context = context,
                    oldScan = history[selectedScans[1]],
                    newScan = history[selectedScans[0]],
                    onClose = { 
                        showComparison = false
                        selectedScans = emptyList()
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Modo de selección
                    if (selectedScans.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF5D8BF4).copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF5D8BF4))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${selectedScans.size} escaneos seleccionados",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5D8BF4)
                                )
                                Row {
                                    if (selectedScans.size == 2) {
                                        Button(
                                            onClick = { showComparison = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF5D8BF4)
                                            )
                                        ) {
                                            Icon(Icons.Default.Compare, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Comparar")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    TextButton(onClick = { selectedScans = emptyList() }) {
                                        Text("Cancelar")
                                    }
                                }
                            }
                        }
                    }
                    
                    // Lista de escaneos
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "Toca 2 escaneos para comparar",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        items(history.size) { index ->
                            val scan = history[index]
                            ScanHistoryCard(
                                scan = scan,
                                isSelected = index in selectedScans,
                                onSelect = {
                                    selectedScans = if (index in selectedScans) {
                                        selectedScans - index
                                    } else if (selectedScans.size < 2) {
                                        selectedScans + index
                                    } else {
                                        selectedScans.drop(1) + index
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHistoryCard(
    scan: ScanEntry,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(scan.timestamp))
    
    val criticalApps = scan.apps.count { it.risk == Risk.CRITICAL }
    val highRiskApps = scan.apps.count { it.risk == Risk.HIGH }
    val mediumRiskApps = scan.apps.count { it.risk == Risk.MEDIUM }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                Color(0xFF5D8BF4).copy(alpha = 0.2f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            BorderStroke(2.dp, Color(0xFF5D8BF4))
        else 
            null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "📅 $dateStr",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${scan.apps.size} apps analizadas",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
                
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Seleccionado",
                        tint = Color(0xFF5D8BF4),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Resumen de amenazas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (criticalApps > 0) {
                    RiskBadge("🔴 $criticalApps", Color(0xFFDC2626))
                }
                if (highRiskApps > 0) {
                    RiskBadge("🟠 $highRiskApps", Color(0xFFF59E0B))
                }
                if (mediumRiskApps > 0) {
                    RiskBadge("🟡 $mediumRiskApps", Color(0xFFFBBF24))
                }
                if (criticalApps == 0 && highRiskApps == 0) {
                    RiskBadge("✅ Seguro", Color(0xFF10B981))
                }
            }
        }
    }
}

@Composable
private fun RiskBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ScanComparisonView(
    context: Context,
    oldScan: ScanEntry,
    newScan: ScanEntry,
    onClose: () -> Unit
) {
    val comparison = remember { ScanComparator.compareScan(oldScan.apps, newScan.apps) }
    val oldDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val oldDate = oldDateFormat.format(Date(oldScan.timestamp))
    val newDate = oldDateFormat.format(Date(newScan.timestamp))
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF5D8BF4).copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📊 Comparativa de Escaneos",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5D8BF4)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Antes", fontSize = 13.sp, color = Color.Gray)
                        Text(oldDate, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = Color(0xFF5D8BF4))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Ahora", fontSize = 13.sp, color = Color.Gray)
                        Text(newDate, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Resumen de cambios
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "📈 Resumen de Cambios",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ChangeStat("➕ Nuevas", comparison.newApps.size, Color(0xFF3B82F6))
                    ChangeStat("➖ Eliminadas", comparison.removedApps.size, Color(0xFFEF4444))
                    ChangeStat("🔄 Modificadas", comparison.modifiedApps.size, Color(0xFFFBBF24))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Apps nuevas
        if (comparison.newApps.isNotEmpty()) {
            ComparisonSection(
                title = "➕ Apps Nuevas (${comparison.newApps.size})",
                color = Color(0xFF3B82F6)
            ) {
                comparison.newApps.forEach { app ->
                    AppComparisonCard(
                        appName = app.appName,
                        packageName = app.packageName,
                        riskScore = app.riskScore,
                        details = listOf("Instalada recientemente")
                    )
                }
            }
        }
        
        // Apps eliminadas
        if (comparison.removedApps.isNotEmpty()) {
            ComparisonSection(
                title = "➖ Apps Eliminadas (${comparison.removedApps.size})",
                color = Color(0xFFEF4444)
            ) {
                comparison.removedApps.forEach { app ->
                    AppComparisonCard(
                        appName = app.appName,
                        packageName = app.packageName,
                        riskScore = app.riskScore,
                        details = listOf("Ya no está instalada")
                    )
                }
            }
        }
        
        // Apps modificadas
        if (comparison.modifiedApps.isNotEmpty()) {
            ComparisonSection(
                title = "🔄 Apps Modificadas (${comparison.modifiedApps.size})",
                color = Color(0xFFFBBF24)
            ) {
                comparison.modifiedApps.forEach { change ->
                    AppComparisonCard(
                        appName = change.appName,
                        packageName = change.packageName,
                        riskScore = null,
                        details = change.changes
                    )
                }
            }
        }
        
        // Sin cambios
        if (comparison.totalChanges == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sin Cambios Detectados",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Las apps instaladas son idénticas en ambos escaneos",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5D8BF4)
            )
        ) {
            Text("Cerrar Comparativa")
        }
        
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChangeStat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 13.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun ComparisonSection(
    title: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun AppComparisonCard(
    appName: String,
    packageName: String,
    riskScore: Int?,
    details: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        packageName,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                
                if (riskScore != null) {
                    val riskColor = when {
                        riskScore >= 80 -> Color(0xFFDC2626)
                        riskScore >= 60 -> Color(0xFFF59E0B)
                        riskScore >= 30 -> Color(0xFFFBBF24)
                        else -> Color(0xFF10B981)
                    }
                    Surface(
                        color = riskColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "$riskScore",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = riskColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                details.forEach { detail ->
                    Text(
                        "• $detail",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
