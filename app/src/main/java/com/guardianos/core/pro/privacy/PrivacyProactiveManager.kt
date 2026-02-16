package com.guardianos.core.pro.privacy

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Funciones de privacidad proactiva: modo sigilo y pánico (PRO).
 */
object PrivacyProactiveManager {
    fun triggerPanic(context: Context) {
        // Borra datos de vault, historial, etc. usando PanicMode
        val result = com.guardianos.core.pro.PanicMode.executePanicAction(context)
        // Opcional: mostrar notificación o feedback según resultado
    }

    fun openPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
        context.startActivity(intent)
    }
}
