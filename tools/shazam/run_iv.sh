#!/usr/bin/env bash
# Escanea con Shazam las emisoras musicales de GTA IV (4 a la vez). Reanudable.
cd "$(dirname "$0")"
M="$HOME/Music/GTA IV Radios/GTA_iv_Radio"; O="$(realpath ../../work/iv)"
printf '%s\n' "Liberty Rock Radio" "Radio Broker" "K109 The Studio" "Vice City FM" "The Beat 102.7" "Vladivostok FM" \
  "The Vibe 98.8" "Electro-Choc" "San Juan Sounds" "Liberty City Hardcore" "RamJam FM" "Tuff Gong Radio" "Jazz Nation Radio 108.5" \
  "Self-Actualization FM" "Massive B Soundsystem 96.9" "The Journey" "The Classics 104.1" |
  xargs -P1 -I{} docker run --rm --dns 1.1.1.1 --dns 8.8.8.8 -v "$M":/music:ro -v "$PWD":/work -v "$O":/out gta-shazam python scan_songs.py "/music/{}.mp3" "/out/{}.json" 120
echo IV_SCAN_DONE
