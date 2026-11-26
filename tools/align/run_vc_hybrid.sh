#!/usr/bin/env bash
# Arma las emisoras híbridas de Vice City (base juego + canciones del CD en FLAC) y corre el control de cortes.
set -uo pipefail
V=/music/"GTA VC Radios"
one() { local s=$1 host=$2; shift 2
  echo "== $s"; python /tools/hybrid.py /game/$s.mp3 /out/$s "$host" "$@" && python /tools/qa.py /game/$s.mp3 /out/$s; }
one FLASH   "Toni"                          "$V/Volume 4 - Flash FM/"*.flac "$V/Volume 2 - Wave 103/12."*.flac
one VROCK   "Lazlow"                        "$V/Volume 1 - V-Rock/"*.flac
one WAVE    "Adam First"                    "$V/Volume 2 - Wave 103/"*.flac
one EMOTION "Fernando Martinez"             "$V/Volume 3 - Emotion 98.3/"*.flac
one FEVER   'Oliver "Ladykiller" Biscuit'   "$V/Volume 6 - Fever 105/"*.flac
one ESPANT  "Pepe"                          "$V/Volume 7 - Radio Espantoso/"*.flac
