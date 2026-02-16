package com.guardianos.vault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de cifrado AES-256-GCM con soporte para:
 * - Android KeyStore (hardware-backed)
 * - Derivación de claves con PBKDF2 (password-based)
 */
class CipherManager {
    
    companion object {
        private const val KEYSTORE_ALIAS = "family_vault_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ITERATIONS = 100000
        private const val KEY_SIZE = 256
        private const val SALT_SIZE = 32
        private const val IV_SIZE = 12
        private const val TAG_LENGTH = 128
    }
    
    /**
     * Cifrado con Android KeyStore (hardware-backed, más seguro).
     * Usado para vault principal.
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getOrCreateKeystoreKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return iv.toHexString() + ":" + ciphertext.toHexString()
    }

    fun decrypt(encrypted: String): String {
        val parts = encrypted.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted format")
        
        val iv = parts[0].hexToBytes()
        val ciphertext = parts[1].hexToBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getOrCreateKeystoreKey()
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }
    
    /**
     * Cifrado basado en contraseña (PBKDF2 + AES-256-GCM).
     * Usado para documentos individuales con contraseña específica.
     */
    fun encryptWithPassword(plaintext: ByteArray, password: String): ByteArray {
        // Generar salt aleatorio
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        
        // Derivar clave desde contraseña
        val key = deriveKeyFromPassword(password, salt)
        
        // Cifrar con AES-GCM
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        
        // Formato: salt (32) + iv (12) + ciphertext
        return salt + iv + ciphertext
    }
    
    fun decryptWithPassword(encrypted: ByteArray, password: String): ByteArray {
        if (encrypted.size < SALT_SIZE + IV_SIZE) {
            throw IllegalArgumentException("Invalid encrypted data")
        }
        
        // Extraer componentes
        val salt = encrypted.sliceArray(0 until SALT_SIZE)
        val iv = encrypted.sliceArray(SALT_SIZE until SALT_SIZE + IV_SIZE)
        val ciphertext = encrypted.sliceArray(SALT_SIZE + IV_SIZE until encrypted.size)
        
        // Derivar clave desde contraseña
        val key = deriveKeyFromPassword(password, salt)
        
        // Descifrar
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Deriva clave de 256 bits desde contraseña usando PBKDF2-HMAC-SHA256.
     */
    private fun deriveKeyFromPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Obtiene o crea clave en Android KeyStore (hardware-backed).
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        
        return if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        } else {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .setUserAuthenticationRequired(false) // Cambiar a true para requerir biométrico
                    .build()
            )
            generator.generateKey()
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = ByteArray(length / 2) { index -> 
        substring(index * 2, index * 2 + 2).toInt(16).toByte() 
    }
}
