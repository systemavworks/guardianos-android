# GuardianOS
[🇬🇧 Read this README in English](README.en.md)
> **Auditoría ética local para la protección digital de menores**

[![License: GPL v3](https://img.shields.io/badge/Licencia-GPLv3-blue.svg)](LICENSE)

## 🛡️ Misión

**GuardianOS** es una aplicación Android de código abierto diseñada para realizar **auditorías éticas locales** en dispositivos móviles utilizados por menores.  
La app **nunca se conecta a internet**, no contiene rastreadores (*trackers*), ni dependencias propietarias, ni servicios de Google. Todo el análisis se realiza **de forma totalmente offline**, garantizando la máxima privacidad y promoviendo entornos digitales seguros y autónomos.

## ✨ Características principales

- 🔍 **Análisis local de aplicaciones instaladas**: revisa permisos, llamadas a APIs sensibles (ubicación, micrófono, contactos, etc.) y comportamientos potencialmente invasivos.
- 📱 **Compatible con ROMs libres**: funciona perfectamente en entornos *deGoogled* como **LineageOS**, **/e/ OS**, y otras distribuciones Android sin servicios de Google.
- 👨‍👩‍👧‍👦 **Enfoque pedagógico y familiar**: genera informes comprensibles para padres, madres y educadores, ayudándoles a identificar riesgos reales en el uso digital de menores.
- 🧾 **Informes técnicos exportables**: permite guardar resultados del análisis sin necesidad de conexión a internet.
- 🌐 **Cero comunicación externa**: la app no envía, recibe ni almacena datos en servidores remotos. Todo ocurre en el dispositivo.
- 📜 **Software 100 % libre**: publicado bajo la licencia **GNU General Public License v3.0 (GPLv3)**.

## 📦 Instalación

- Disponible próximamente en [**F-Droid**](https://f-droid.org/).
- También puedes compilarla tú mismo desde el código fuente (ver sección *Desarrollo*).

## 🧑‍💻 Desarrollo

### Requisitos
- JDK 17 o superior
- Gradle (este proyecto sigue la política de F-Droid: **no incluye Gradle Wrapper**)

### Compilación
Para generar una versión de depuración (debug):

```bash
./gradlew assembleDebug
```

El APK resultante se guardará en:  
`app/build/outputs/apk/debug/app-debug.apk`

> ⚠️ Nota: Este proyecto **no incluye** `gradlew` ni la carpeta `gradle/` para cumplir con las políticas de F-Droid. Asegúrate de tener Gradle instalado en tu sistema.

### Metadatos para tiendas
Los metadatos (descripción, capturas, changelogs, etc.) se gestionan mediante la estructura estándar de Fastlane, ubicada en:

```
fastlane/metadata/android/
```

Esta carpeta contiene versiones localizadas (por ejemplo, `es-ES/`, `en-US/`) y es utilizada por F-Droid para mostrar información de la app en la tienda.

## 📜 Licencia

Este proyecto es software libre y está licenciado bajo la **GNU General Public License v3.0**.  
Puedes redistribuirlo y/o modificarlo bajo los términos de esta licencia.

Ver el archivo [`LICENSE`](LICENSE) para más detalles.

## 🌐 Más información

- 🌍 Sitio web oficial: [https://guardianos.es](https://guardianos.es)
- 📧 Contacto: [info@guardianos.es](mailto:info@guardianos.es)
- 📍 Proyecto desarrollado en **Sevilla, Andalucía (España)**

## 💡 Nota para revisores de F-Droid

Este proyecto cumple estrictamente con las políticas de F-Droid:
- No contiene binarios precompilados.
- No incluye Gradle Wrapper.
- No tiene dependencias no libres.
- No realiza ninguna conexión de red.
- Incluye script de limpieza (`prepare-for-fdroid.sh`) para eliminar artefactos innecesarios antes del build.

Gracias por revisar GuardianOS. ¡Juntos construimos un ecosistema digital más ético y seguro para la infancia!
