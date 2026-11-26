#!/usr/bin/env bash
# Escanea con Shazam las emisoras de GTA IV que llegaron después (IF99, Fusion FM). Reanudable. Comparte el turnero.
cd "$(dirname "$0")"
M="$HOME/Music/GTA IV Radios/GTA_iv_Radio"; O="$(realpath ../../work/iv)"
printf '%s\n' "IF99" "FusionFM" |
  xargs -P1 -I{} docker run --rm --dns 1.1.1.1 --dns 8.8.8.8 -v "$M":/music:ro -v "$PWD":/work -v "$O":/out gta-shazam python scan_songs.py "/music/{}.mp3" "/out/{}.json" 120
echo IV_EXTRA_SCAN_DONE
