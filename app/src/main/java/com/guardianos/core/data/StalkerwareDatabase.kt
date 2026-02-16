package com.guardianos.core.data

/**
 * Base de datos LOCAL de stalkerware conocido.
 * NO requiere conexión a internet. TODO procesado en el dispositivo.
 * 
 * Incluye:
 * - Apps de espionaje comerciales
 * - Apps de "control parental" mal usadas
 * - Apps con capacidad de monitoreo oculto
 */
object StalkerwareDatabase {
    
    data class StalkerwareApp(
        val packageName: String,
        val name: String,
        val description: String,
        val capabilities: List<String>,
        val severity: String = "CRITICAL"
    )
    
    /**
     * Apps de espionaje comercial conocidas (actualizado 2026).
     */
    val KNOWN_STALKERWARE = listOf(
        // FlexiSPY y derivados
        StalkerwareApp(
            "com.flexispy.android",
            "FlexiSPY",
            "App comercial de espionaje que registra llamadas, SMS, ubicación, WhatsApp, etc.",
            listOf("Llamadas", "SMS", "Ubicación GPS", "WhatsApp", "Redes sociales", "Keylogger", "Cámara/Micrófono remoto")
        ),
        StalkerwareApp(
            "com.spy.android",
            "SPY24",
            "Software de espionaje que se oculta completamente del launcher",
            listOf("SMS", "Llamadas", "Ubicación", "Keylogger", "Historial navegador")
        ),
        
        // mSpy y variantes
        StalkerwareApp(
            "com.mspy.android",
            "mSpy",
            "App de monitoreo popular usado para espiar parejas",
            listOf("SMS", "Llamadas", "Ubicación GPS", "WhatsApp", "Facebook", "Snapchat", "Keylogger")
        ),
        StalkerwareApp(
            "com.mobile.spy",
            "Mobile Spy",
            "Variante de mSpy con capacidad de grabación remota",
            listOf("Llamadas", "Ambiente", "Cámara", "Ubicación", "Mensajería")
        ),
        
        // Hoverwatch
        StalkerwareApp(
            "com.hoverwatch.android",
            "Hoverwatch",
            "App que captura screenshots cada vez que el usuario cambia de app",
            listOf("Screenshots automáticos", "Ubicación", "SMS", "Llamadas", "WhatsApp")
        ),
        
        // TheTruthSpy
        StalkerwareApp(
            "com.thetruthspy.android",
            "TheTruthSpy",
            "Espionaje con capacidad de grabación de ambiente",
            listOf("Grabación ambiente", "Cámara remota", "Ubicación GPS", "Keylogger", "Mensajes")
        ),
        
        // Spyzie/Cocospy
        StalkerwareApp(
            "com.spyzie.android",
            "Spyzie",
            "App china de espionaje con acceso remoto total",
            listOf("Ubicación en tiempo real", "Geofencing", "SMS", "Llamadas", "Apps de mensajería")
        ),
        StalkerwareApp(
            "com.cocospy.android",
            "Cocospy",
            "Hermano de Spyzie con las mismas capacidades",
            listOf("Ubicación", "Keylogger", "WhatsApp", "Control remoto")
        ),
        
        // Cerberus (legítimo pero usado para stalking)
        StalkerwareApp(
            "com.lsdroid.cerberus",
            "Cerberus Anti-Robo",
            "App legítima anti-robo pero mal usada para espionaje por su capacidad de ocultarse",
            listOf("Ubicación GPS", "Fotos remotas", "Grabación audio", "Borrado remoto", "Ocultar icono")
        ),
        
        // KidLogger (control parental mal usado)
        StalkerwareApp(
            "com.kidlogger.android",
            "KidLogger",
            "Control parental que registra TODO lo que hace el usuario",
            listOf("Keylogger", "Screenshots", "Historial", "Apps usadas", "Tiempo de uso")
        ),
        
        // XNSPY
        StalkerwareApp(
            "com.xnspy.android",
            "XNSPY",
            "Espionaje comercial con panel web de control",
            listOf("Llamadas", "SMS", "WhatsApp", "Ubicación", "Keylogger", "Grabación ambiente")
        ),
        
        // PhoneSpector
        StalkerwareApp(
            "com.phonespector.android",
            "PhoneSpector",
            "Especializado en espiar iPhone/Android sin jailbreak/root",
            listOf("iMessage", "WhatsApp", "Fotos", "Videos", "Ubicación")
        ),
        
        // Highster Mobile
        StalkerwareApp(
            "com.highster.mobile",
            "Highster Mobile",
            "Espionaje USA con capacidad de recuperar mensajes borrados",
            listOf("SMS borrados", "Llamadas", "Ubicación", "Fotos", "Mensajería")
        ),
        
        // Auto Forward Spy
        StalkerwareApp(
            "com.autoforward.spy",
            "Auto Forward Spy",
            "Graba llamadas y ambiente automáticamente",
            listOf("Grabación llamadas", "Grabación ambiente", "Ubicación GPS", "SMS", "WhatsApp")
        ),
        
        // TheTruthSpy (variantes)
        StalkerwareApp(
            "com.android.systemupdate",
            "System Update (Fake)",
            "Stalkerware disfrazado de actualización del sistema",
            listOf("Oculto", "Keylogger", "Ubicación", "SMS", "Llamadas")
        ),
        
        // GuestSpy
        StalkerwareApp(
            "com.guestspy.android",
            "GuestSpy",
            "Espionaje con interfaz web para el atacante",
            listOf("Ubicación tiempo real", "Geofencing", "SMS", "Llamadas", "WhatsApp", "Tinder", "Bumble")
        ),
        
        // Spyic
        StalkerwareApp(
            "com.spyic.android",
            "Spyic",
            "Versión sin root de software de espionaje",
            listOf("Ubicación", "Mensajes", "Llamadas", "Navegador", "Calendarios")
        ),
        
        // Spyera
        StalkerwareApp(
            "com.spyera.android",
            "Spyera",
            "Software profesional de espionaje gubernamental ahora comercial",
            listOf("Interceptación llamadas", "VoIP", "Mensajería cifrada", "Ubicación", "Keylogger avanzado")
        ),
        
        // MobiStealth
        StalkerwareApp(
            "com.mobistealth.android",
            "MobiStealth",
            "Modo stealth completo, invisible en el dispositivo",
            listOf("Modo invisible", "Keylogger", "Email", "Chat", "Ubicación", "Historial")
        )
    )
    
    /**
     * Patrones de nombres de paquetes sospechosos.
     * Apps que intentan parecer legítimas del sistema.
     */
    val SUSPICIOUS_PACKAGE_PATTERNS = listOf(
        // Nombres engañosos comunes
        "system.update",
        "android.settings",
        "google.services",
        "android.service",
        "system.service",
        "device.update",
        "security.update",
        "system.config",
        "android.system",
        "system.manager",
        "device.manager",
        "android.monitor",
        "system.monitor",
        
        // Nombres genéricos usados por stalkerware
        "monitor",
        "tracker",
        "spy",
        "stealth",
        "hidden",
        "secret",
        "invisible",
        "remote",
        "control",
        "parental",
        "kidcontrol",
        "childmonitor"
    )
    
    /**
     * Combinaciones de permisos altamente sospechosas.
     * Si una app tiene TODOS estos permisos, es muy probable stalkerware.
     */
    val STALKERWARE_PERMISSION_PROFILES = listOf(
        // Perfil 1: Espionaje total
        listOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_CALL_LOG",
            "android.permission.CAMERA"
        ),
        
        // Perfil 2: Keylogger + ubicación
        listOf(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_SMS",
            "android.permission.INTERNET"
        ),
        
        // Perfil 3: Grabación remota
        listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.INTERNET",
            "android.permission.WAKE_LOCK"
        ),
        
        // Perfil 4: Monitoreo de comunicaciones
        listOf(
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_CONTACTS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.INTERNET"
        )
    )
    
    /**
     * Servicios de accesibilidad conocidos por stalkerware.
     * El servicio de accesibilidad permite keylogging y captura de pantalla.
     */
    val MALICIOUS_ACCESSIBILITY_SERVICES = listOf(
        "AccessibilityService",
        "KeyloggerService",
        "MonitorService",
        "ScreenCaptureService",
        "InputMonitor",
        "KeyboardMonitor"
    )
    
    /**
     * Apps legítimas de "control parental" que pueden ser mal usadas.
     * Se marcan con severidad MEDIUM en lugar de CRITICAL.
     */
    val DUAL_USE_APPS = listOf(
        StalkerwareApp(
            "net.qustodio.app",
            "Qustodio",
            "Control parental legítimo pero puede ser usado para espiar adultos",
            listOf("Ubicación", "Apps", "Navegación", "Tiempo de uso", "Bloqueo remoto"),
            "MEDIUM"
        ),
        StalkerwareApp(
            "com.screentime.rc",
            "Screen Time",
            "Control parental con capacidad de monitoreo total",
            listOf("Tiempo de uso", "Apps", "Ubicación", "Mensajes"),
            "MEDIUM"
        ),
        StalkerwareApp(
            "com.google.android.apps.kids.familylink",
            "Google Family Link",
            "Legítimo para menores pero inapropiado para adultos",
            listOf("Ubicación", "Apps", "Tiempo de uso", "Aprobación apps"),
            "MEDIUM"
        ),
        StalkerwareApp(
            "com.life360.android.safetymapd",
            "Life360",
            "Localización familiar, puede ser abusivo si se instala sin consentimiento",
            listOf("Ubicación tiempo real", "Historial ubicaciones", "Alertas", "Chat"),
            "MEDIUM"
        )
    )
}
