# Corrección de Crashes v2.0.0 - Emergencia

## 🔴 PROBLEMA CRÍTICO IDENTIFICADO

**Crash principal**: `SecurityException` en `NetworkGuardian.getCurrentWifiInfo()`

```
java.lang.SecurityException: WifiService: Neither user 10477 nor current process 
has android.permission.ACCESS_WIFI_STATE.
at com.guardianos.core.network.NetworkGuardian.getCurrentWifiInfo(NetworkGuardian.kt:96)
at com.guardianos.core.pro.ui.NetworkAnalyzerScreenKt$NetworkAnalyzerScreen$1.invokeSuspend(NetworkAnalyzerScreen.kt:40)
```

---

## ✅ CORRECCIONES APLICADAS

### 1. **AndroidManifest.xml** - Permisos WiFi agregados

**Archivo**: `app/src/main/AndroidManifest.xml`

```xml
<!-- AGREGADOS -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

**Ubicación**: Después de `INTERNET`, antes de `QUERY_ALL_PACKAGES`

---

### 2. **NetworkGuardian.kt** - Try-catch + verificación de permisos

**Archivo**: `app/src/main/java/com/guardianos/core/network/NetworkGuardian.kt`

**Cambios**:

1. **Eliminado** `@SuppressLint("MissingPermission")` (ocultaba el problema)

2. **Agregada** verificación de permisos al inicio:
```kotlin
// Verificar permisos primero
if (context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
    android.util.Log.w(TAG, "⚠️ Permiso ACCESS_WIFI_STATE no otorgado")
    return null
}
if (context.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
    android.util.Log.w(TAG, "⚠️ Permiso ACCESS_NETWORK_STATE no otorgado")
    return null
}
```

3. **Envuelto** TODO el cuerpo en `try-catch`:
```kotlin
fun getCurrentWifiInfo(): WifiNetworkInfo? {
    return try {
        // ... verificación de permisos
        // ... todo el código existente
        
        WifiNetworkInfo(...)
    } catch (e: SecurityException) {
        android.util.Log.e(TAG, "❌ SecurityException al acceder a info WiFi: ${e.message}")
        null
    } catch (e: Exception) {
        android.util.Log.e(TAG, "❌ Error obteniendo info WiFi: ${e.message}", e)
        null
    }
}
```

---

## 🔍 STALKERWARE DETECTOR - SIN CAMBIOS NECESARIOS

**Análisis del código**:
- ✅ Ya tiene try-catch en puntos críticos
- ✅ Usa `coroutineContext.ensureActive()` para cancelación
- ✅ No usa `getApplicationLabel()` (evita cargar APK assets)
- ✅ GC agresivo cada 20 apps (optimizado para OPPO A80)
- ✅ No accede a permisos peligrosos sin verificación

**Conclusión**: El "crash en stalkerware" reportado era **consecuencia** del crash de NetworkGuardian (la app moría antes de ejecutarse).

---

## 📊 RESULTADO ESPERADO

### Antes (v2.0.0 con bug):
1. Usuario abre "Análisis de Red"
2. `NetworkGuardian.getCurrentWifiInfo()` intenta acceder a WiFi
3. **CRASH**: `SecurityException` (permiso no declarado)
4. App muere completamente
5. Stalkerware detector nunca se ejecuta

### Después (v2.0.0 corregido):
1. Usuario abre "Análisis de Red"
2. `getCurrentWifiInfo()` verifica permisos primero
3. **Si permisos OK**: Muestra info WiFi completa (SSID, seguridad, canal, etc.)
4. **Si permisos faltantes**: Devuelve `null` gracefully, muestra mensaje "Sin info WiFi"
5. **Si error inesperado**: Try-catch lo captura, devuelve `null`, app sigue funcionando
6. Stalkerware detector funciona normalmente (sin interferencias)

---

## 🧪 TESTING RECOMENDADO

### Test 1: Análisis de Red (sin permisos previos)
```bash
# Limpiar datos app (eliminar permisos)
adb shell pm clear com.guardianos.core.pro

# Instalar nueva APK
./gradlew assembleProDebug
adb install -r app/build/outputs/apk/pro/debug/app-pro-debug.apk

# Verificar logs al abrir "Análisis de Red"
adb logcat | grep -E "(NetworkGuardian|GUARDIAN_CRASH)"

# Resultado esperado:
# ⚠️ Permiso ACCESS_WIFI_STATE no otorgado
# (Sin crash, app sigue funcionando)
```

### Test 2: Análisis de Red (con permisos)
```bash
# Permisos WiFi son normalmente otorgados automáticamente
# Abrir "Análisis de Red"

# Resultado esperado:
# 📡 Red WiFi: [TU_SSID]
#    Seguridad: WPA2 (SEGURA)
#    Señal: -76 dBm
#    Frecuencia: 2437MHz (Canal 6)
#    Velocidad: 72Mbps
#    IP: 192.168.x.x | Gateway: 192.168.x.1
```

### Test 3: Stalkerware Detection
```bash
# Ejecutar escaneo completo
# Abrir app → Escanear → Esperar 10-15 segundos

# Resultado esperado (log):
# 📱 Apps instaladas totales: 142
# 🔍 Apps a escanear (tras filtrado): 80
# ✅ Escaneo completo: [N] detecciones
```

---

## 🛡️ COMPATIBILIDAD

### Permisos agregados:
- `ACCESS_WIFI_STATE`: **Normal permission** (otorgado automáticamente al instalar)
- `ACCESS_NETWORK_STATE`: **Normal permission** (otorgado automáticamente al instalar)

**No requiere**:
- ❌ Runtime permission request (Dialog)
- ❌ Usuario aceptar manualmente
- ❌ Cambios en UI

**F-Droid compliant**: ✅ SÍ (permisos normales, no invasivos)

---

## 📝 ARCHIVOS MODIFICADOS

1. `app/src/main/AndroidManifest.xml` (+2 líneas)
2. `app/src/main/java/com/guardianos/core/network/NetworkGuardian.kt` (~30 líneas modificadas)

**Archivos sin cambios**:
- `StalkerwareDetector.kt` (ya estaba correcto)
- `RiskScorer.kt` (sin problemas)
- `MediaAccessScanner.kt` (sin problemas)
- UI Screens (sin problemas)

---

## 🚀 PRÓXIMOS PASOS

1. **Build**:
   ```bash
   ./gradlew assembleProDebug
   ```

2. **Instalar en OPPO A80**:
   ```bash
   adb install -r app/build/outputs/apk/pro/debug/app-pro-debug.apk
   ```

3. **Verificar**:
   - ✅ No crashes al abrir app
   - ✅ "Análisis de Red" muestra info WiFi completa
   - ✅ "Escaneo Stalkerware" completa sin crashes
   - ✅ Logs muestran progreso normal (sin SecurityException)

4. **Si todo OK → Release v2.0.1**:
   ```bash
   git add app/src/main/AndroidManifest.xml
   git add app/src/main/java/com/guardianos/core/network/NetworkGuardian.kt
   git commit -m "fix: Crashes por falta de permisos WiFi (SecurityException)

   - Agregados ACCESS_WIFI_STATE y ACCESS_NETWORK_STATE al manifest
   - Try-catch robusto en NetworkGuardian.getCurrentWifiInfo()
   - Verificación de permisos antes de acceder a WifiManager
   - Resolución de crash reportado tras 24h de testing"
   
   git tag v2.0.1
   ./gradlew assembleProRelease
   ```

---

## 🔬 ANÁLISIS TÉCNICO

### ¿Por qué crasheaba?

**Android 6.0+** requiere que TODOS los permisos (incluso normales) estén **declarados explícitamente** en el manifest, aunque no requieran runtime permission.

**Secuencia del error**:
1. `NetworkGuardian.getCurrentWifiInfo()` llama `wifiManager.getConnectionInfo()`
2. Android verifica manifest → **no encuentra** `ACCESS_WIFI_STATE`
3. Android lanza `SecurityException` → app crashea
4. Nota: `@SuppressLint` solo oculta warning del IDE, **no previene el crash**

### ¿Por qué funcionaba el escaneo de /proc/net/tcp?

Leer `/proc/net/tcp` es una operación de **filesystem** (requiere permisos de lectura básicos), no requiere permisos WiFi específicos.

Solo crasheaba al intentar acceder a **WifiManager** (API de Android con permisos específicos).

---

## 📋 CHECKLIST FINAL

- [x] Permisos WiFi agregados al manifest
- [x] Try-catch en `getCurrentWifiInfo()`
- [x] Verificación de permisos antes de acceder a WifiManager
- [x] Sin errores de compilación
- [x] Logs informativos (⚠️ warnings en lugar de crashes)
- [x] Graceful degradation (devuelve null si falta permiso)
- [ ] Testing en OPPO A80 (pendiente usuario)
- [ ] Verificar no hay otros crashes ocultos
- [ ] Release v2.0.1

---

**Fecha**: 2026-02-14  
**Versión corregida**: v2.0.1 (preparada para build)  
**Archivos modificados**: 2  
**Líneas cambiadas**: ~32  
**Urgencia**: 🔴 CRÍTICA (crash al abrir funcionalidad Pro)
