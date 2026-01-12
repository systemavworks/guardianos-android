# GuardianOS

> **Local ethical auditing for the digital protection of minors**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

## 🛡️ Mission

**GuardianOS** is an open-source Android application designed to perform **local ethical audits** on mobile devices used by minors.  
The app **never connects to the internet**, contains no trackers, proprietary dependencies, or Google services. All analysis happens **entirely offline**, ensuring maximum privacy and promoting safe, autonomous digital environments.

## ✨ Key Features

- 🔍 **Local inspection of installed apps**: reviews permissions, sensitive API calls (location, microphone, contacts, etc.), and potentially invasive behaviors.
- 📱 **Compatible with free Android ROMs**: works seamlessly on *deGoogled* environments such as **LineageOS**, **/e/ OS**, and other Google-free Android distributions.
- 👨‍👩‍👧‍👦 **Educational & family-focused**: generates clear reports for parents, guardians, and educators to help identify real digital risks.
- 🧾 **Exportable technical reports**: allows saving audit results without any internet connection.
- 🌐 **Zero external communication**: the app does not send, receive, or store data on remote servers. Everything stays on-device.
- 📜 **100% Free Software**: released under the **GNU General Public License v3.0 (GPLv3)**.

## 📦 Installation

- Coming soon on [**F-Droid**](https://f-droid.org/).
- You can also build it from source (see *Development* section below).

## 🧑‍💻 Development

### Requirements
- JDK 17 or higher
- Gradle (this project follows F-Droid policy: **no Gradle Wrapper included**)

### Building
To generate a debug APK:

```bash
./gradlew assembleDebug
```

The resulting APK will be located at:  
`app/build/outputs/apk/debug/app-debug.apk`

> ⚠️ Note: This project **does not include** `gradlew` or the `gradle/` folder to comply with F-Droid policies. Ensure Gradle is installed on your system.

### Store Metadata
App store metadata (description, screenshots, changelogs, etc.) follows the Fastlane standard and is located in:

```
fastlane/metadata/android/
```

This directory includes localized versions (e.g., `es-ES/`, `en-US/`) and is used by F-Droid to display app information in the catalog.

## 📜 License

This project is free software licensed under the **GNU General Public License v3.0**.  
You are free to redistribute and/or modify it under the terms of this license.

See the [`LICENSE`](LICENSE) file for details.

## 🌐 More Information

- 🌍 Official website: [https://guardianos.es](https://guardianos.es)
- 📧 Contact: [info@guardianos.es](mailto:info@guardianos.es)
- 📍 Developed in **Seville, Andalusia (Spain)**

## 💡 Note for F-Droid Reviewers

This project strictly complies with F-Droid inclusion policies:
- No prebuilt binaries.
- No Gradle Wrapper included.
- No non-free dependencies.
- No network communication whatsoever.
- Includes a cleanup script (`prepare-for-fdroid.sh`) to remove unnecessary artifacts before build.

Thank you for reviewing GuardianOS. Together, we’re building a more ethical and secure digital future for children.


