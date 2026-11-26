#!/usr/bin/env bash
# Copia a un dispositivo las emisoras ya cortadas en temas (III, VC y lo que terminó de SA/IV/V). Uso: DEV=<serial> tools/push_split.sh
set -uo pipefail
A="adb"; W="$(dirname "$0")/../work"
copy() { $A shell "mkdir -p '$2' && rm -f '$2'/*" && $A push "$1/." "$2/" | tail -1; }
for d in "$W/out/GTA III Radios"/*/; do copy "$d" "/sdcard/Music/GTA III Radios/$(basename "$d")"; done
for d in "$W/out/GTA VC Radios"/*/; do copy "$d" "/sdcard/Music/GTA Vice City Radios/$(basename "$d")"; done
for p in "out-sa-full|GTA SA Radios" "out-iv|GTA IV Radios" "out-v|GTA V Radios"; do IFS='|' read -r o g <<< "$p"
  for d in "$W/$o"/*/; do [ -f "$d/.done" ] && copy "$d" "/sdcard/Music/$g/$(basename "$d")"; done; done
echo SPLIT_DONE
