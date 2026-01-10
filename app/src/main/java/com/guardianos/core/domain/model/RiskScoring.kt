package com.guardianos.core.domain.model

object RiskScoring {

    fun calculate(
        permissions: List<AppPermission>,
        installSource: InstallSource,
        hasInternet: Boolean,
        isSystemApp: Boolean = false,
        apkPath: String? = null,
        packageName: String? = null
    ): Pair<Int, Risk> {

        // 🔒 Excluir overlays, recursos del sistema y componentes APEX
        if (isOverlayPackage(apkPath, packageName) || isFrameworkComponent(apkPath, packageName)) {
            return 0 to Risk.LOW
        }

        var score = 0

        // 1️⃣ Permisos peligrosos
        permissions.filter { it.dangerous }.forEach {
            score += when {
                it.name.contains("LOCATION") -> 30
                it.name.contains("CONTACT") -> 30
                it.name.contains("AUDIO") -> 25
                it.name.contains("CAMERA") -> 25
                it.name.contains("PHONE") -> 20
                else -> 10
            }
        }

        // 2️⃣ Acceso a red
        if (hasInternet) score += 20

        // 3️⃣ Fuente de instalación (solo si NO es del sistema legítimo)
        if (!isSystemApp && !isSystemPath(apkPath)) {
            score += when (installSource) {
                InstallSource.UNKNOWN -> 30
                InstallSource.SIDELOAD -> 20
                InstallSource.ADB -> 15
                else -> 0
            }
        }

        // 4️⃣ Clasificación final
        val risk = when {
            score >= 70 -> Risk.HIGH
            score >= 40 -> Risk.MEDIUM
            else -> Risk.LOW
        }

        return score to risk
    }

    // Detecta rutas del sistema (APEX, system, vendor, product)
    private fun isSystemPath(path: String?): Boolean {
        return path?.run {
            startsWith("/system/") ||
            startsWith("/product/") ||
            startsWith("/apex/") ||
            startsWith("/vendor/")
        } ?: false
    }

    // Detecta overlays por ruta o nombre de paquete
    private fun isOverlayPackage(path: String?, packageName: String?): Boolean {
        return path?.contains("overlay") == true ||
               packageName?.endsWith(".overlay") == true ||
               packageName?.contains(".overlay.") == true ||
               packageName?.startsWith("android.") == true ||
               packageName?.startsWith("com.android.") == true ||
               packageName?.startsWith("com.google.android.overlay") == true
    }

    // Detecta componentes del framework o recursos sin comportamiento activo
    private fun isFrameworkComponent(path: String?, packageName: String?): Boolean {
        return packageName?.let { pkg ->
            pkg.contains("framework") ||
            pkg.contains("resources") ||
            pkg.contains("permissioncontroller") ||
            pkg.contains("connectivity") ||
            pkg.contains("media.module") ||
            pkg.contains("wifiresources") ||
            pkg.contains("cellbroadcast") ||
            pkg.contains("healthfitness") ||
            pkg.contains("documentsui") ||
            pkg.contains("ext.services")
        } ?: false
    }
}
