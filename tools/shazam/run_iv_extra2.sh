#!/usr/bin/env bash
# Escanea Independence FM (GTA IV, llegó después). Reanudable. Comparte el turnero.
cd "$(dirname "$0")"
M="$HOME/Music/GTA IV Radios/GTA_iv_Radio"; O="$(realpath ../../work/iv)"
docker run --rm --dns 1.1.1.1 --dns 8.8.8.8 -v "$M":/music:ro -v "$PWD":/work -v "$O":/out gta-shazam python scan_songs.py "/music/IndependenceFMDJGarySheen.mp3" "/out/IndependenceFMDJGarySheen.json" 120
echo IV_EXTRA2_SCAN_DONE
