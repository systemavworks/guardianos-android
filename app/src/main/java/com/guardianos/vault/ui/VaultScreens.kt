package com.guardianos.vault.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianos.core.pro.*
import com.guardianos.vault.data.FamilyCredential
import com.guardianos.vault.data.FamilyDocument
import com.guardianos.vault.data.SecureDocument
import com.guardianos.vault.data.DocumentCategory
import com.guardianos.vault.security.BiometricAuthManager
import com.guardianos.vault.security.VaultSecurityManager
import com.guardianos.vault.utils.PasswordGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)

/**
 * Pantalla de configuración de contraseña maestra (primera vez).
 */
@Composable
fun MasterPasswordSetupScreen(
    context: Context,
    onPasswordSet: () -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF3B82F6)
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            "Crear Contraseña Maestra",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "Protege tu vault con una contraseña segura",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = "" },
            label = { Text("Contraseña maestra") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "🙈" else "👁️", fontSize = 20.sp)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; error = "" },
            label = { Text("Confirmar contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Indicador de fortaleza
        if (password.isNotEmpty()) {
            val strength = PasswordGenerator.evaluateStrength(password)
            val (strengthText, strengthColor) = when (strength) {
                PasswordGenerator.PasswordStrength.VERY_STRONG -> "Muy fuerte 💪" to Color(0xFF22C55E)
                PasswordGenerator.PasswordStrength.STRONG -> "Fuerte 👍" to Color(0xFF3B82F6)
                PasswordGenerator.PasswordStrength.MEDIUM -> "Regular ⚠️" to Color(0xFFFFA726)
                PasswordGenerator.PasswordStrength.WEAK -> "Débil 😟" to Color(0xFFFF6B6B)
                PasswordGenerator.PasswordStrength.VERY_WEAK -> "Muy débil ❌" to Color(0xFFDC2626)
            }
            
            Text(
                "Fortaleza: $strengthText",
                fontSize = 13.sp,
                color = strengthColor,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        if (error.isNotEmpty()) {
            Text(
                error,
                color = Color.Red,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = {
                when {
                    password.length < 6 -> error = "La contraseña debe tener al menos 6 caracteres"
                    password != confirmPassword -> error = "Las contraseñas no coinciden"
                    else -> {
                        val result = VaultSecurityManager.setMasterPassword(context, password)
                        if (result) {
                            onPasswordSet()
                        } else {
                            error = "Error al configurar contraseña"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crear Contraseña Maestra")
        }
        
        TextButton(onClick = onCancel) {
            Text("Cancelar")
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "⚠️ IMPORTANTE: No podrás recuperar esta contraseña si la olvidas",
            fontSize = 12.sp,
            color = Color(0xFFFF6B6B),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Pantalla de desbloqueo del vault.
 */
@Composable
fun VaultUnlockScreen(
    context: Context,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val remaining = VaultSecurityManager.getRemainingAttempts(context)
    
    // Verificar si está bloqueado
    if (VaultSecurityManager.isVaultLocked(context)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Red
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                "Vault Bloqueado",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )
            
            Text(
                "Has superado el límite de intentos fallidos",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(Modifier.height(32.dp))
            
            Button(onClick = onCancel) {
                Text("Volver")
            }
        }
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF3B82F6)
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            "Desbloquear Vault",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "Introduce tu contraseña maestra",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = "" },
            label = { Text("Contraseña maestra") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        if (error.isNotEmpty()) {
            Text(
                error,
                color = Color.Red,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        Text(
            "Intentos restantes: $remaining",
            fontSize = 13.sp,
            color = if (remaining <= 2) Color.Red else Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (VaultSecurityManager.verifyMasterPassword(context, password)) {
                    onUnlocked()
                } else {
                    val newRemaining = VaultSecurityManager.getRemainingAttempts(context)
                    error = "Contraseña incorrecta. Intentos restantes: $newRemaining"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Desbloquear")
        }
        
        // Biometría si está disponible
        if (BiometricAuthManager.isBiometricAvailable(context)) {
            Spacer(Modifier.height(16.dp))
            val activity = context as? androidx.fragment.app.FragmentActivity
            OutlinedButton(
                onClick = {
                    activity?.let {
                        BiometricAuthManager.authenticate(
                            it,
                            title = "Autenticación biométrica",
                            subtitle = "Verifica tu identidad para continuar",
                            onSuccess = {
                                onUnlocked()
                            },
                            onError = { errorMsg ->
                                // Mostrar error en la UI
                                // Aquí podrías usar un Snackbar, Toast, o actualizar el estado 'error'
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("👤")
                Spacer(Modifier.width(8.dp))
                Text("Usar huella digital")
            }
        }
        
        TextButton(onClick = onCancel) {
            Text("Cancelar")
        }
    }
}

/**
 * Pantalla principal del Family Vault (lista de credenciales).
 */
@Composable
fun FamilyVaultMainScreen(
    context: Context,
    onBack: () -> Unit
) {
    var credentials by remember { mutableStateOf<List<FamilyCredential>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Todas") }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    val categories = listOf(
        "Todas", "Redes Sociales", "Email", "Banca", "Trabajo", 
        "Streaming", "Compras", "Juegos", "Otras"
    )
    
    // Cargar credenciales
    LaunchedEffect(Unit) {
        FamilyVault.loadCredentials(context).onSuccess {
            credentials = it
        }.onFailure {
            error = it.message ?: "Error al cargar credenciales"
        }
    }
    
    // Filtrar credenciales
    val filteredCredentials = remember(credentials, selectedCategory, searchQuery) {
        var filtered = credentials
        
        if (selectedCategory != "Todas") {
            filtered = filtered.filter { it.category == selectedCategory }
        }
        
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true)
            }
        }
        
        filtered.sortedByDescending { it.lastModified }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Cabecera con botón Volver
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    "Family Vault",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir")
            }
        }
        
        // Búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Buscar...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Filtros por categoría (scroll horizontal)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category, fontSize = 12.sp) }
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (error.isNotEmpty()) {
            Text(error, color = Color.Red, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        
        // Lista de credenciales
        if (filteredCredentials.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (credentials.isEmpty()) "No hay credenciales" else "No se encontraron resultados",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCredentials) { credential ->
                    CredentialCard(
                        context = context,
                        credential = credential,
                        onDelete = {
                            scope.launch {
                                FamilyVault.deleteCredential(context, credential.id)
                                FamilyVault.loadCredentials(context).onSuccess {
                                    credentials = it
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Diálogo para añadir credencial
    if (showAddDialog) {
        AddCredentialDialog(
            context = context,
            onDismiss = { showAddDialog = false },
            onSaved = {
                scope.launch {
                    FamilyVault.loadCredentials(context).onSuccess {
                        credentials = it
                    }
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CredentialCard(
    context: Context,
    credential: FamilyCredential,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        credential.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        credential.username,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
                
                Text(
                    credential.category,
                    fontSize = 11.sp,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier
                        .background(
                            Color(0xFF3B82F6).copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                
                // Contraseña
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (passwordVisible) credential.password else "••••••••",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "🙈" else "👁️", fontSize = 20.sp)
                        }
                        
                        IconButton(onClick = {
                            FamilyVault.copyPasswordToClipboard(context, credential.password)
                        }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Copiar",
                                tint = Color.Gray
                            )
                        }
                    }
                }
                
                if (credential.url.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("URL: ${credential.url}", fontSize = 12.sp, color = Color.Gray)
                }
                
                if (credential.notes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Notas: ${credential.notes}", fontSize = 12.sp, color = Color.Gray)
                }
                
                Spacer(Modifier.height(8.dp))
                Text(
                    "Última modificación: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(credential.lastModified))}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                
                Spacer(Modifier.height(12.dp))
                
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Eliminar credencial")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCredentialDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Otras") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showPasswordGenerator by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val categories = listOf(
        "Redes Sociales", "Email", "Banca", "Trabajo",
        "Streaming", "Compras", "Juegos", "Otras"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Credencial") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = "" },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario/Email *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña *") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "🙈" else "👁️", fontSize = 20.sp)
                            }
                            IconButton(onClick = { showPasswordGenerator = true }) {
                                Text("✨")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Evaluación de fortaleza
                if (password.isNotEmpty()) {
                    val strength = PasswordGenerator.evaluateStrength(password)
                    val (text, color) = when (strength) {
                        PasswordGenerator.PasswordStrength.VERY_STRONG -> "Muy fuerte" to Color(0xFF22C55E)
                        PasswordGenerator.PasswordStrength.STRONG -> "Fuerte" to Color(0xFF3B82F6)
                        PasswordGenerator.PasswordStrength.MEDIUM -> "Regular" to Color(0xFFFFA726)
                        PasswordGenerator.PasswordStrength.WEAK -> "Débil" to Color(0xFFFF6B6B)
                        PasswordGenerator.PasswordStrength.VERY_WEAK -> "Muy débil" to Color(0xFFDC2626)
                    }
                    Text(text, fontSize = 11.sp, color = color, modifier = Modifier.padding(top = 4.dp))
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Selector de categoría
                var expandedCat by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedCat,
                    onExpandedChange = { expandedCat = !expandedCat }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        label = { Text("Categoría") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCat) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedCat,
                        onDismissRequest = { expandedCat = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expandedCat = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        title.isBlank() -> error = "El título es obligatorio"
                        username.isBlank() -> error = "El usuario es obligatorio"
                        password.isBlank() -> error = "La contraseña es obligatoria"
                        else -> {
                            scope.launch {
                                val credential = FamilyCredential(
                                    title = title,
                                    username = username,
                                    password = password,
                                    category = category,
                                    url = url,
                                    notes = notes
                                )
                                FamilyVault.saveCredential(context, credential).onSuccess {
                                    onSaved()
                                }.onFailure {
                                    error = it.message ?: "Error al guardar"
                                }
                            }
                        }
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
    
    // Diálogo generador de contraseñas
    if (showPasswordGenerator) {
        PasswordGeneratorDialog(
            onGenerated = { 
                password = it
                showPasswordGenerator = false
            },
            onDismiss = { showPasswordGenerator = false }
        )
    }
}

@Composable
fun PasswordGeneratorDialog(
    onGenerated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var length by remember { mutableStateOf(16f) }
    var includeUppercase by remember { mutableStateOf(true) }
    var includeDigits by remember { mutableStateOf(true) }
    var includeSpecial by remember { mutableStateOf(true) }
    var generatedPassword by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generador de Contraseñas") },
        text = {
            Column {
                Text("Longitud: ${length.toInt()}", fontSize = 13.sp)
                Slider(
                    value = length,
                    onValueChange = { length = it },
                    valueRange = 8f..32f,
                    steps = 23
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeUppercase, onCheckedChange = { includeUppercase = it })
                    Text("Mayúsculas (A-Z)", fontSize = 13.sp)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeDigits, onCheckedChange = { includeDigits = it })
                    Text("Números (0-9)", fontSize = 13.sp)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeSpecial, onCheckedChange = { includeSpecial = it })
                    Text("Símbolos (!@#$%)", fontSize = 13.sp)
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        generatedPassword = PasswordGenerator.generate(
                            length = length.toInt(),
                            includeUppercase = includeUppercase,
                            includeDigits = includeDigits,
                            includeSpecial = includeSpecial
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generar")
                }
                
                if (generatedPassword.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332))
                    ) {
                        Text(
                            generatedPassword,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp
                        )
                    }
                    
                    val strength = PasswordGenerator.evaluateStrength(generatedPassword)
                    val (text, color) = when (strength) {
                        PasswordGenerator.PasswordStrength.VERY_STRONG -> "Muy fuerte 💪" to Color(0xFF22C55E)
                        PasswordGenerator.PasswordStrength.STRONG -> "Fuerte 👍" to Color(0xFF3B82F6)
                        PasswordGenerator.PasswordStrength.MEDIUM -> "Regular ⚠️" to Color(0xFFFFA726)
                        PasswordGenerator.PasswordStrength.WEAK -> "Débil 😟" to Color(0xFFFF6B6B)
                        PasswordGenerator.PasswordStrength.VERY_WEAK -> "Muy débil ❌" to Color(0xFFDC2626)
                    }
                    Text("Fortaleza: $text", fontSize = 12.sp, color = color, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerated(generatedPassword) },
                enabled = generatedPassword.isNotEmpty()
            ) {
                Text("Usar esta contraseña")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Pantalla de Document Vault (lista de documentos).
 */
@Composable
fun DocumentVaultMainScreen(
    context: Context,
    onBack: () -> Unit
) {
    var documents by remember { mutableStateOf<List<FamilyDocument>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<com.guardianos.vault.data.DocumentType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    // Cargar documentos
    LaunchedEffect(Unit) {
        try {
            documents = DocumentVault.loadDocuments(context)
        } catch (e: Exception) {
            error = e.message ?: "Error al cargar documentos"
        }
    }
    
    // Filtrar documentos
    val filteredDocuments = remember(documents, selectedType, searchQuery) {
        var filtered = documents
        
        selectedType?.let { type ->
            filtered = filtered.filter { it.type == type }
        }
        
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
        
        filtered.sortedByDescending { it.createdAt }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Document Vault",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir")
            }
        }
        
        // Búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Buscar...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Filtros por tipo
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedType == null,
                onClick = { selectedType = null },
                label = { Text("Todos", fontSize = 12.sp) }
            )
            
            com.guardianos.vault.data.DocumentType.values().forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.name, fontSize = 12.sp) }
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (error.isNotEmpty()) {
            Text(error, color = Color.Red, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        
        // Lista de documentos
        if (filteredDocuments.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (documents.isEmpty()) "No hay documentos" else "No se encontraron resultados",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredDocuments) { document ->
                    DocumentCard(
                        context = context,
                        document = document,
                        onDelete = {
                            scope.launch {
                                DocumentVault.deleteDocument(context, document)
                                documents = DocumentVault.loadDocuments(context)
                            }
                        }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("← Volver")
        }
    }
    
    // Diálogo para añadir documento
    if (showAddDialog) {
        AddDocumentDialog(
            context = context,
            onDismiss = { showAddDialog = false },
            onSaved = {
                scope.launch {
                    documents = DocumentVault.loadDocuments(context)
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun DocumentCard(
    context: Context,
    document: FamilyDocument,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        document.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        document.encryptedFilePath,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
                
                Text(
                    document.type.name,
                    fontSize = 11.sp,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier
                        .background(
                            Color(0xFF3B82F6).copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                "Cifrado · ${document.type.name}",
                fontSize = 11.sp,
                color = Color.Gray
            )
            
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                
                // FamilyDocument no tiene notes ni tags, solo mostrar info básica
                Text("Tipo de documento:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(document.type.name, fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                
                Text("Creado:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(document.createdAt.toString(), fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                
                Text("Propietario:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(document.ownerRole.name, fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var showPreview by remember { mutableStateOf(false) }
                    TextButton(onClick = { showPreview = true }) {
                        Text("Ver documento")
                    }
                    if (showPreview) {
                        AlertDialog(
                            onDismissRequest = { showPreview = false },
                            title = { Text("Vista previa de documento") },
                            text = {
                                Column {
                                    Text("Nombre: ${document.name}")
                                    Text("Tipo: ${document.type.name}")
                                    Text("Creado: ${document.createdAt}")
                                    Text("Propietario: ${document.ownerRole.name}")
                                    Text("\nPara ver el contenido real, descifra el archivo desde la bóveda.", color = Color.Gray, fontSize = 12.sp)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showPreview = false }) { Text("Cerrar") }
                            }
                        )
                    }
                    
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Eliminar")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var docType by remember { mutableStateOf(com.guardianos.vault.data.DocumentType.OTHER) }
    var ownerRole by remember { mutableStateOf(com.guardianos.vault.data.FamilyRole.PARENT) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Documento") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = "" },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Selector de categoría
                var expandedCat by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedCat,
                    onExpandedChange = { expandedCat = !expandedCat }
                ) {
                    OutlinedTextField(
                        value = docType.name,
                        onValueChange = {},
                        label = { Text("Tipo de documento") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCat) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedCat,
                        onDismissRequest = { expandedCat = false }
                    ) {
                        com.guardianos.vault.data.DocumentType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    docType = type
                                    expandedCat = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Selector de propietario
                var expandedRole by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedRole,
                    onExpandedChange = { expandedRole = !expandedRole }
                ) {
                    OutlinedTextField(
                        value = ownerRole.name,
                        onValueChange = {},
                        label = { Text("Propietario") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedRole) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedRole,
                        onDismissRequest = { expandedRole = false }
                    ) {
                        com.guardianos.vault.data.FamilyRole.values().forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.name) },
                                onClick = {
                                    ownerRole = role
                                    expandedRole = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📎")
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri == null) "Seleccionar archivo" else "Archivo seleccionado")
                }
                
                selectedUri?.let { uri ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        uri.lastPathSegment ?: "Archivo seleccionado",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        title.isBlank() -> error = "El nombre es obligatorio"
                        selectedUri == null -> error = "Debes seleccionar un archivo"
                        else -> {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val inputStream = context.contentResolver.openInputStream(selectedUri!!)
                                    val bytes = inputStream?.readBytes()
                                    inputStream?.close()
                                    
                                    if (bytes != null) {
                                        val newDoc = com.guardianos.vault.data.FamilyDocument(
                                            name = title,
                                            type = docType,
                                            encryptedFilePath = "", // DocumentVault lo asignará
                                            ownerRole = ownerRole
                                        )
                                        
                                        val result = DocumentVault.saveDocument(context, newDoc, bytes, password = "")
                                        
                                        withContext(Dispatchers.Main) {
                                            result.onSuccess {
                                                onSaved()
                                                onDismiss()
                                            }.onFailure { e ->
                                                error = "Error: ${e.message}"
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            error = "No se pudo leer el archivo"
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        error = "Error inesperado: ${e.message}"
                                    }
                                }
                            }
                        }
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
