# 🛡️ Guardian Shield - Guía de Debug

## Problemas resueltos

### ✅ 1. Botón PRO más compacto
- **Antes**: Botón grande "Activar PRO • 9,99€" que deformaba el layout
- **Ahora**: Botón outlined más pequeño "PRO 9.99€" (36dp altura, 11sp texto)

### ✅ 2. Intervalo de búsqueda optimizado
- **Antes**: Buscaba accesos de las últimas 2 horas
- **Ahora**: Busca accesos de la última 1 hora (más relevante para notificaciones)

### ✅ 3. Logging exhaustivo añadido
- **GuardianShieldMonitor**: Log completo de escaneo con estadísticas
- **GuardianShieldService**: Log detallado de cada decisión de notificación

## Cómo verificar que Guardian Shield funciona

### Paso 1: Compilar e instalar
```bash
./gradlew assembleProDebug
adb install -r app/build/outputs/apk/pro/debug/app-pro-debug.apk
```

### Paso 2: Activar PRO
1. Abre la app
2. Introduce código: `GUAR-XXXX-XXXX-XXXX`
3. Verifica que aparece "⭐ GuardianOS PRO"
4. **CIERRA Y VUELVE A ABRIR** la app para verificar persistencia

### Paso 3: Activar Guardian Shield
1. Toca el icono 🛡️ en el TopBar
2. Concede permisos:
   - **Acceso a uso de aplicaciones**: Settings > Special access > Usage access > GuardianOS
   - **Notificaciones**: Settings > Apps > GuardianOS > Notifications (activar)
3. Activa el switch "Guardian Shield"
4. Verifica que aparece notificación persistente: "🛡️ Guardian Shield Activo"

### Paso 4: Ver logs en tiempo real
```bash
adb logcat | grep -E "GuardianShield|PermissionAccess"
```

### Logs esperados

#### Al activar Guardian Shield:
```
D GuardianShieldMonitor: ═══════════════════════════════════════════
D GuardianShieldMonitor: Buscando accesos de las últimas 1 horas...
I GuardianShieldMonitor: ✓ Permiso PACKAGE_USAGE_STATS concedido
I GuardianShieldMonitor: ═══════════════════════════════════════════
I GuardianShieldMonitor: Resumen de escaneo:
I GuardianShieldMonitor:   Total eventos procesados: XXX
I GuardianShieldMonitor:   Apps omitidas (whitelist): XX
I GuardianShieldMonitor:   Apps omitidas (sistema): XX
I GuardianShieldMonitor:   Apps con permisos: XX
I GuardianShieldMonitor:   Accesos totales encontrados: XX
```

#### Cada 30 segundos (verificación):
```
D GuardianShieldService: ═══════════════════════════════════════════
D GuardianShieldService: Guardian Shield: Iniciando verificación de permisos...
I GuardianShieldService: ✓ Guardian Shield: X accesos detectados en última hora
D GuardianShieldService:   → Analizando: WhatsApp (com.whatsapp)
D GuardianShieldService:     Permiso: android.permission-group.MICROPHONE
D GuardianShieldService:     ✓ ES SENSIBLE → Generando notificación
D GuardianShieldService: Preparando notificación para WhatsApp - tu micrófono
I GuardianShieldService: ✅ Notificación enviada: WhatsApp - tu micrófono
I GuardianShieldService: Resumen: 1 notificaciones enviadas, 0 omitidas
```

#### Si NO detecta nada:
```
I GuardianShieldService: ✓ Guardian Shield: 0 accesos detectados en última hora
W GuardianShieldService: ⚠ Guardian Shield: NO se detectaron accesos. Posibles razones:
W GuardianShieldService:   1. Ninguna app usó permisos en la última hora
W GuardianShieldService:   2. Permiso PACKAGE_USAGE_STATS no concedido
W GuardianShieldService:   3. Todas las apps detectadas están en whitelist
```

## Diagnóstico de problemas

### Problema: "0 accesos detectados"

#### Causa 1: Permiso PACKAGE_USAGE_STATS no concedido
**Verificar:**
```bash
adb logcat | grep "NO tenemos permiso PACKAGE_USAGE_STATS"
```
**Solución:**
1. Settings > Special app access > Usage access
2. Buscar GuardianOS
3. Activar

#### Causa 2: No hay apps usando permisos
**Verificar:**
- Abre WhatsApp y haz una llamada (usa micrófono)
- Abre Instagram y toma una foto (usa cámara)
- Espera 30 segundos a que el servicio verifique
- Revisa logs

#### Causa 3: Apps en whitelist
**Verificar logs:**
```
D GuardianShieldMonitor:   ⊗ com.whatsapp: En whitelist
```

**NOTA**: WhatsApp/Instagram/Facebook **NO están** en la whitelist. Solo están:
- Apps del sistema Android
- Google Play Services
- Cámara/Teléfono del sistema
- Launchers (Samsung, Xiaomi, OPPO, Huawei)

### Problema: "Notificaciones no aparecen"

#### Verificar permisos Android 13+:
```bash
adb shell pm list permissions -d -g | grep POST_NOTIFICATIONS
```

#### Forzar permisos desde ADB:
```bash
adb shell pm grant es.guardianos.app android.permission.POST_NOTIFICATIONS
```

#### Verificar canal de notificaciones:
```bash
adb shell dumpsys notification | grep guardian_shield_alerts
```

### Problema: "Persistencia PRO se pierde"

#### Verificar SharedPreferences:
```bash
adb shell run-as es.guardianos.app cat /data/data/es.guardianos.app/shared_prefs/guardianos_pro.xml
```

Debe contener:
```xml
<boolean name="activated" value="true" />
<string name="activation_code">GUAR-XXXX-XXXX-XXXX</string>
```

#### Si el archivo no existe o está vacío:
El problema está en `ProActivationManager.saveActivationState()`. Verificar logs al activar PRO:
```bash
adb logcat | grep "ProActivation"
```

## Apps de prueba recomendadas

Para probar Guardian Shield, usa estas apps que **SÍ** deben generar notificaciones:

### 📷 Cámara:
- Instagram (com.instagram.android)
- Snapchat (com.snapchat.android)
- TikTok (com.zhiliaoapp.musically)

### 🎤 Micrófono:
- WhatsApp (com.whatsapp)
- Telegram (org.telegram.messenger)
- Discord (com.discord)

### 📍 Ubicación:
- Google Maps (com.google.android.apps.maps) - **whitelist, NO notifica**
- Uber (com.ubercab)
- Glovo (com.glovo)

## Estadísticas esperadas

Después de 1 hora de uso normal:
- **Apps escaneadas**: 50-200 (depende de uso)
- **Apps con permisos**: 5-15
- **Notificaciones generadas**: 1-10
- **Notificaciones omitidas**: 40-190 (sistema + whitelist)

## Comandos útiles

### Ver todas las notificaciones activas:
```bash
adb shell dumpsys notification
```

### Ver servicio Guardian Shield:
```bash
adb shell dumpsys activity services | grep GuardianShield
```

### Limpiar datos de la app (reset PRO):
```bash
adb shell pm clear es.guardianos.app
```

### Ver permisos concedidos:
```bash
adb shell dumpsys package es.guardianos.app | grep permission
```

## Checklist de verificación

- [ ] App compila sin errores
- [ ] Botón PRO se ve compacto y no deforma layout
- [ ] Activar PRO con código funciona
- [ ] Cerrar y abrir app mantiene PRO activo
- [ ] Permiso PACKAGE_USAGE_STATS concedido
- [ ] Permiso POST_NOTIFICATIONS concedido (Android 13+)
- [ ] Notificación persistente "Guardian Shield Activo" visible
- [ ] Logs muestran escaneos cada 30 segundos
- [ ] Logs muestran apps detectadas y análisis
- [ ] Al usar WhatsApp/Instagram, aparece log "✓ ES SENSIBLE"
- [ ] Notificación silenciosa aparece (sin sonido/vibración)
- [ ] Notificación muestra: "[App] está usando [permiso]"

## Solución rápida si nada funciona

```bash
# 1. Limpiar proyecto
./gradlew clean

# 2. Recompilar
./gradlew assembleProDebug

# 3. Desinstalar app anterior
adb uninstall es.guardianos.app

# 4. Instalar nueva
adb install -r app/build/outputs/apk/pro/debug/app-pro-debug.apk

# 5. Conceder todos los permisos manualmente
adb shell pm grant es.guardianos.app android.permission.POST_NOTIFICATIONS
adb shell pm grant es.guardianos.app android.permission.FOREGROUND_SERVICE
adb shell pm grant es.guardianos.app android.permission.PACKAGE_USAGE_STATS

# 6. Ver logs en tiempo real
adb logcat | grep -E "GuardianShield|ProActivation"
```

## Contacto y reporte de bugs

Si después de seguir esta guía Guardian Shield sigue sin funcionar, envía:
1. Logs completos: `adb logcat > guardian_shield_logs.txt`
2. Modelo de dispositivo: `adb shell getprop ro.product.model`
3. Versión Android: `adb shell getprop ro.build.version.release`
4. Screenshot de notificaciones activas
5. Screenshot de permisos concedidos

---

**Última actualización**: 11 febrero 2026
**Versión**: 1.0.0-pro-debug
