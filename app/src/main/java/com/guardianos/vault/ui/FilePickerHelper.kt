package com.guardianos.vault.ui

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Helper para seleccionar archivos del dispositivo.
 * Compatible con Android Storage Access Framework.
 */
object FilePickerHelper {
    
    /**
     * Contrato para seleccionar un documento.
     */
    fun getDocumentContract() = ActivityResultContracts.GetContent()
    
    /**
     * Lee el contenido de un archivo seleccionado.
     */
    fun readFileBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Obtiene el nombre del archivo desde su URI.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "archivo"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex >= 0) {
                    name = cursor.getString(displayNameIndex)
                }
            }
        }
        return name
    }
}
