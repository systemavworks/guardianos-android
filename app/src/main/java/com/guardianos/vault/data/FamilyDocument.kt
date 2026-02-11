package com.guardianos.vault.data

import java.time.Instant
import java.util.UUID

data class FamilyDocument(
    val id: UUID = UUID.randomUUID(),
    val name: String, // Ej: "DNI Juan", "Pasaporte Ana"
    val type: DocumentType,
    val encryptedFilePath: String, // Ruta interna cifrada
    val ownerRole: FamilyRole = FamilyRole.PARENT,
    val createdAt: Instant = Instant.now()
)

enum class DocumentType {
    DNI, PASSPORT, HEALTH_CARD, OTHER
}

enum class FamilyRole {
    PARENT, CHILD, OTHER
}
