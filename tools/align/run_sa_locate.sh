#!/usr/bin/env bash
# San Andreas: ubica los 125 tracks de la caja de CD dentro de cada emisora completa del juego.
set -uo pipefail
F="/music/GTA SA Radios/full tracks"
for f in "$F"/*.mp3; do
  s=$(basename "$f" .mp3); echo "== $s"
  python /tools/locate.py "$f" "/out/$s.loc.json" "/music/GTA SA Radios/"*.mp3 | awk '$1+0 >= 5'
done
echo SA_LOCATE_DONE
