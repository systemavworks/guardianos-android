package com.guardianos.vault.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Gestor de seguridad del vault con contraseña maestra y protecciones avanzadas.
 */
object VaultSecurityManager {
    private const val PREFS_NAME = "vault_security"
    private const val KEY_MASTER_HASH = "master_password_hash"
    private const val KEY_SALT = "password_salt"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LAST_ACCESS = "last_access_time"
    private const val MAX_FAILED_ATTEMPTS = 5
    private const val AUTO_LOGOUT_MS = 120000L // 2 minutos
    
    /**
     * Verifica si el vault tiene una contraseña maestra configurada.
     */
    fun hasMasterPassword(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_MASTER_HASH)
    }
    
    /**
     * Establece la contraseña maestra (primera vez).
     */
    fun setMasterPassword(context: Context, password: String): Boolean {
        if (password.length < 6) return false
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        
        prefs.edit()
            .putString(KEY_MASTER_HASH, hash)
            .putString(KEY_SALT, salt)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
        
        return true
    }
    
    /**
     * Verifica la contraseña maestra.
     */
    fun verifyMasterPassword(context: Context, password: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedHash = prefs.getString(KEY_MASTER_HASH, null) ?: return false
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        
        // Verificar si el vault está bloqueado
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            return false
        }
        
        val hash = hashPassword(password, salt)
        val isValid = hash == storedHash
        
        if (isValid) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LAST_ACCESS, System.currentTimeMillis())
                .apply()
        } else {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, failedAttempts + 1)
                .apply()
        }
        
        return isValid
    }
    
    /**
     * Obtiene intentos fallidos restantes.
     */
    fun getRemainingAttempts(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        return MAX_FAILED_ATTEMPTS - failed
    }
    
    /**
     * Verifica si el vault está bloqueado.
     */
    fun isVaultLocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0) >= MAX_FAILED_ATTEMPTS
    }
    
    /**
     * Desbloquea el vault (solo administrador).
     */
    fun unlockVault(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
    }
    
    /**
     * Destruye el vault por completo (modo pánico).
     */
    fun destroyVault(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // Borrar archivos del vault
        context.getSharedPreferences("guardianos_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.contains("vault") || file.name.contains("credential") || file.name.contains("document")) {
                file.delete()
            }
        }
    }
    
    /**
     * Verifica si debe hacer auto-logout por inactividad.
     */
    fun shouldAutoLogout(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAccess = prefs.getLong(KEY_LAST_ACCESS, 0)
        return (System.currentTimeMillis() - lastAccess) > AUTO_LOGOUT_MS
    }
    
    /**
     * Actualiza el timestamp de último acceso.
     */
    fun updateLastAccess(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ACCESS, System.currentTimeMillis()).apply()
    }
    
    /**
     * Genera un salt aleatorio.
     */
    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(32)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Hash de contraseña con salt usando PBKDF2-HMAC-SHA256 (más seguro que SHA-256 simple).
     * Usa 100,000 iteraciones según recomendaciones OWASP 2024.
     */
    private fun hashPassword(password: String, salt: String): String {
        val saltBytes = salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = javax.crypto.spec.PBEKeySpec(
            password.toCharArray(),
            saltBytes,
            100000, // Iteraciones OWASP
            256 // Longitud de clave
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }
}
