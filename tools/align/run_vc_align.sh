#!/usr/bin/env bash
# Vice City: ubica cada canción del CD (FLAC) dentro del mp3 del juego. Salida: /out/<STEM>.cue y .json
set -euo pipefail
V=/music/"GTA VC Radios"; G=/game; O=/out
run() { python /tools/align.py "$G/$1.mp3" "$O/$1.cue" "$3" "$V/$2"/*.flac; }
run FLASH   "Volume 4 - Flash FM"               "Toni"
run VROCK   "Volume 1 - V-Rock"                 "Lazlow"
run WAVE    "Volume 2 - Wave 103"               "Adam First"
run EMOTION "Volume 3 - Emotion 98.3"           "Fernando Martinez"
run FEVER   "Volume 6 - Fever 105"              'Oliver "Ladykiller" Biscuit'
run ESPANT  "Volume 7 - Radio Espantoso"        "Pepe"
