#!/usr/bin/env bash
# Escanea con Shazam las emisoras musicales de GTA V (4 a la vez), después de que termine GTA IV. Reanudable.
cd "$(dirname "$0")"
# (antes esperaba a que terminara GTA IV; ahora corre en paralelo)
M="$HOME/Music/GTA V Radios"; O="$(realpath ../../work/v)"
printf '%s\n' "Los Santos Underground Radio" "Non-Stop-Pop FM" "Los Santos Rock Radio" "Radio Mirror Park" "WorldWide FM" \
  "West Coast Classics" "Radio Los Santos" "Blonded Radio" "Space 103.2" "Blue Ark" "FlyLo FM" "Vinewood Boulevard Radio" \
  "Rebel Radio" "The Lab" "Channel X" "Soulwax FM" "East Los FM" "The Lowdown 91.1" |
  xargs -P1 -I{} docker run --rm --dns 1.1.1.1 --dns 8.8.8.8 -v "$M":/music:ro -v "$PWD":/work -v "$O":/out gta-shazam python scan_songs.py "/music/{}.mp3" "/out/{}.json" 120
echo V_SCAN_DONE
