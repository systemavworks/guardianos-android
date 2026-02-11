package com.guardianos.vault.data

import java.util.UUID

/**
 * Modelo de documento para Document Vault PRO.
 */
data class SecureDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val fileName: String,
    val fileType: String,                   // pdf, jpg, png, doc, etc.
    val category: DocumentCategory,
    val encryptedData: String,              // Datos del archivo cifrados
    val fileSizeBytes: Long,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Categorías de documentos sensibles.
 */
enum class DocumentCategory(val displayName: String) {
    IDENTIFICATION("Identificación"),     // DNI, pasaporte, licencia
    MEDICAL("Médicos"),                   // Informes, recetas, seguros salud
    FINANCIAL("Financieros"),             // Contratos, certificados bancarios
    INSURANCE("Seguros"),                 // Pólizas de seguros
    LEGAL("Legales"),                     // Contratos, poderes, testamentos
    EDUCATION("Educación"),               // Títulos, certificados
    REAL_ESTATE("Inmobiliario"),          // Escrituras, contratos alquiler
    TAX("Fiscal"),                        // Declaraciones, facturas
    OTHER("Otros")
}
