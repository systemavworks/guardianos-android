package com.guardianos.vault.utils

import java.security.SecureRandom

/**
 * Generador de contraseñas seguras.
 */
object PasswordGenerator {
    
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    
    /**
     * Genera una contraseña segura.
     */
    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSpecial: Boolean = true
    ): String {
        var chars = LOWERCASE
        if (includeUppercase) chars += UPPERCASE
        if (includeDigits) chars += DIGITS
        if (includeSpecial) chars += SPECIAL
        
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
    
    /**
     * Evalúa la fortaleza de una contraseña.
     */
    fun evaluateStrength(password: String): PasswordStrength {
        if (password.length < 6) return PasswordStrength.VERY_WEAK
        
        var score = 0
        
        // Longitud
        score += when {
            password.length >= 16 -> 3
            password.length >= 12 -> 2
            password.length >= 8 -> 1
            else -> 0
        }
        
        // Mayúsculas
        if (password.any { it.isUpperCase() }) score++
        
        // Minúsculas
        if (password.any { it.isLowerCase() }) score++
        
        // Dígitos
        if (password.any { it.isDigit() }) score++
        
        // Caracteres especiales
        if (password.any { !it.isLetterOrDigit() }) score++
        
        return when {
            score >= 7 -> PasswordStrength.VERY_STRONG
            score >= 5 -> PasswordStrength.STRONG
            score >= 3 -> PasswordStrength.MEDIUM
            score >= 2 -> PasswordStrength.WEAK
            else -> PasswordStrength.VERY_WEAK
        }
    }
    
    enum class PasswordStrength {
        VERY_WEAK,
        WEAK,
        MEDIUM,
        STRONG,
        VERY_STRONG
    }
}
