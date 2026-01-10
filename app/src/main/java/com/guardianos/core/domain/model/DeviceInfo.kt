package com.guardianos.core.domain.model

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val securityPatch: String = "N/A"
)
