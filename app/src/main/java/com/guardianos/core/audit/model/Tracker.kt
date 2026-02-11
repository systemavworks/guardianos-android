// app/src/main/java/com/guardianos/core/audit/model/Tracker.kt
package com.guardianos.core.audit.model

// Tracker individual detectado en una app
data class Tracker(
    val name: String,
    val description: String,
    val categories: List<String>,
    val website: String? = null
)

// Respuesta completa de Exodus Privacy API
data class ExodusReport(
    val trackers: Map<String, TrackerData>,
    val creation_date: String? = null,
    val version: Int? = null
)

// Datos crudos de la API (mapeo 1:1)
data class TrackerData(
    val name: String,
    val description: String,
    val categories: List<String>,
    val website: String? = null,
    val code_signature: String? = null,
    val network_signature: String? = null
)

// Resultado del análisis de una app
data class AppTrackerReport(
    val packageName: String,
    val trackerCount: Int = 0,
    val trackers: List<Tracker> = emptyList(),
    val analyzedAt: Long = System.currentTimeMillis()
)
