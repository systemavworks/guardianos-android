# 🚀 Jetpack Compose - Guía Avanzada para GuardianOS

Esta guía cubre patrones avanzados de Jetpack Compose aplicados específicamente al desarrollo de **GuardianOS**, una aplicación de auditoría de seguridad para Android orientada a la protección digital de menores.

> ✅ **Objetivo**: Maximizar rendimiento, claridad visual y robustez del código UI mediante patrones comprobados de Compose.

---

## 1️⃣ `LaunchedEffect` – Ejecución controlada al aparecer o cambiar estado

Ejecuta lógica asíncrona (APIs, auditorías, timers) cuando un composable entra en composición o cuando cambian sus claves.

### Carga automática de apps al iniciar
```kotlin
@Composable
fun GuardianHomeScreen(
    viewModel: GuardianViewModel = viewModel()
) {
    val context = LocalContext.current.applicationContext

    LaunchedEffect(Unit) {
        viewModel.auditApps(context, AuditMode.QUICK)
    }
}
```

### Recarga al cambiar parámetro (ej. modo de auditoría)
```kotlin
LaunchedEffect(viewModel.auditMode) {
    if (viewModel.apps.value.isEmpty()) {
        viewModel.auditApps(context, viewModel.auditMode)
    }
}
```

> ⚠️ **Importante**: Usa `applicationContext` para evitar fugas si la Activity se destruye durante la auditoría.

---

## 2️⃣ `derivedStateOf` – Estado calculado eficiente

Evita recálculos innecesarios en listas grandes o estadísticas complejas.

### Filtrado y agregación segura
```kotlin
val filteredApps by remember {
    derivedStateOf {
        apps.filter { app ->
            app.risk >= Risk.HIGH ||
            app.findings.any { f -> f.title.contains(query, ignoreCase = true) }
        }
    }
}

val criticalCount by remember {
    derivedStateOf {
        apps.count { it.risk == Risk.CRITICAL }
    }
}
```

✅ **Ventaja**: Solo se recalcula si `apps` o `query` cambian. Ideal para listas de +100 apps.

---

## 3️⃣ Efectos secundarios (`SideEffect`, `DisposableEffect`)

### `DisposableEffect`: Observar cambios en apps instaladas
```kotlin
@Composable
fun AppInstallationMonitor(viewModel: GuardianViewModel) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action in listOf(
                        Intent.ACTION_PACKAGE_ADDED,
                        Intent.ACTION_PACKAGE_REMOVED
                    )) {
                    viewModel.triggerReaudit()
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}
```

### `SideEffect`: Logging de eventos de seguridad
```kotlin
@Composable
fun SecurityLogger(apps: List<AppAudit>) {
    SideEffect {
        logSecurityEvent(
            event = "audit_completed",
            params = mapOf(
                "total_apps" to apps.size,
                "critical_risks" to apps.count { it.risk == Risk.CRITICAL }
            )
        )
    }
}
```

---

## 4️⃣ Navegación con `navigation-compose`

### Estructura recomendada
```kotlin
// Routes.kt
object GuardianRoutes {
    const val HOME = "home"
    const val DETAIL = "detail/{packageName}"
    
    fun detail(packageName: String) = "detail/$packageName"
}
```

### NavHost principal
```kotlin
@Composable
fun GuardianNavGraph(
    navController: NavHostController,
    viewModel: GuardianViewModel
) {
    NavHost(navController, startDestination = GuardianRoutes.HOME) {
        composable(GuardianRoutes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onAppClick = { pkg ->
                    navController.navigate(GuardianRoutes.detail(pkg))
                }
            )
        }
        composable(
            route = GuardianRoutes.DETAIL,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            )
        ) { entry ->
            val pkg = entry.arguments?.getString("packageName") ?: return@composable
            val app = viewModel.getAppByPackage(pkg)
            app?.let {
                DetailScreen(app) { navController.popBackStack() }
            }
        }
    }
}
```

> 🔒 **Buena práctica**: Nunca pases objetos grandes por la ruta. Usa ID (packageName) y resuelve desde ViewModel.

---

## 5️⃣ Animaciones con propósito de seguridad

### Badge de riesgo animado
```kotlin
@Composable
fun RiskBadge(risk: Risk) {
    val color by animateColorAsState(
        targetValue = when (risk) {
            Risk.CRITICAL -> Color(0xFFEF4444) // Rojo intenso
            Risk.HIGH      -> Color(0xFFF97316) // Naranja
            Risk.MEDIUM    -> Color(0xFFEAB308) // Amarillo
            Risk.LOW       -> Color(0xFF22C55E) // Verde
        },
        animationSpec = tween(300)
    )

    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = risk.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
```

### Alerta crítica con `AnimatedVisibility`
```kotlin
AnimatedVisibility(
    visible = hasCriticalRisk,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically()
) {
    Surface(
        color = Color(0xFF450A0A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("🚨 Riesgo crítico detectado", modifier = Modifier.padding(16.dp))
    }
}
```

---

## 6️⃣ Optimizaciones clave

### ✅ Claves en `LazyColumn`
```kotlin
LazyColumn {
    items(
        items = filteredApps,
        key = { app -> app.packageName } // Evita recomposiciones erróneas
    ) { app ->
        AppAuditCard(app)
    }
}
```

### ✅ Memoización de componentes
```kotlin
@Composable
fun AppAuditCard(app: AppAudit) = remember(app.id) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(app.appName)
            RiskBadge(app.risk)
        }
    }
}
```

### ✅ Separación ViewModel/UI
```kotlin
// En ViewModel
class GuardianViewModel : ViewModel() {
    private val _apps = mutableStateOf<List<AppAudit>>(emptyList())
    val apps: State<List<AppAudit>> = _apps

    fun auditApps(context: Context, mode: AuditMode) { /* ... */ }
}

// En UI
@Composable
fun HomeScreen(viewModel: GuardianViewModel) {
    val apps by viewModel.apps
    LazyColumn { items(apps) { AppAuditCard(it) } }
}
```

---

## 📦 Dependencias recomendadas (build.gradle)

```gradle
dependencies {
    implementation platform('androidx.compose:compose-bom:2024.12.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.navigation:navigation-compose:2.8.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.8.4'
    implementation 'androidx.activity:activity-compose:1.9.1'
}
```

> ✅ Usa **Compose BOM** para evitar conflictos de versiones.

---

## 🛡️ Principios de diseño GuardianOS

- **Claridad sobre minimalismo**: Los colores y animaciones comunican riesgo real.
- **Rendimiento en dispositivos limitados**: `LazyColumn`, `derivedStateOf`, y memoización son obligatorios.
- **Privacidad por defecto**: Nada de analytics externos; `SideEffect` solo para logs locales cifrados.
- **Resiliencia**: Manejo seguro de contexto, limpieza de listeners, y auditoría automática post-instalación.

---

> 📌 Esta guía evoluciona con el proyecto. Última actualización: **enero 2026**.
