package com.guardianos.core.pro

import android.content.Context
import android.util.Log
import com.guardianos.vault.data.FamilyDocument
import com.guardianos.vault.security.CipherManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

/**
 * Bóveda de documentos cifrada con AES-256-GCM + PBKDF2.
 * - Doble capa: metadata cifrado + archivos cifrados
 * - Sin almacenamiento en nube (100% local)
 * - Cada documento se cifra individualmente
 * - Límite de tamaño por documento: 50MB
 */
object DocumentVault {
    private const val DOCS_FILE = "family_docs.enc"
    private const val TAG = "DocumentVault"
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50 MB
    private val gson = Gson()
    private val cipher = CipherManager()

    /**
     * Guarda documento con cifrado real AES-256-GCM.
     * El archivo se cifra con contraseña derivada por PBKDF2 (100k iteraciones).
     */
    fun saveDocument(context: Context, doc: FamilyDocument, fileBytes: ByteArray, password: String = ""): Result<Unit> {
        return try {
            // Validar tamaño del archivo
            if (fileBytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("El archivo está vacío"))
            }
            
            if (fileBytes.size > MAX_FILE_SIZE) {
                val sizeMB = fileBytes.size / (1024 * 1024)
                return Result.failure(IllegalArgumentException(
                    "Archivo demasiado grande (${sizeMB}MB). Límite: 50MB"
                ))
            }
            
            // Validar espacio disponible
            val availableSpace = context.filesDir.usableSpace
            if (availableSpace < fileBytes.size * 2) { // Factor de seguridad 2x
                return Result.failure(Exception("Espacio insuficiente en el dispositivo"))
            }
            
            // Cifrar archivo con contraseña (si se proporciona) o clave por defecto
            val encryptedBytes = if (password.isNotEmpty()) {
                cipher.encryptWithPassword(fileBytes, password)
            } else {
                // Usar clave derivada del ID del documento como fallback
                cipher.encryptWithPassword(fileBytes, "guardianos_${doc.id}")
            }
            
            // Guardar archivo cifrado
            val fileName = "doc_${doc.id}.enc"
            val docFile = File(context.filesDir, fileName)
            docFile.writeBytes(encryptedBytes)
            
            Log.d(TAG, "Document encrypted and saved: $fileName (${encryptedBytes.size} bytes)")
            
            // Actualizar metadatos
            val docs = loadDocuments(context).toMutableList()
            docs.removeAll { it.id == doc.id } // Evitar duplicados
            docs.add(doc.copy(encryptedFilePath = fileName))
            
            // Guardar metadatos cifrados
            val metadataJson = gson.toJson(docs)
            val encryptedMetadata = cipher.encrypt(metadataJson)
            File(context.filesDir, DOCS_FILE).writeText(encryptedMetadata)
            
            Log.d(TAG, "Metadata updated: ${docs.size} documents")
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Validation error: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving document", e)
            Result.failure(Exception("Error al guardar documento: ${e.message}", e))
        }
    }

    /**
     * Carga lista de documentos (solo metadatos, no archivos completos).
     */
    fun loadDocuments(context: Context): List<FamilyDocument> {
        return try {
            val file = File(context.filesDir, DOCS_FILE)
            if (!file.exists()) {
                Log.d(TAG, "No documents file found")
                return emptyList()
            }
            
            val encryptedMetadata = file.readText()
            val metadataJson = cipher.decrypt(encryptedMetadata)
            val type = object : TypeToken<List<FamilyDocument>>() {}.type
            val docs = gson.fromJson<List<FamilyDocument>>(metadataJson, type) ?: emptyList()
            
            Log.d(TAG, "Loaded ${docs.size} documents")
            docs
        } catch (e: Exception) {
            Log.e(TAG, "Error loading documents", e)
            emptyList()
        }
    }

    /**
     * Carga el archivo de un documento específico (descifrado).
     */
    fun loadDocumentFile(context: Context, doc: FamilyDocument, password: String = ""): ByteArray? {
        return try {
            val file = File(context.filesDir, doc.encryptedFilePath)
            if (!file.exists()) {
                Log.w(TAG, "Document file not found: ${doc.encryptedFilePath}")
                return null
            }
            
            val encryptedBytes = file.readBytes()
            
            // Descifrar con contraseña o clave por defecto
            val decryptedBytes = if (password.isNotEmpty()) {
                cipher.decryptWithPassword(encryptedBytes, password)
            } else {
                cipher.decryptWithPassword(encryptedBytes, "guardianos_${doc.id}")
            }
            
            Log.d(TAG, "Document decrypted: ${doc.name} (${decryptedBytes.size} bytes)")
            decryptedBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error loading document file", e)
            null
        }
    }

    /**
     * Elimina documento y su archivo cifrado.
     */
    fun deleteDocument(context: Context, doc: FamilyDocument) {
        try {
            // Eliminar archivo
            val file = File(context.filesDir, doc.encryptedFilePath)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Document file deleted: ${doc.encryptedFilePath}")
            }
            
            // Actualizar metadatos
            val docs = loadDocuments(context).filter { it.id != doc.id }
            val metadataJson = gson.toJson(docs)
            val encryptedMetadata = cipher.encrypt(metadataJson)
            File(context.filesDir, DOCS_FILE).writeText(encryptedMetadata)
            
            Log.d(TAG, "Document removed from metadata: ${doc.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting document", e)
            throw Exception("Error al eliminar documento: ${e.message}")
        }
    }
    
    /**
     * Borra toda la bóveda (modo pánico).
     */
    fun clearVault(context: Context) {
        try {
            // Eliminar todos los archivos cifrados
            val docs = loadDocuments(context)
            docs.forEach { doc ->
                File(context.filesDir, doc.encryptedFilePath).delete()
            }
            
            // Eliminar metadatos
            File(context.filesDir, DOCS_FILE).delete()
            
            Log.d(TAG, "Vault cleared: ${docs.size} documents deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing vault", e)
        }
    }
}
