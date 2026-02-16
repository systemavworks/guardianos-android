// ~/Desarrollo/family-vault/app/src/main/java/com/guardianos/vault/data/FamilyCredential.kt
package com.guardianos.vault.data

import java.util.UUID

/**
 * Modelo de credencial para Family Vault PRO.
 */
data class FamilyCredential(
    val id: String = UUID.randomUUID().toString(),
    val title: String,                      // Nombre del servicio
    val username: String,                   // Usuario/email
    val password: String,                   // Contraseña (se cifra)
    val category: String = "Otras",         // Categoría
    val notes: String = "",                 // Notas adicionales
    val url: String = "",                   // URL del servicio
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)
