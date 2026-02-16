# Copilot Instructions for guardianos-android

GuardianOS es una app de auditoría de seguridad para Android (GPL v3) con arquitectura modular y doble flavor (free/pro).


## Arquitectura y flujo de datos

### Estructura modular
```
app/src/main/java/com/guardianos/
├── core/                  # Lógica principal
│   ├── MainActivity.kt    # Orquestador UI (Compose) + flujos de auditoría
│   ├── audit/             # Sistema de auditoría (AppAuditor, ISOAuditor, StalkerwareDetector)
│   ├── domain/model/      # Modelos de dominio (AppAudit, Risk, AuditFinding, etc.)
│   ├── data/              # Bases de datos locales (MalwareDatabase, StalkerwareDatabase)
│   ├── monitor/           # GuardianShieldMonitor (análisis de permisos en tiempo real)
│   ├── network/           # NetworkGuardian (análisis de tráfico de red)
│   ├── pdf/               # PDFGenerator (informes estándar y Pro)
│   └── pro/               # Funciones avanzadas (ScanHistory, FamilyVault, PrivacyAnalyzer, etc.)
└── vault/                 # Gestión de credenciales y cifrado
    ├── data/              # Modelos (FamilyCredential, SecureDocument, enums)
    ├── security/          # CipherManager (AES-GCM), VaultSecurityManager, BiometricAuthManager
    └── ui/                # Pantallas Compose (VaultScreens.kt, ProScreens.kt)
```

### Flujo de auditoría típico
1. Usuario inicia escaneo en `MainActivity` → `auditApps(context, mode)`
2. `AppAuditor.auditApps()` analiza apps instaladas en 3 capas:
   - **CAPA 1**: Huellas estáticas (SHA-256 certificados, malware DB, trackers conocidos)
   - **CAPA 2**: Heurística (combinaciones de permisos peligrosos, nombres sospechosos)
   - **CAPA 3**: Análisis avanzado (Exodus API, ISO 27001, APK structure, stalkerware)
3. Genera `List<AppAudit>` con riskScore + findings
4. En versión Pro: `ScanHistory.saveScan()` guarda JSON local
5. Usuario exporta PDF vía `PDFGenerator.generateReport()`


## Convenciones críticas

### Sistema de flavors
- **free**: F-Droid compliant (sin código Pro ni deps privativas, `BuildConfig.PRO_VERSION = false`)
- **pro**: Funciones avanzadas (`BuildConfig.PRO_VERSION = true`)
- **Control de acceso Pro**:
  ```kotlin
  if (BuildConfig.PRO_VERSION && isProActivated(context)) {
      // Función Pro habilitada
  }
  ```
  - Activación vía `SharedPreferences` ("guardianos_pro" → "activated")
  - Validar con `isProActivated(context)` antes de mostrar/ejecutar funciones Pro

### Arquitectura de datos
- **Modelos de dominio**: Inmutables en `domain/model/` (`data class` con validación)
- **Serialización**: Gson para historial/vault (`TypeToken` para listas genéricas)
- **Cifrado**: 
  - `CipherManager`: AES-GCM 256 bits (doble capa: vault + items individuales)
  - `VaultSecurityManager`: Master password (SHA-256 + salt aleatorio 32 bytes), max 5 intentos, auto-logout 2min
- **UI**: 100% Jetpack Compose (Material3), screens en `MainActivity` y `vault/ui/`

### Reglas de seguridad
- **Family Vault**: Todas las credenciales se cifran doblemente antes de guardar en JSON local
- **Historial**: Solo en Pro, JSON sin cifrado (contiene AppAudit público)
- **Clipboard**: Auto-borrado tras 30s (`Handler.postDelayed`)
- **Modo pánico**: `PanicMode.triggerPanic()` borra vault + historial instantáneamente


## Workflows de desarrollo

### Build y deployment
```bash
# F-Droid (solo free)
./gradlew assembleFreeRelease

# Pro (GitHub Releases, activación por licencia local)
./gradlew assembleProRelease

# Debug con logs
./gradlew assembleProDebug
adb logcat | grep GuardianOS

# Preparar repo para F-Droid (elimina binarios Gradle)
./prepare-for-fdroid.sh
```

### Testing
- Unit tests: `app/src/test/java/` (JUnit)
- Instrumented: `app/src/androidTest/java/` (Espresso)
- Test crítico: Verificar `BuildConfig.PRO_VERSION` en free builds

### Fastlane
- Metadatos F-Droid: `fastlane/metadata/android/es-ES/` (screenshots, descriptions)
- Actualizar changelogs por versionCode


## Integraciones y APIs

### Exodus Privacy API
```kotlin
// audit/api/ExodusApi.kt (Retrofit)
val trackers = ExodusClient.api.getAppData(packageName)
```
- Analiza trackers conocidos por packageName
- Fallback: base de datos local si API falla

### Bases de datos locales
- `MalwareDatabase`: Firmas SHA-256 + packageNames de malware conocido
- `StalkerwareDatabase`: Apps de vigilancia oculta (lista curada)
- Actualizar manualmente en `data/` tras investigación

### WorkManager (Pro)
```kotlin
// Escaneo automático programado
AuditScheduler.schedulePeriodicAudit(context, intervalHours = 24)
```


## Patrones de implementación

### Agregar nuevo chequeo de auditoría
1. Crear modelo en `domain/model/` si es necesario
2. Lógica en `audit/` (e.g., `AppAuditor.checkNewThreat()`)
3. Añadir finding en `auditApps()`:
   ```kotlin
   if (newThreatDetected) {
       findings.add(AuditFinding("⚠️ Título", "Descripción", riskScore = 15))
       score += 15
   }
   ```
4. Actualizar `Risk.kt` si requiere nueva categoría

### Nueva función Pro
1. Implementar en `core/pro/NewFeature.kt` (object o class)
2. Condicionar acceso:
   ```kotlin
   if (BuildConfig.PRO_VERSION && isProActivated(context)) {
       NewFeature.execute()
   } else {
       // Mostrar dialog de upgrade
   }
   ```
3. UI en `MainActivity` o `vault/ui/ProScreens.kt` (Compose)
4. Guardar estado en `SharedPreferences` o JSON cifrado

### Gestión de credenciales (Family Vault)
```kotlin
// Guardar (doble cifrado automático)
FamilyVault.saveCredential(context, FamilyCredential(
    id = UUID.randomUUID().toString(),
    title = "Gmail",
    username = "user@gmail.com",
    password = "secret123",
    category = "Email"
))

// Cargar y buscar
val creds = FamilyVault.loadCredentials(context)
val filtered = FamilyVault.searchCredentials(context, "gmail")

// Copiar contraseña (auto-borrado 30s)
FamilyVault.copyPasswordToClipboard(context, password)
```


## Archivos críticos

- **MainActivity.kt** (2700+ líneas): Orquestador completo (UI, auditoría, navegación, activación Pro)
- **AppAuditor.kt**: Motor de análisis de seguridad (3 capas, scoring)
- **FamilyVault.kt**: Gestión de credenciales (cifrado, búsqueda, backup)
- **VaultSecurityManager.kt**: Master password, auto-logout, intentos
- **build.gradle** (app): Configuración de flavors, versionCode, dependencias
- **prepare-for-fdroid.sh**: Script de limpieza pre-publicación

## Decisiones de diseño

- **Sin Room/SQLite**: JSON local + Gson por simplicidad (auditabilidad F-Droid)
- **Sin MVVM/MVP**: Arquitectura funcional directa (object + suspend functions)
- **Compose sin ViewModel**: State management local en @Composable (MutableState)
- **Licencia GPL v3**: Todo el código es auditable y modificable
- **Monetización ética**: Pro via licencia local (sin servidores externos, sin DRM invasivo)


Actualiza este archivo tras cambios en arquitectura, flujos de auditoría o sistema de activación Pro.
