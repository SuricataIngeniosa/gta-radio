#!/usr/bin/env bash
# Escanea las emisoras musicales con Shazam (paso 10 s, ventana 8 s). Reanudable: volver a correrlo sigue donde quedó.
set -euo pipefail
cd "$(dirname "$0")"
MUSIC="${MUSIC:-$HOME/Music/GTA Vice City Radios}"
OUT="$(realpath ../../data/vicecity/scan)"
for s in ${STATIONS:-FLASH VROCK WAVE EMOTION FEVER ESPANT}; do
  docker run --rm -v "$MUSIC":/music:ro -v "$PWD":/work -v "$OUT":/out gta-shazam \
    python scan.py "/music/$s.mp3" "/out/$s.jsonl" 10 8
done
echo SCAN_DONE
