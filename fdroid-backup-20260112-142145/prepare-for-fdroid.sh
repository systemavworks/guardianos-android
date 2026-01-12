#!/bin/bash

# prepare-for-fdroid.sh
# Limpieza segura del repositorio GuardianOS para cumplir con los requisitos de F-Droid
# Autor: Victor Shift Lara / GuardianOS
# Fecha: 12 de enero de 2026

set -e  # Detener si hay error

REPO_DIR="$(pwd)"
BACKUP_DIR="$REPO_DIR/fdroid-backup-$(date +%Y%m%d-%H%M%S)"

echo "🛡️  Preparando repositorio para F-Droid..."
echo "📁 Directorio actual: $REPO_DIR"

# 1. Crear copia de seguridad (solo código fuente relevante)
echo "💾 Creando copia de seguridad en: $BACKUP_DIR"
mkdir -p "$BACKUP_DIR"
rsync -av --exclude='.git' \
          --exclude='.gradle' \
          --exclude='app/build' \
          --exclude='build' \
          --exclude='*.apk' \
          --exclude='gradlew' \
          --exclude='gradlew.bat' \
          --exclude='gradle/wrapper/gradle-wrapper.jar' \
          ./ "$BACKUP_DIR/"

# 2. Eliminar archivos problemáticos del control de versiones
echo "🧹 Eliminando artefactos de Gradle y compilación..."
git rm -rf --ignore-unmatch .gradle/
git rm -rf --ignore-unmatch app/build/
git rm -rf --ignore-unmatch build/
git rm -f --ignore-unmatch gradlew gradlew.bat
git rm -f --ignore-unmatch gradle/wrapper/gradle-wrapper.jar

# 3. Asegurar que el .gitignore es correcto
cat > .gitignore << 'EOF'
# Android / Gradle
.gradle/
build/
app/build/
*.apk
*.ap_
*.jks
*.keystore

# IDE
.idea/
*.iml
.DS_Store

# Fastlane (opcional, pero no necesario ignorar)
# fastlane/

# Otros
/local.properties
/captures/
EOF

echo "✅ .gitignore actualizado"

# 4. Añadir cambios
git add .gitignore
git add -A

# 5. Confirmar
git commit -m "fix: remove gradle wrapper and build artifacts for F-Droid compliance"

echo ""
echo "✅ ¡Listo!"
echo "🔹 Tu repositorio está limpio y listo para F-Droid."
echo "🔹 Copia de seguridad en: $BACKUP_DIR"
echo ""
echo "📌 Siguiente paso: haz push y comenta en GitLab:"
echo "   @fdroid-bot please rebuild"
