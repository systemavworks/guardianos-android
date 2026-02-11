package com.guardianos.core.pro

import com.guardianos.core.domain.model.AppAudit
import com.guardianos.core.domain.model.AppPermission
import com.guardianos.core.domain.model.Risk

/**
 * Analizador de privacidad avanzado para versión Pro.
 * Genera explicaciones detalladas y en español claro sobre por qué una app es peligrosa.
 */
object PrivacyAnalyzer {
    
    data class PrivacyThreat(
        val title: String,
        val explanation: String,
        val severity: Risk,
        val recommendation: String
    )
    
    /**
     * Analiza una app y genera explicaciones detalladas sobre amenazas de privacidad.
     */
    fun analyzePrivacyThreats(app: AppAudit): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        
        // 1. Analizar combinaciones peligrosas de permisos
        detectSpyingPatterns(app.permissions)?.let { threats.add(it) }
        
        // 2. Analizar trackers (basado en findings)
        val trackerCount = app.findings.count { it.title.contains("tracker", ignoreCase = true) }
        if (trackerCount > 0) {
            threats.add(PrivacyThreat(
                title = "🕵️ Rastreadores detectados",
                explanation = "Esta app contiene $trackerCount rastreadores que envían información sobre tu uso a empresas de publicidad y análisis. Esto incluye: qué haces en la app, cuánto tiempo la usas, y posiblemente tu ubicación.",
                severity = when {
                    trackerCount > 10 -> Risk.CRITICAL
                    trackerCount > 5 -> Risk.HIGH
                    else -> Risk.MEDIUM
                },
                recommendation = "Considera usar una alternativa sin trackers desde F-Droid, o bloquea los trackers con NetGuard/RethinkDNS."
            ))
        }
        
        // 3. Analizar origen sospechoso
        if (app.installSource.name.contains("UNKNOWN", ignoreCase = true)) {
            threats.add(PrivacyThreat(
                title = "⚠️ Origen desconocido",
                explanation = "Esta app fue instalada desde una fuente desconocida (no oficial). Esto aumenta el riesgo de que contenga malware o spyware, ya que no pasó por ningún proceso de revisión.",
                severity = Risk.HIGH,
                recommendation = "Verifica que descargaste esta app de una fuente confiable. Si no estás seguro, desinstálala."
            ))
        }
        
        // 4. Analizar permisos sin justificación aparente
        detectUnjustifiedPermissions(app)?.let { threats.add(it) }
        
        // 5. Analizar capacidades de administración
        if (app.permissions.any { it.name.contains("DEVICE_ADMIN", ignoreCase = true) }) {
            threats.add(PrivacyThreat(
                title = "🔧 Permisos de administrador",
                explanation = "Esta app tiene permisos de administrador del dispositivo. Puede cambiar configuraciones del sistema, bloquear la pantalla, e incluso dificultar su desinstalación. Esto es típico de apps de control parental o MDM, pero también de malware avanzado.",
                severity = Risk.CRITICAL,
                recommendation = "Solo otorga permisos de administrador a apps en las que confíes plenamente."
            ))
        }
        
        return threats
    }
    
    /**
     * Detecta patrones típicos de espionaje (combinaciones peligrosas).
     */
    private fun detectSpyingPatterns(permissions: List<AppPermission>): PrivacyThreat? {
        val hasLocation = permissions.any { 
            it.name.contains("LOCATION", ignoreCase = true) 
        }
        val hasContacts = permissions.any { 
            it.name.contains("CONTACTS", ignoreCase = true) 
        }
        val hasCamera = permissions.any { 
            it.name.contains("CAMERA", ignoreCase = true) 
        }
        val hasMicrophone = permissions.any { 
            it.name.contains("MICROPHONE", ignoreCase = true) || 
            it.name.contains("RECORD_AUDIO", ignoreCase = true)
        }
        val hasStorage = permissions.any { 
            it.name.contains("STORAGE", ignoreCase = true) || 
            it.name.contains("READ_EXTERNAL", ignoreCase = true)
        }
        val hasSMS = permissions.any { 
            it.name.contains("SMS", ignoreCase = true) 
        }
        
        // Patrón crítico: localización + contactos + cámara/micrófono
        if (hasLocation && hasContacts && (hasCamera || hasMicrophone)) {
            val capabilities = mutableListOf<String>()
            if (hasLocation) capabilities.add("tu ubicación en tiempo real")
            if (hasContacts) capabilities.add("todos tus contactos")
            if (hasCamera) capabilities.add("grabar con la cámara")
            if (hasMicrophone) capabilities.add("grabar audio")
            
            return PrivacyThreat(
                title = "🚨 PATRÓN DE ESPIONAJE DETECTADO",
                explanation = "Esta app tiene una combinación MUY PELIGROSA de permisos. Puede acceder a: ${capabilities.joinToString(", ")}. Esta combinación permite rastrearte completamente y construir un perfil detallado de tu vida.",
                severity = Risk.CRITICAL,
                recommendation = "DESINSTALA esta app inmediatamente a menos que sea absolutamente necesaria y confíes en el desarrollador. Nunca otorgues todos estos permisos a apps de origen dudoso."
            )
        }
        
        // Patrón alto: SMS + contactos + ubicación
        if (hasSMS && hasContacts && hasLocation) {
            return PrivacyThreat(
                title = "⚠️ Acceso total a comunicaciones",
                explanation = "Esta app puede leer tus SMS, acceder a tu lista de contactos, y saber dónde estás. Esto le da acceso completo a tu vida personal: mensajes privados, códigos de verificación bancarios, números de teléfono de familiares, y tus movimientos.",
                severity = Risk.HIGH,
                recommendation = "Revoca los permisos de SMS y contactos si la app no los necesita para su función principal."
            )
        }
        
        // Patrón medio: almacenamiento + internet sin justificación aparente
        if (hasStorage && permissions.any { it.name.contains("INTERNET") }) {
            return PrivacyThreat(
                title = "📤 Acceso a archivos + internet",
                explanation = "Esta app puede leer archivos de tu dispositivo (fotos, documentos, descargas) y enviarlos a internet. Esto incluye fotos personales, PDFs con datos sensibles, y cualquier archivo descargado.",
                severity = Risk.MEDIUM,
                recommendation = "Revisa si la app realmente necesita acceso al almacenamiento. Considera usar \"Solo archivos seleccionados\" en Android 11+."
            )
        }
        
        return null
    }
    
    /**
     * Detecta permisos que no tienen justificación aparente según el tipo de app.
     */
    private fun detectUnjustifiedPermissions(app: AppAudit): PrivacyThreat? {
        // Linternas que piden ubicación
        if (app.appName.contains("linterna", ignoreCase = true) || 
            app.appName.contains("flashlight", ignoreCase = true)) {
            if (app.permissions.any { it.name.contains("LOCATION") }) {
                return PrivacyThreat(
                    title = "🔦 Linterna con acceso a ubicación",
                    explanation = "Una app de linterna NO necesita saber dónde estás. Este es un caso clásico de apps que recopilan datos innecesarios para venderlos a empresas de marketing.",
                    severity = Risk.HIGH,
                    recommendation = "Desinstala esta app. Usa la linterna integrada de Android o descarga una alternativa sin permisos desde F-Droid."
                )
            }
        }
        
        // Juegos que piden contactos
        if ((app.appName.contains("game", ignoreCase = true) || 
             app.appName.contains("juego", ignoreCase = true)) &&
            app.permissions.any { it.name.contains("CONTACTS") }) {
            return PrivacyThreat(
                title = "🎮 Juego con acceso a contactos",
                explanation = "Este juego pide acceso a tus contactos. Esto no es necesario para jugar, y probablemente los use para spam (invitar contactos), marketing, o construcción de perfiles de usuario.",
                severity = Risk.MEDIUM,
                recommendation = "Revoca el permiso de contactos. El juego debería funcionar igual."
            )
        }
        
        return null
    }
    
    /**
     * Genera un resumen ejecutivo de privacidad.
     */
    fun generatePrivacySummary(app: AppAudit, threats: List<PrivacyThreat>): String {
        if (threats.isEmpty()) {
            return "✅ Esta app parece respetar tu privacidad. Los permisos solicitados son razonables para su función."
        }
        
        val critical = threats.count { it.severity == Risk.CRITICAL }
        val high = threats.count { it.severity == Risk.HIGH }
        
        return when {
            critical > 0 -> "🚨 PELIGRO: Esta app tiene $critical amenazas CRÍTICAS de privacidad. Recomendamos desinstalarla."
            high > 0 -> "⚠️ RIESGO ALTO: Esta app tiene $high amenazas serias de privacidad. Revisa los permisos y considera alternativas."
            else -> "⚡ PRECAUCIÓN: Esta app tiene algunos problemas de privacidad menores. Revisa los permisos otorgados."
        }
    }
}
