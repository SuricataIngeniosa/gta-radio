#!/usr/bin/env bash
# Sube al teléfono las emisoras de GTA VI a medida que split_vi.sh las termina (~/Music/GTA VI Radios/<Emisora>/, sin .tmp).
# Para el teléfono: FLAC 16 bit (los cortes de la PC son 24 bit y no entran). Reemplaza la carpeta de la emisora entera.
# Uso: tools/push_vi.sh   (sale cuando split_vi terminó y no queda nada por subir)
set -uo pipefail
A="adb"; M="$HOME/Music/GTA VI Radios"; W="$(cd "$(dirname "$0")/.." && pwd)/work/vi-phone"; P="/sdcard/Music/GTA VI Radios"; T="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$W"; $A shell "mkdir -p '$P' && touch '$P/.nomedia'"
while :; do
  for d in "$M"/*/; do
    s="$(basename "$d")"; case "$s" in *.tmp|Metadata_Timestamps|Pistas_Separadas) continue;; esac
    [ -f "$W/$s.pushed" ] && continue
    mkdir -p "$W/$s"
    docker run --rm --user "$(id -u):$(id -g)" -v "$d":/in:ro -v "$W/$s":/out -v "$T":/t:ro gtaradio-align bash /t/align/to16.sh || { echo "falló conversión $s"; continue; }
    [ "$(ls "$d"/*.flac | wc -l)" = "$(ls "$W/$s"/*.flac | grep -vc part)" ] || { echo "faltan archivos $s"; continue; }
    $A shell "rm -rf '$P/$s.tmp' && mkdir -p '$P/$s.tmp'" && $A push "$W/$s/." "$P/$s.tmp/" | tail -1 \
      && $A shell "rm -rf '$P/$s' && mv '$P/$s.tmp' '$P/$s'" && touch "$W/$s.pushed" && rm -rf "$W/$s" && echo "SUBIDA: $s"
  done
  [ -n "$(docker ps -q --filter ancestor=gtaradio-align)" ] || ls -d "$M"/*.tmp >/dev/null 2>&1 || { left=0; for d in "$M"/*/; do s="$(basename "$d")"; case "$s" in Metadata_Timestamps|Pistas_Separadas) ;; *) [ -f "$W/$s.pushed" ] || left=1;; esac; done; [ $left = 0 ] && { echo PUSH_VI_DONE; exit 0; }; }
  sleep 30
done
