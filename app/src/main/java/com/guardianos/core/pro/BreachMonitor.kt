package com.guardianos.core.pro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Monitor de brechas de seguridad usando HaveIBeenPwned API.
 */
object BreachMonitor {
    
    private const val API_BASE = "https://api.pwnedpasswords.com/range/"
    
    /**
     * Verifica si una contraseña ha sido filtrada en brechas de seguridad.
     * Usa k-Anonymity para no enviar la contraseña completa.
     */
    suspend fun isPasswordPwned(password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val hash = sha1(password).uppercase()
            val prefix = hash.substring(0, 5)
            val suffix = hash.substring(5)
            
            val url = URL("$API_BASE$prefix")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "GuardianOS-Android")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val found = response.lines().any { line ->
                    line.startsWith(suffix, ignoreCase = true)
                }
                Result.success(found)
            } else {
                Result.failure(Exception("API error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Verifica si un email ha sido comprometido en brechas.
     * NOTA: Esta API requiere clave, por ahora solo retorna info simulada.
     */
    suspend fun isEmailBreached(email: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // Por ahora retornamos resultado simulado
            // Para implementación real, necesitas registrarte en haveibeenpwned.com
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
