#!/usr/bin/env bash
# Escanea con Shazam las emisoras musicales completas de San Andreas (3 a la vez). Reanudable.
cd "$(dirname "$0")"
M="$HOME/Music/GTA SA Radios/full tracks"; O="$(realpath ../../work/sa-full)"
printf '%s\n' "SF-UR" "Radio Los Santos" "Master Sounds 98.3" "K-DST" "Bounce FM" "K-JAH West" "Radio X" "K-Rose" "CSR 103.9" "Playback FM" |
  xargs -P1 -I{} docker run --rm --dns 1.1.1.1 --dns 8.8.8.8 -v "$M":/music:ro -v "$PWD":/work -v "$O":/out gta-shazam python scan_songs.py "/music/{}.mp3" "/out/{}.json" 120
echo SA_SCAN_DONE
