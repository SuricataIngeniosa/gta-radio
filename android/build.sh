#!/usr/bin/env bash
# Compila el APK debug con el toolchain local (JDK 17 en ~/.local/opt). ./build.sh install -> también adb install.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="$HOME/.local/opt/jdk17" PATH="$HOME/.local/opt/jdk17/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug -q
ls -la app/build/outputs/apk/debug/app-debug.apk
[[ "${1:-}" == "install" ]] && adb install -r app/build/outputs/apk/debug/app-debug.apk || true
