# GuardianOS - ProGuard Rules
# Optimización y ofuscación para Release builds

# ============================================
# REGLAS GENERALES
# ============================================

# Mantener números de línea para stack traces
-keepattributes SourceFile,LineNumberTable

# Mantener anotaciones
-keepattributes *Annotation*

# Mantener clases nativas
-keepclasseswithmembernames class * {
    native <methods>;
}

# Mantener parámetros genéricos
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================
# KOTLIN
# ============================================

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================
# JETPACK COMPOSE
# ============================================

-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ============================================
# RETROFIT & GSON
# ============================================

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson - CRÍTICO para TypeToken
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-dontwarn sun.misc.**

# Clases Gson core
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# TypeToken - CRÍTICO: Preservar clases anónimas
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    *;
}

# Mantener modelos de datos - COMPLETO
-keep class com.guardianos.core.domain.model.** { *; }
-keep class com.guardianos.core.audit.model.** { *; }
-keep class com.guardianos.vault.data.** { *; }

# Clases Pro con TypeToken - CRÍTICO para historial
-keep class com.guardianos.core.pro.ScanHistory$ScanEntry { *; }
-keep class com.guardianos.core.pro.FamilyVault$* { *; }
-keep class com.guardianos.core.pro.DocumentVault$* { *; }
-keepclassmembers class com.guardianos.core.pro.** {
    <init>(...);
    <fields>;
}

# Preservar todas las data classes con sus constructores
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    public <init>(...);
}

# Enums usados en serialización
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# ============================================
# iTEXT 7 PDF - CRÍTICO
# ============================================

# Mantener todas las clases de iText
-keep class com.itextpdf.** { *; }
-keep interface com.itextpdf.** { *; }
-keepclassmembers class com.itextpdf.** { *; }

# Mantener enums de iText
-keepclassmembers enum com.itextpdf.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# SLF4J - LOGGING (requerido por iText)
# ============================================

# No advertir sobre SLF4J faltante (logging opcional)
-dontwarn org.slf4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.commons.logging.**

# Mantener interfaces SLF4J pero permitir implementaciones faltantes
-keep interface org.slf4j.** { *; }
-keep class org.slf4j.** { *; }

# Evitar error de StaticLoggerBinder faltante
-dontnote org.slf4j.impl.StaticLoggerBinder

# ============================================
# GUARDIANOS - CLASES CRÍTICAS
# ============================================

# ProActivationManager (sistema de licencias)
-keep class com.guardianos.core.pro.ProActivationManager { *; }

# CipherManager (cifrado del vault)
-keep class com.guardianos.vault.security.CipherManager { *; }
-keep class com.guardianos.vault.security.VaultSecurityManager { *; }

# Bases de datos
-keep class com.guardianos.core.data.** { *; }

# Network Scanner
-keep class com.guardianos.core.network.NetworkScanner$** { *; }
-keep class com.guardianos.core.network.NetworkGuardian$** { *; }

# ============================================
# ANDROIDX
# ============================================

# WorkManager
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }

# Biometric
-keep class androidx.biometric.** { *; }

# ============================================
# WARNINGS A IGNORAR
# ============================================

-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Jackson (usado opcionalmente por iText, no requerido en Android)
-dontwarn com.fasterxml.jackson.**
-keep class com.fasterxml.jackson.** { *; }

# AWT/ImageIO (no disponibles en Android, iText tiene alternativas)
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn java.beans.**

# ============================================
# OPTIMIZACIÓN
# ============================================

# Habilitar optimización agresiva
-optimizationpasses 5
-dontpreverify
-repackageclasses ''
-allowaccessmodification

# Eliminar logs en producción (opcional, comentar para debugging)
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# ============================================
# SERIALIZACIÓN
# ============================================

# Mantener clases Serializable
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================
# REFLECTION
# ============================================

-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# ============================================
# FIN
# ============================================
