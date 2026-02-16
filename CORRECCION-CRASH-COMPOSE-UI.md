# 🔧 Corrección Crash Compose UI - LazyColumn con restricciones infinitas

**Fecha:** 14 de febrero de 2026  
**Versión afectada:** v2.0.0 (post-fix permisos WiFi)  
**Severidad:** CRÍTICA ⚠️  
**Plataforma:** OPPO A80, Android 15

---

## 📋 Resumen Ejecutivo

Después de corregir los crashes por permisos WiFi, apareció un nuevo crash de **IllegalStateException** en Compose UI cuando se intentaban mostrar los resultados del escaneo de stalkerware. El problema: `LazyColumn` anidados dentro de `Column` sin restricciones de altura explícitas.

**Estado:** ✅ **CORREGIDO** - 5 archivos modificados, compilación exitosa

---

## 💥 Crash detectado

### Stack trace completo
```
02-14 13:03:49.922  6364  6364 E AndroidRuntime: FATAL EXCEPTION: main
02-14 13:03:49.922  6364  6364 E AndroidRuntime: Process: com.guardianos.core.pro, PID: 6364
02-14 13:03:49.922  6364  6364 E AndroidRuntime: java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed. One of the common reasons is nesting layouts like LazyColumn and Column(Modifier.verticalScroll()). If you want to add a header before the list of items please add a header as a separate item() before the main items() inside the LazyColumn scope. There are could be other reasons for this to happen: your ComposeView was added into a LinearLayout with some weight, you applied Modifier.wrapContentSize(unbounded = true) or wrote a custom layout. Please try to remove the source of infinite constraints in the hierarchy above the scrolling container.
02-14 13:03:49.922  6364  6364 E AndroidRuntime: 	at androidx.compose.foundation.CheckScrollableContainerConstraintsKt.checkScrollableContainerConstraints-K40F9xA(CheckScrollableContainerConstraints.kt:35)
02-14 13:03:49.922  6364  6364 E AndroidRuntime: 	at androidx.compose.foundation.lazy.LazyListKt$rememberLazyListMeasurePolicy$1$1.invoke-0kLqBqw(LazyList.kt:187)
```

### Contexto del crash
- **Momento:** Inmediatamente después de completar el escaneo de stalkerware (52 apps detectadas)
- **Log previo:** `02-14 13:03:48.293 StalkerwareDetector: ✅ Escaneo completo: 51 detecciones`
- **Log siguiente:** `02-14 13:03:49.809 StalkerwareScreen: Escaneo completado: 52 apps con riesgo`
- **Crash:** 120ms después al intentar renderizar la UI de resultados

---

## 🔍 Análisis técnico

### Causa raíz
En Compose, **LazyColumn** (scrollable vertical) requiere restricciones de altura finitas. Si está dentro de un `Column` con `Modifier.fillMaxSize()` y otros elementos estáticos, Compose no puede calcular cuánto espacio debe asignarle al LazyColumn → **restricción infinita** → crash.

### Arquitectura problemática
```kotlin
❌ CÓDIGO INCORRECTO (pre-fix):

Column(
    modifier = Modifier
        .fillMaxSize()      // Column ocupa toda la pantalla
        .padding(16.dp)
) {
    StatisticsCard(reports)         // Elemento estático
    Row { /* Botones */ }            // Elemento estático
    Text("Apps con riesgo")          // Elemento estático
    
    LazyColumn(                      // ❌ Sin restricción de altura
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reports) { report ->
            StalkerwareReportCard(report)
        }
    }
}
```

**Problema:** El Column padre tiene altura fija (`fillMaxSize`), pero no especifica cuánto del espacio restante debe asignar al LazyColumn después de renderizar los elementos estáticos (StatisticsCard, botones, texto). Compose interpreta esto como "restricción infinita".

---

## ✅ Solución aplicada

### Opción 1: Usar `Modifier.weight(1f)` (solución implementada)
```kotlin
✅ CÓDIGO CORRECTO (post-fix):

Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
) {
    StatisticsCard(reports)         // Elemento estático
    Row { /* Botones */ }            // Elemento estático  
    Text("Apps con riesgo")          // Elemento estático
    
    LazyColumn(
        modifier = Modifier.weight(1f),  // ✅ Ocupa el espacio restante
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reports) { report ->
            StalkerwareReportCard(report)
        }
    }
}
```

**Efecto de `.weight(1f)`:** Le dice a Compose "este LazyColumn debe ocupar todo el espacio vertical restante después de renderizar los otros elementos". Restricción finita → ✅ sin crash.

### Opción 2: Usar `Modifier.fillMaxHeight()` (no recomendada)
```kotlin
// ALTERNATIVA (no ideal si hay elementos posteriores):
LazyColumn(
    modifier = Modifier.fillMaxHeight(),  // Ocupa toda la altura restante
    verticalArrangement = Arrangement.spacedBy(12.dp)
) { ... }
```
**Problema:** Si hay elementos DESPUÉS del LazyColumn (e.g., botones de acción), estos no serían visibles porque LazyColumn ocuparía todo el espacio.

---

## 📄 Archivos corregidos

### 1. `StalkerwareScreen.kt` (crash original)
**Línea:** 465  
**Función:** `StalkerwareResultsScreen()`  
**Cambio:**
```kotlin
LazyColumn(
-    verticalArrangement = Arrangement.spacedBy(12.dp)
+    modifier = Modifier.weight(1f),
+    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
```

### 2. `VaultScreens.kt` - CredentialsListScreen (prevención)
**Línea:** 564  
**Función:** Parte de `CredentialsListScreen()`  
**Cambio:** Mismo patrón, LazyColumn dentro de Column con fillMaxSize.

### 3. `VaultScreens.kt` - SecureDocumentsScreen #1 (prevención)
**Línea:** 1101  
**Función:** Parte de `SecureDocumentsScreen()`  
**Cambio:** Mismo patrón.

### 4. `VaultScreens.kt` - SecureDocumentsScreen #2 (prevención)
**Línea:** 1632  
**Función:** Otra variante de `SecureDocumentsScreen()`  
**Cambio:** Mismo patrón.

### 5. `ProScreens.kt` - ScanComparisonScreen (prevención)
**Línea:** 110  
**Función:** `ScanComparisonScreen()`  
**Contexto:**
```kotlin
Column(
    modifier = Modifier.fillMaxSize().padding(16.dp)
) {
    Text("Comparativa de Escaneos", ...)  // Cabecera estática
    Card { /* Resumen estadístico */ }     // Card estática
    LazyColumn(                            // ❌ Sin weight antes del fix
```
**Cambio:** Agregado `modifier = Modifier.weight(1f)`.

---

## 🧪 Testing requerido

### Test 1: Escaneo de stalkerware completo
```bash
# Objetivo: Verificar que los resultados se muestran sin crash

1. Abrir GuardianOS Pro
2. Navegar a "Detección de stalkerware"
3. Iniciar escaneo completo
4. Esperar a que complete (10-15 segundos, 40+ apps)
5. ✅ Verificar que se muestra la pantalla de resultados sin crash
6. ✅ Verificar que el LazyColumn de apps es scrollable
7. ✅ Verificar que los botones "Re-escanear" y "Exportar PDF" son visibles y funcionales
```

**Resultado esperado:**
```
02-14 13:03:48.293 StalkerwareDetector: ✅ Escaneo completo: 51 detecciones
02-14 13:03:49.806 RiskScorer: ✅ Escaneo finalizado en 11332ms
02-14 13:03:49.809 StalkerwareScreen: Escaneo completado: 52 apps con riesgo

[SIN CRASH - App sigue funcionando]
```

### Test 2: Family Vault (prevención)
```bash
# Objetivo: Verificar que las listas de credenciales y documentos se muestran correctamente

1. Abrir Family Vault
2. Agregar 5+ credenciales de diferentes categorías
3. ✅ Verificar que la lista es scrollable sin crash
4. Navegar a "Documentos seguros"
5. Agregar 3+ documentos de diferentes tipos
6. ✅ Verificar que la lista es scrollable sin crash
```

### Test 3: Comparación de escaneos (prevención)
```bash
# Objetivo: Verificar que la comparación de escaneos se muestra correctamente

1. Realizar 2 escaneos de seguridad en momentos diferentes
2. Navegar a "Historial de escaneos" (Pro)
3. Seleccionar 2 escaneos
4. Tocar "Comparar"
5. ✅ Verificar que la pantalla de comparación se muestra sin crash
6. ✅ Verificar que la lista de cambios (apps nuevas, eliminadas, modificadas) es scrollable
```

### Test 4: Dispositivos de RAM limitada
```bash
# Objetivo: Verificar que el fix no causa regresiones en dispositivos con poca RAM

- Probar en OPPO A80 (2GB RAM disponible)
- Cargar 50+ apps con riesgo en resultados de stalkerware
- ✅ Verificar que el scroll es fluido
- ✅ Verificar que no hay OOM (OutOfMemoryError)
```

---

## 📊 Patrones de diseño Compose

### Regla de oro: LazyColumn en Column
**Siempre que pongas un LazyColumn dentro de un Column con Modifier.fillMaxSize():**

1. **Agrega `Modifier.weight(1f)` al LazyColumn** si hay elementos estáticos antes/después
2. **O usa `Modifier.fillMaxHeight()`** si el LazyColumn es el último elemento
3. **O elimina `Modifier.fillMaxSize()` del Column padre** y usa `Modifier.wrapContentHeight()`

### Anti-patrones a evitar
```kotlin
// ❌ NUNCA HAGAS ESTO:
Column(Modifier.fillMaxSize()) {
    SomeStaticComposable()
    LazyColumn() {  // Sin modifier → crash
        items(list) { ... }
    }
}

// ❌ TAMPOCO ESTO:
Column(Modifier.verticalScroll(rememberScrollState())) {
    LazyColumn() {  // LazyColumn dentro de Column scrollable → crash
        items(list) { ... }
    }
}
```

### Patrones seguros
```kotlin
// ✅ OPCIÓN 1: weight si hay elementos después
Column(Modifier.fillMaxSize()) {
    HeaderComposable()
    LazyColumn(Modifier.weight(1f)) { ... }
    FooterButtons()  // Visible porque LazyColumn solo ocupa "el resto"
}

// ✅ OPCIÓN 2: fillMaxHeight si es el último elemento
Column(Modifier.fillMaxSize()) {
    HeaderComposable()
    LazyColumn(Modifier.fillMaxHeight()) { ... }
    // No más elementos después
}

// ✅ OPCIÓN 3: Sin fillMaxSize en el padre
Column(Modifier.wrapContentHeight()) {
    HeaderComposable()
    LazyColumn { ... }  // Puede crecer libremente
}

// ✅ OPCIÓN 4: Scaffold (recomendado para pantallas completas)
Scaffold(
    topBar = { HeaderComposable() }
) { paddingValues ->
    LazyColumn(Modifier.padding(paddingValues)) { ... }
}
```

---

## 🎯 Checklist de corrección

- [x] StalkerwareScreen.kt - LazyColumn con `.weight(1f)`
- [x] VaultScreens.kt - 3 LazyColumn corregidos
- [x] ProScreens.kt - ScanComparisonScreen corregido
- [x] Compilación exitosa sin errores
- [ ] Testing en OPPO A80 completado
- [ ] Build de release `assembleProRelease`
- [ ] Commit: "fix: Crash al mostrar resultados de stalkerware (Compose LazyColumn)"
- [ ] Tag: v2.0.1 (si pasa tests)

---

## 🚀 Próximos pasos

### Inmediato (hoy)
1. **Compilar y probar:** `./gradlew assembleProDebug`
2. **Instalar en OPPO A80:** `adb install -r app/build/outputs/apk/pro/debug/app-pro-debug.apk`
3. **Ejecutar tests 1-4** (escaneo stalkerware, vault, comparación, RAM limitada)

### Si tests pasan
```bash
# 1. Commit de corrección
git add .
git commit -m "fix: Crash Compose UI al mostrar resultados de stalkerware

- Agregado Modifier.weight(1f) a 5 LazyColumn
- Corregido IllegalStateException por restricciones infinitas
- Archivos: StalkerwareScreen.kt, VaultScreens.kt (x3), ProScreens.kt
- Previene crashes en pantallas de resultados y listas

Fixes #crash-compose-lazycolumn"

# 2. Tag de versión corregida
git tag -a v2.0.1 -m "v2.0.1 - Corrección crashes Compose UI

Cambios:
- Fix crash al mostrar resultados de stalkerware
- Fix prevención en Family Vault y comparación de escaneos
- 5 archivos corregidos, arquitectura Compose mejorada"

# 3. Push
git push origin main --tags

# 4. Build de release
./gradlew assembleProRelease

# 5. Generar changelog
echo "## v2.0.1 (14 feb 2026)
- 🔧 Fix crash al mostrar resultados de stalkerware
- 🔧 Prevención de crashes similares en Family Vault
- 🛡️ Arquitectura Compose optimizada para listas largas" >> CHANGELOG.md
```

---

## 📚 Documentación técnica

### Enlaces útiles
- [Compose Lazy layouts](https://developer.android.com/jetpack/compose/lists)
- [Modifier.weight() documentation](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/RowScope#(androidx.compose.ui.Modifier).weight(kotlin.Float,kotlin.Boolean))
- [CheckScrollableContainerConstraints.kt source](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/CheckScrollableContainerConstraints.kt)

### Lecciones aprendidas
1. **Compose es estricto con restricciones de layout** - A diferencia de XML donde se podía "salir con la suya", Compose crashea de inmediato si detecta layouts imposibles.
2. **Usar Modifier.weight() liberalmente** - Es la forma idiomática de distribuir espacio en Column/Row con múltiples elementos.
3. **Testing en pantallas PRO críticas** - El escaneo de stalkerware es una función clave, debe tener pruebas exhaustivas antes de release.
4. **Revisar TODAS las pantallas con LazyColumn** - Un crash en una pantalla indica alta probabilidad de crashes similares en pantallas con arquitectura similar.

---

**Autor:** Sistema de análisis automático GuardianOS  
**Revisión:** Manual  
**Estado:** Listo para testing en dispositivo físico 📱
