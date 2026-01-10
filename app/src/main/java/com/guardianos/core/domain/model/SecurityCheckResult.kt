package com.guardianos.core.domain.model

data class SecurityCheckResult(
    val findings: List<AuditFinding>,
    val score: Int
)
