#!/usr/bin/env bash
# Espera a que se conecte el S24 e instala el APK actual, y deja una copia en Download (para compartir / el BYD).
A="adb"; APK="$(dirname "$0")/../android/app/build/outputs/apk/debug/app-debug.apk"
until $A get-state 2>/dev/null | grep -q device; do sleep 10; done
$A install -r "$APK" | tail -1 && $A push "$APK" /sdcard/Download/GTA-Radio.apk | tail -1 && echo APK_INSTALADO
