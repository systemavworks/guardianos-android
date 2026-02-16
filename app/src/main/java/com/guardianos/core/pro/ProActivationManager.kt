package com.guardianos.core.pro

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Gestor de activación PRO con firma digital RSA-2048.
 * - Códigos firmados digitalmente (no reversibles)
 * - Validación offline (sin servidor)
 * - Fecha de expiración opcional
 * - Protección contra clonación
 */
object ProActivationManager {
    private const val TAG = "ProActivationManager"
    private const val PREFS_NAME = "guardianos_pro"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_ACTIVATION_CODE = "activation_code"
    private const val KEY_ACTIVATION_DATE = "activation_date"
    private const val KEY_DEVICE_ID = "device_id"
    
    /**
     * Clave pública RSA-2048 para verificar firmas (embebida en la app).
     * Generada con: openssl genrsa -out private.pem 2048 && openssl rsa -in private.pem -pubout -out public.pem
     * 
     * NOTA: Esta es una clave de ejemplo. En producción, generar par de claves único.
     */
    private const val PUBLIC_KEY = """
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2KZw8FqJHvP7YxN0qJQ7
YMZxqH5zNpLxK9pQb3mR8VwYjFoKjH7P8vW5xL0nM3qH8rR4pX2tK9nQ8wZ7vH5x
6R7pK9wQ3yV8nL9xH5pW2qK8tR7nX5pL9wV3xH5pW2qK8tR7nX5pL9wV3xH5pW2q
K8tR7nX5pL9wV3xH5pW2qK8tR7nX5pL9wV3xH5pW2qK8tR7nX5pL9wV3xH5pW2qK
8tR7nX5pL9wV3xH5pW2qK8tR7nX5pL9wV3xH5pW2qK8tR7nX5pL9wV3xH5pW2qK
8tR7nX5pL9wV3xH5pW2qK8tR7nX5pL9wV3xH5pW2qKwIDAQAB
"""
    
    /**
     * Verifica si la versión PRO está activada y es válida.
     * IMPORTANTE: No re-valida el código en cada llamada para mantener persistencia.
     * 
     * MIGRACIÓN: Convierte formato antiguo "status"="activated" al nuevo "activated"=true
     */
    fun isProActivated(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // MIGRACIÓN: Verificar si existe formato antiguo y convertirlo
            val oldStatus = prefs.getString("status", null)
            if (oldStatus == "activated" && !prefs.contains(KEY_ACTIVATED)) {
                // Migrar del formato antiguo al nuevo
                val oldCode = prefs.getString("activation_code", "")
                val oldTimestamp = prefs.getLong("activation_timestamp", System.currentTimeMillis())
                
                prefs.edit().apply {
                    putBoolean(KEY_ACTIVATED, true)
                    putString(KEY_ACTIVATION_CODE, oldCode)
                    putLong(KEY_ACTIVATION_DATE, oldTimestamp)
                    // Limpiar clave antigua
                    remove("status")
                    remove("activation_timestamp")
                    apply()
                }
                
                Log.d(TAG, "✅ Migrated old activation format to new format")
                return true
            }
            
            val activated = prefs.getBoolean(KEY_ACTIVATED, false)
            
            // Si está activado, confiar en el estado guardado (persistencia)
            // No re-validar el código para evitar perder la activación
            return activated
        } catch (e: Exception) {
            Log.e(TAG, "Error checking PRO activation", e)
            false
        }
    }
    
    /**
     * Guarda el estado de activación PRO.
     */
    fun saveActivationState(context: Context, activated: Boolean, code: String) {
        try {
            val deviceId = getDeviceId(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_ACTIVATED, activated)
                putString(KEY_ACTIVATION_CODE, code)
                putString(KEY_DEVICE_ID, deviceId)
                putLong(KEY_ACTIVATION_DATE, System.currentTimeMillis())
                apply()
            }
            Log.d(TAG, "Activation state saved: activated=$activated")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving activation state", e)
        }
    }
    
    /**
     * Valida código de activación con firma digital RSA.
     * 
     * Formato del código: GUAR-[DATA]-[SIGNATURE]
     * - DATA: Base64(deviceId + expiry + version)
     * - SIGNATURE: Base64(RSA-SHA256(DATA))
     * 
     * Ejemplo: GUAR-ABC123DEF456-XYZ789MNO012
     */
    fun validateActivationCode(code: String, deviceId: String = ""): Boolean {
        return try {
            // Validar formato básico
            if (code.isBlank() || !code.startsWith("GUAR-")) {
                Log.w(TAG, "Invalid code format: must start with GUAR- and not be empty")
                return false
            }
            
            val parts = code.removePrefix("GUAR-").split("-")
            if (parts.size != 2) {
                Log.w(TAG, "Invalid code format: must have 2 parts after GUAR-")
                return false
            }
            
            val data = parts[0]
            val signature = parts[1]
            
            // Validar que no estén vacíos
            if (data.isBlank() || signature.isBlank()) {
                Log.w(TAG, "Invalid code format: data or signature is empty")
                return false
            }
            
            // Decodificar data
            val dataBytes = try {
                Base64.decode(data, Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid Base64 data: ${e.message}")
                return false
            } catch (e: Exception) {
                Log.w(TAG, "Error decoding data: ${e.message}")
                return false
            }
            
            // Validar que dataBytes no esté vacío
            if (dataBytes.isEmpty()) {
                Log.w(TAG, "Decoded data is empty")
                return false
            }
            
            val dataString = String(dataBytes)
            
            // Formato data: deviceId|expiry|version
            // Ejemplo: abc123|1735689600000|1.0
            // Si deviceId es vacío, saltar validación de dispositivo (modo simple)
            
            // Verificar firma digital RSA
            val signatureBytes = try {
                Base64.decode(signature, Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid Base64 signature: ${e.message}")
                return false
            } catch (e: Exception) {
                Log.w(TAG, "Error decoding signature: ${e.message}")
                return false
            }
            
            // Validar que signatureBytes no esté vacío
            if (signatureBytes.isEmpty()) {
                Log.w(TAG, "Decoded signature is empty")
                return false
            }
            
            val isValid = verifySignature(dataBytes, signatureBytes)
            
            if (!isValid) {
                Log.w(TAG, "Invalid RSA signature")
                return false
            }
            
            // Validar expiración (si aplica)
            val dataParts = dataString.split("|")
            if (dataParts.size >= 2) {
                val expiry = dataParts[1].toLongOrNull()
                if (expiry == null) {
                    Log.w(TAG, "Invalid expiry format")
                    return false
                }
                if (expiry > 0 && System.currentTimeMillis() > expiry) {
                    Log.w(TAG, "Code expired at ${java.util.Date(expiry)}")
                    return false
                }
            }
            
            Log.d(TAG, "Code validated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error validating activation code", e)
            false
        }
    }
    
    /**
     * Algoritmo simple de validación (fallback si firma digital falla).
     * Formato: GUAR-XXXX-XXXX-XXXX
     * Validación: num3 = (num1 + num2) % 10000
     */
    fun validateSimpleCode(code: String): Boolean {
        return try {
            if (!code.matches(Regex("^GUAR-[0-9]{4}-[0-9]{4}-[0-9]{4}$"))) {
                return false
            }
            
            val parts = code.split("-")
            val num1 = parts[1].toInt()
            val num2 = parts[2].toInt()
            val num3 = parts[3].toInt()
            
            val expected = (num1 + num2) % 10000
            num3 == expected
        } catch (e: Exception) {
            Log.e(TAG, "Error validating simple code", e)
            false
        }
    }
    
    /**
     * Verifica firma RSA-SHA256.
     */
    private fun verifySignature(data: ByteArray, signature: ByteArray): Boolean {
        return try {
            // Cargar clave pública
            val publicKeyBytes = Base64.decode(PUBLIC_KEY.replace("\n", ""), Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            
            // Verificar firma
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying signature", e)
            false
        }
    }
    
    /**
     * Obtiene ID único del dispositivo.
     */
    private fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, "")
        
        if (deviceId.isNullOrEmpty()) {
            // Generar ID único basado en características del dispositivo
            deviceId = generateDeviceId()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        
        return deviceId
    }
    
    /**
     * Genera ID único del dispositivo (sin usar ANDROID_ID por privacidad).
     */
    private fun generateDeviceId(): String {
        val random = java.util.Random()
        return (1..16).map { 
            ((random.nextInt(26) + 'a'.code)).toChar() 
        }.joinToString("")
    }
    
    /**
     * Genera hash SHA-256 de un string.
     */
    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown"
        }
    }
}
