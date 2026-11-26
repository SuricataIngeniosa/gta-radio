#!/usr/bin/env bash
set -euo pipefail
V=/music/"GTA VC Radios"
for p in "VROCK:Volume 1 - V-Rock" "WAVE:Volume 2 - Wave 103" "EMOTION:Volume 3 - Emotion 98.3" "FEVER:Volume 6 - Fever 105" "ESPANT:Volume 7 - Radio Espantoso"; do
  s=${p%%:*}; d=${p#*:}; echo "== $s"; python /tools/locate.py /game/$s.mp3 /out/$s.loc.json "$V/$d/"*.flac
done
