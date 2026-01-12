package com.guardianos.core.domain.model


data class AppAudit(
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val installSource: InstallSource,
    val permissions: List<AppPermission>,
    val findings: List<AuditFinding>,
    val riskScore: Int,
    val risk: Risk
)


