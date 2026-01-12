package com.guardianos.core.domain.model

enum class Risk {
    CRITICAL, HIGH, MEDIUM, LOW;

    override fun toString(): String = when (this) {
        CRITICAL -> "CRÍTICO"
        HIGH -> "ALTO"
        MEDIUM -> "MEDIO"
        LOW -> "BAJO"
    }
}
