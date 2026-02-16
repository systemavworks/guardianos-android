# 🚨 Plan de Estabilización P0 - GuardianOS

**Fecha:** 11 de febrero de 2026  
**Prioridad:** CRÍTICA (blocker para release público)  
**Dispositivo problema:** OPPO A80 (ColorOS) + ecosistema BBK Electronics

---

## 📊 Estado Actual

### ✅ Funciones Implementadas (10/10)
- [x] Stalkerware Detection (CAPA 5)
- [x] Guardian Shield (monitorización tiempo real)
- [x] ISO 27001 Audit
- [x] Network Analysis
- [x] Media Access Control
- [x] Forensic Reports
- [x] Privacy Proactive (Panic Mode)
- [x] Consulting
- [x] **Scan History** (UI completa con comparación temporal)
- [x] **Family Vault** (UI completa con cifrado AES-256-GCM)

### ⚠️ Problemas Detectados

#### 1. Crashes en OPPO A80 (ColorOS)
**Síntomas:**
- App se cierra inesperadamente durante operaciones de red
- Posible corrupción de datos en Family Vault si crash durante cifrado

**Causa raíz potencial:**
- ColorOS tiene políticas agresivas de gestión de memoria
- Background processes terminados prematuramente
- Restricciones DNS específicas del fabricante

**Riesgo crítico:** ❌  
*El cifrado AES-256-GCM requiere operación atómica. Un crash durante cifrado/descifrado puede corromper datos IRREVERSIBLEMENTE.*

#### 2. Bloqueo DNS en BBK Electronics (OPPO/OnePlus/Realme/Vivo)
**Síntomas:**
- Conexiones DNS locales (1.1.1.1, 9.9.9.9) bloqueadas
- NetworkGuardian no puede resolver IPs externas
- Timeout en auditorías que requieren conectividad

**Causa raíz:**
- ColorOS/OxygenOS filtran DNS para "protección familiar"
- Private DNS forzado en algunos modelos
- Apps de seguridad penalizadas por sistema

---

## 🔧 Soluciones Implementadas (P0)

### 1. ✅ CrashHandler Ético
**Archivo:** `core/crash/CrashHandler.kt` (150 líneas)

**Capacidades:**
- Captura crashes con `UncaughtExceptionHandler`
- Guarda logs locales en `filesDir/crashes/` (sin permisos externos)
- Rotación automática (max 10 logs)
- Stack trace completo + device info + causa raíz (3 niveles)
- **CERO telemetría externa** (filosofía ética)

**Uso diagnóstico:**
```kotlin
// Ver últimos 3 crashes
val recentCrashes = CrashHandler.getRecentCrashes(context)

// Borrar logs por usuario
CrashHandler.clearAllCrashLogs(context)
```

**Justificación ética:**  
> "Preferimos logs auditables localmente que telemetría opaca enviada a servidores."

---

### 2. ✅ DNSFixer para BBK Devices
**Archivo:** `core/network/DNSFixer.kt` (200 líneas)

**Capacidades:**
- Detecta fabricantes problemáticos (OPPO, OnePlus, Realme, Vivo)
- Activa workaround DNS-over-HTTPS transparent
- Guarda configuración en SharedPreferences para NetworkGuardian
- **Informa al usuario qué hace** (Toast transparente)

**Tests implementados:**
```kotlin
// Test conectividad DNS
val status = DNSFixer.checkDNSConnectivity(context)
// Retorna: Google DNS, Cloudflare DNS, Quad9 DNS

// Ver estado del workaround
val workaroundStatus = DNSFixer.getWorkaroundStatus(context)
```

**Justificación ética:**  
> "Usamos DNS alternativo SOLO en dispositivos problemáticos, y lo decimos claramente al usuario. Sin trucos ocultos."

---

### 3. ✅ DiagnosticsScreen (Transparency Tool)
**Archivo:** `core/ui/DiagnosticsScreen.kt` (450 líneas)

**Pantalla de transparencia técnica accesible desde AboutScreen** → Botón "🔧 Diagnóstico Técnico"

**Muestra:**
- 📱 Device Info (fabricante, modelo, Android SDK)
- 🌐 Estado DNS (workaround activo?, tests de conectividad)
- 💥 Crash Logs recientes (últimos 3, con timestamp y tamaño)
- 🔄 Botones: "Comprobar Conectividad", "Borrar Logs"

**Filosofía:**  
> "El usuario debe ver EXACTAMENTE qué hace la app internamente. Cero cajas negras."

---

## 📅 Roadmap de Estabilización

### Semana 1: Testing Intensivo (Esta Semana)

#### Día 1-2: Recolección de datos
```bash
# Compilar versión PRO debug con handlers P0
./gradlew assembleProDebug

# Instalar en OPPO A80
adb install app/build/outputs/apk/pro/debug/app-pro-debug.apk

# Monitorizar logcat en tiempo real
adb logcat | grep -E "(GUARDIAN_CRASH|DNSFixer)"
```

**Acciones de testing:**
1. ✅ Escaneo FULL con todas las capas (5 capas incluido stalkerware)
2. ✅ Guardian Shield activo durante 30 minutos
3. ✅ Family Vault: crear master password → añadir 5 credenciales → cerrar app → reabrir
4. ✅ Scan History: escanear 3 veces → comparar → verificar JSON intacto
5. ✅ Network Analysis con DNSFixer activo
6. ✅ ISO 27001 audit completo
7. ✅ Panic Mode (borrado vault + historial)

**Criterios de éxito Día 1-2:**
- ❌ Si >1 crash: analizar logs, identificar causa raíz
- ⚠️ Si bloqueo DNS: verificar workaround efectivo
- ✅ Si 0 crashes + DNS OK: proceder a Día 3-4

#### Día 3-4: Testing estabilidad extendido
- Dejar app ejecutándose 48h con Guardian Shield activo
- Simular condiciones adversas:
  - Memoria baja (abrir 10+ apps pesadas)
  - Conexión intermitente (activar/desactivar WiFi)
  - Rotaciones de pantalla durante operaciones
  - Background/foreground transitions rápidas

**Checklist crítico:**
- [ ] Family Vault no corrompe datos tras 10+ operaciones encrypt/decrypt
- [ ] Scan History mantiene integridad JSON tras 20+ escaneos
- [ ] NetworkGuardian no crashea con DNS bloqueado
- [ ] Guardian Shield no drena batería (max 5% tras 24h)
- [ ] CrashHandler captura TODOS los crashes (no hay crashes silenciosos)

#### Día 5: Validación multi-fabricante
Repetir testing en:
- [ ] Samsung Galaxy (OneUI)
- [ ] Xiaomi (MIUI)
- [ ] Motorola (Android stock)
- [ ] Google Pixel (Android puro)

**Meta:** 0 crashes en cualquier dispositivo mainstream.

---

### Semana 2: Hardening y Release Candidate

#### Mejoras post-testing
```kotlin
// Si crashes detectados en Family Vault:
// 1. Implementar transacciones atómicas
try {
    CipherManager.startTransaction()
    // ... operación cifrado ...
    CipherManager.commitTransaction()
} catch (e: Exception) {
    CipherManager.rollbackTransaction()
    throw CriticalSecurityException("Vault corruption prevented")
}

// 2. Añadir checksums SHA-256 en cada operación
val checksum = MessageDigest.getInstance("SHA-256").digest(data)
metadata.put("checksum", checksum.toHex())

// 3. Backup automático antes de modificaciones
FamilyVault.createAutoBackup(context)
```

#### Release Candidate (RC1)
- [ ] Compilar versión PRO release con obfuscación ProGuard
- [ ] Firmar APK con keystore producción
- [ ] Subir a F-Droid repo (metadata + changelogs)
- [ ] Publicar en https://guardianos.es/pro/download
- [ ] Anuncio en redes: "GuardianOS PRO 2.0.0 - Ahora estable en OPPO/OnePlus"

---

## 🎯 Criterios de Release Público

### Bloqueadores absolutos (0 tolerancia)
- ❌ **Crashes en Family Vault durante cifrado**  
  *Razón: Puede corromper documentos sensibles (DNI, pasaportes)*
  
- ❌ **Pérdida de datos en Scan History**  
  *Razón: Usuario pierde evidencia temporal de amenazas*
  
- ❌ **Guardian Shield drena batería >10% diario**  
  *Razón: Rompe promesa de monitorización sostenible*

### Warnings aceptables (con workaround documentado)
- ⚠️ **DNS bloqueado en BBK devices**  
  *Solución: DNSFixer automático + mensaje transparente*
  
- ⚠️ **Permisos Accessibility denegados en algunas ROMs**  
  *Solución: Mensaje educativo sobre limitaciones del fabricante*

---

## 📝 Documentación Transparente para Usuario Final

### Pantalla "Known Issues" en AboutScreen
```markdown
#### 🔧 Problemas Conocidos

**OPPO/OnePlus/Realme (ColorOS/OxygenOS):**
- Algunos dispositivos bloquean DNS local. GuardianOS usa DNS-over-HTTPS automáticamente.
- Si experimentas crashes, activa "Modo Diagnóstico" en Ajustes.

**Xiaomi (MIUI):**
- Requiere permisos especiales para Guardian Shield. Sigue las instrucciones en pantalla.

**Samsung (OneUI):**
- Batería optimizada puede pausar monitorización. Añade GuardianOS a "Apps sin optimizar".

📧 ¿Problemas en tu dispositivo? Contacta info@guardianos.es con logs de diagnóstico.
```

---

## ✅ Checklist Final Pre-Release

### Testing Funcional
- [ ] 10/10 funciones PRO ejecutan sin crashes (72h)
- [ ] Family Vault: 100 operaciones encrypt/decrypt exitosas
- [ ] Scan History: 50 escaneos + 10 comparaciones OK
- [ ] DNSFixer efectivo en 5+ dispositivos BBK
- [ ] CrashHandler captura y guarda logs correctamente

### Testing No-Funcional
- [ ] Consumo batería <5% diario (Guardian Shield activo)
- [ ] Uso RAM <150MB promedio
- [ ] Almacenamiento: Vault + History <50MB tras 30 días uso
- [ ] Inicio app <2 segundos en gama media

### Cumplimiento Ético
- [ ] Cero conexiones a servidores externos (excepto Exodus API si user acepta)
- [ ] Cero trackers (validar con Exodus Privacy)
- [ ] Logs 100% locales (CrashHandler sin telemetría)
- [ ] Código auditable en GitHub (GPL v3)

### F-Droid Compliance
- [ ] Sin binarios Gradle (ejecutar `prepare-for-fdroid.sh`)
- [ ] Sin deps propietarias en flavour FREE
- [ ] Metadatos actualizados (`fastlane/metadata/android/es-ES/`)
- [ ] Screenshots de producción (sin mockups)

---

## 🚀 Post-Release (Monitoring Pasivo)

### Feedback Loop Ético
```markdown
# En AboutScreen → "Reportar Problema"
✉️ Email directo a info@guardianos.es con:
  - Logs de diagnóstico (opcional, usuario decide)
  - Modelo de dispositivo
  - Descripción del problema

❌ NO implementamos analytics automático
✅ Usuario controla qué información comparte
```

### Métricas de Estabilidad (Manual)
- Conteo emails soporte/semana
- Crashes reportados por modelo dispositivo
- Tiempo medio resolución issues

**Meta:** <5 emails crash/semana tras 1 mes en producción

---

**Autor:** Victor Shift Lara  
**Última actualización:** 11 febrero 2026  
**Siguiente revisión:** Post-testing 72h

