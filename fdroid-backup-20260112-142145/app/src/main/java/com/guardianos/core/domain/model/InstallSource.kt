package com.guardianos.core.domain.model

enum class InstallSource {
    PLAY_STORE,
    AMAZON,
    SAMSUNG,
    ADB,
    SYSTEM,
    UNKNOWN,
    SIDELOAD;

    override fun toString(): String = when (this) {
        PLAY_STORE -> "Google Play"
        AMAZON -> "Amazon Appstore"
        SAMSUNG -> "Galaxy Store"
        ADB -> "ADB / Desarrollador"
        SYSTEM -> "Sistema"
        UNKNOWN -> "Desconocido"
        SIDELOAD -> "Instalación manual"
    }
}
