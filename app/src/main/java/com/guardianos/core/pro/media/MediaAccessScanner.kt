package com.guardianos.core.pro.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Escaneo avanzado de apps con acceso a fotos/documentos sensibles (PRO).
 * Detecta permisos OTORGADOS (no solo solicitados).
 */
object MediaAccessScanner {
    private const val TAG = "MediaAccessScanner"
    
    data class MediaAccessInfo(
        val packageName: String,
        val appName: String,
        val grantedPermissions: List<String>,
        val riskLevel: String  // "ALTO", "MEDIO", "BAJO"
    )
    
    /**
     * Obtiene lista de apps con acceso REAL a multimedia/documentos
     */
    fun getAppsWithMediaAccess(context: Context): List<String> {
        val detailedApps = getDetailedMediaAccessInfo(context)
        return detailedApps.map { "${it.appName} (${it.riskLevel})" }
    }
    
    /**
     * Obtiene información detallada de apps con permisos multimedia otorgados
     */
    fun getDetailedMediaAccessInfo(context: Context): List<MediaAccessInfo> {
        val pm = context.packageManager
        val result = mutableListOf<MediaAccessInfo>()
        
        try {
            val apps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            
            for (pkg in apps) {
                val grantedMediaPermissions = mutableListOf<String>()
                
                // Verificar permisos OTORGADOS
                pkg.requestedPermissions?.forEachIndexed { index, permission ->
                    val isGranted = (pkg.requestedPermissionsFlags[index] and 
                                    PackageManager.PERMISSION_GRANTED) != 0
                    
                    if (isGranted && isMediaOrStoragePermission(permission)) {
                        grantedMediaPermissions.add(permission)
                    }
                }
                
                // Solo incluir apps con permisos realmente otorgados
                if (grantedMediaPermissions.isNotEmpty()) {
                    val appName = try {
                        pm.getApplicationLabel(pkg.applicationInfo).toString()
                    } catch (e: Exception) {
                        pkg.packageName
                    }
                    
                    val riskLevel = calculateRiskLevel(grantedMediaPermissions)
                    
                    result.add(MediaAccessInfo(
                        packageName = pkg.packageName,
                        appName = appName,
                        grantedPermissions = grantedMediaPermissions,
                        riskLevel = riskLevel
                    ))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media access", e)
        }
        
        return result.sortedByDescending { app ->
            when (app.riskLevel) {
                "ALTO" -> 3
                "MEDIO" -> 2
                else -> 1
            }
        }
    }
    
    /**
     * Verifica si un permiso es relacionado con multimedia/almacenamiento
     */
    private fun isMediaOrStoragePermission(permission: String): Boolean {
        return permission.contains("READ_EXTERNAL_STORAGE") ||
               permission.contains("WRITE_EXTERNAL_STORAGE") ||
               permission.contains("READ_MEDIA_IMAGES") ||
               permission.contains("READ_MEDIA_VIDEO") ||
               permission.contains("READ_MEDIA_AUDIO") ||
               permission.contains("MANAGE_EXTERNAL_STORAGE") ||
               permission.contains("ACCESS_MEDIA_LOCATION")
    }
    
    /**
     * Calcula nivel de riesgo basado en permisos otorgados
     */
    private fun calculateRiskLevel(permissions: List<String>): String {
        val dangerousPerms = permissions.count { 
            it.contains("WRITE") || it.contains("MANAGE")
        }
        
        return when {
            dangerousPerms >= 2 -> "ALTO"       // Puede escribir/modificar archivos
            permissions.size >= 3 -> "MEDIO"     // Acceso amplio a múltiples tipos
            else -> "BAJO"                       // Acceso limitado
        }
    }
}
