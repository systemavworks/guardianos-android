# 📱 GuardianOS v1.0.1 - Release Notes

## 🎯 Novedades en esta versión

### 🔒 Seguridad y Auditoría
- ✅ Detección de Stalkerware mejorada con base de datos actualizada
- ✅ Análisis de permisos peligrosos (ISO 27001 compliant)
- ✅ Detección de trackers con Exodus Privacy API
- ✅ Escaneo de malware con firmas SHA-256

### 🌐 Análisis de Red (PRO)
- ✅ Detección automática de redes WiFi cercanas
- ✅ Análisis de seguridad WPA2/WPA3
- ✅ Escaneo de dispositivos en red local (Ping optimizado para Android 10+)
- ✅ Detección de ubicación GPS desactivada con diálogo automático
- ✅ Identificación de fabricantes por MAC address

### 📂 Funciones PRO
- ✅ Family Vault con cifrado AES-GCM 256 bits
- ✅ Historial de escaneos con comparación
- ✅ Análisis de acceso a multimedia (MediaStore API)
- ✅ Guardian Shield: Monitor de permisos en tiempo real
- ✅ Informes PDF profesionales con análisis ISO 27001
- ✅ Modo pánico con borrado instantáneo
- ✅ Escaneo automático programado

### 🐛 Correcciones
- ✅ Optimización para dispositivos OPPO A80 (ColorOS)
- ✅ Corrección de crashes en Jetpack Compose
- ✅ Mejora en detección de ubicación GPS
- ✅ Timeout optimizado en escaneos de red (400ms)

---

## 📦 Versiones Disponibles

### 🆓 **GuardianOS Free** (`app-free-release.apk`)
**Características:**
- ✅ Auditoría completa de apps instaladas
- ✅ Detección de Stalkerware
- ✅ Análisis de permisos peligrosos
- ✅ Detección de trackers (Exodus API)
- ✅ Análisis de malware
- ✅ Informes PDF básicos
- ✅ **100% F-Droid compliant**
- ✅ Sin anuncios, sin rastreo

**Ideal para:**
- Usuarios que quieren auditoría básica de seguridad
- Distribución en F-Droid
- Uso sin funciones avanzadas

**Descarga:**
- Web: https://guardianos.es
- F-Droid: (próximamente)
- GitHub Releases

---

### 💎 **GuardianOS PRO** (`app-pro-release.apk`)
**Características:**
- ✅ **Todo lo incluido en Free +**
- ✅ **Family Vault**: Gestión segura de credenciales familiares
- ✅ **Análisis de Red**: WiFi + dispositivos locales
- ✅ **Análisis Multimedia**: Apps con acceso real a fotos/videos
- ✅ **Guardian Shield**: Monitor de permisos en tiempo real
- ✅ **Historial**: Comparación de escaneos anteriores
- ✅ **Informes Pro**: PDFs profesionales ISO 27001
- ✅ **Modo Pánico**: Borrado instantáneo de datos sensibles
- ✅ **Escaneo Automático**: Programación de auditorías

**Activación:**
Requiere código de activación formato: `GUAR-XXXX-XXXX-XXXX`

**Precio:**
- **Donación sugerida**: 5-10€
- **Licencia vitalicia**: Sin suscripciones
- **1 código = 1 dispositivo**

**Adquirir:**
- Web: https://guardianos.es/pro
- Contacto: contacto@guardianos.es

---

## 🔐 Códigos de Activación PRO

Los códigos de activación PRO se proporcionan tras realizar una donación.

**Formato:** `GUAR-1234-5678-9012`

**Cómo activar:**
1. Instala `app-pro-release.apk`
2. Abre GuardianOS
3. Ve a "Activar PRO"
4. Introduce tu código
5. ¡Disfruta de todas las funciones!

**Validación:**
- ✅ Offline (no requiere conexión)
- ✅ Sin servidores externos (60% privacidad)
- ✅ Permanente (no caduca)
- ✅ 1 código por dispositivo

---

## 📊 Información Técnica

### Requisitos
- **Android:** 9.0 (API 29) o superior
- **Recomendado:** Android 10+ para funciones de red
- **Espacio:** ~15 MB
- **Permisos:**
  - Ubicación (solo para escaneo WiFi)
  - Almacenamiento (análisis multimedia Pro)
  - Internet (API Exodus Privacy)

### Compatibilidad
- ✅ Android 9, 10, 11, 12, 13, 14, 15
- ✅ Probado en: Pixel, Samsung, OPPO, Xiaomi
- ✅ Optimizado para ColorOS (OPPO)

### Seguridad
- ✅ 100% Open Source (GPL v3)
- ✅ Sin telemetría ni rastreo
- ✅ Cifrado AES-GCM 256 bits (Vault)
- ✅ Sin permisos peligrosos innecesarios
- ✅ Auditable por F-Droid

---

## 🚀 Instalación

### Método 1: GitHub Releases (Recomendado)
```bash
# Descargar desde GitHub Releases
wget https://github.com/systemavworks/guardianos-android/releases/download/v1.0.1/app-free-release.apk

# Instalar
adb install app-free-release.apk
```

### Método 2: Web
1. Visita https://guardianos.es
2. Descarga la versión deseada
3. Habilita "Orígenes desconocidos" en Android
4. Instala el APK

### Método 3: F-Droid (Solo Free)
```bash
# Próximamente disponible en F-Droid
fdroid install com.guardianos.core.free
```

---

## 🛠️ Compilar desde Código Fuente

```bash
# Clonar repositorio
git clone https://github.com/systemavworks/guardianos-android.git
cd guardianos-android

# Compilar Free
gradle assembleFreeRelease

# Compilar Pro
gradle assembleProRelease

# APKs generados en:
# app/build/outputs/apk/free/release/app-free-release.apk
# app/build/outputs/apk/pro/release/app-pro-release.apk
```

---

## 📜 Licencia

**GNU General Public License v3.0**

GuardianOS es software libre. Puedes redistribuirlo y/o modificarlo bajo los términos de la GPL v3.

**Copyright © 2026 SystemAV Works**

---

## 🆘 Soporte

- **Web:** https://guardianos.es
- **Email:** contacto@guardianos.es
- **GitHub:** https://github.com/systemavworks/guardianos-android
- **Issues:** https://github.com/systemavworks/guardianos-android/issues

---

## 🙏 Agradecimientos

- **Exodus Privacy** por la API de trackers
- **Comunidad F-Droid** por los estándares de privacidad
- **Usuarios beta** por el testing exhaustivo
- **Contribuidores** del proyecto

---

## 📅 Roadmap

### v1.1.0 (Próxima)
- [ ] Análisis de red avanzado (puertos sospechosos)
- [ ] Integración con Have I Been Pwned
- [ ] Widget de monitorización
- [ ] Modo oscuro mejorado

### v1.2.0
- [ ] Soporte para tablets
- [ ] Backup cifrado en nube (opcional)
- [ ] Multi-idioma (EN, ES, FR, DE)

---

**¡Gracias por confiar en GuardianOS!** 🛡️
