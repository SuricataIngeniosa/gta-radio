#!/usr/bin/env bash
# GTA III: copia cada emisora completa como <STEM>.mp3 y arma <STEM>.cue alineando los tracks sueltos.
set -euo pipefail
M=/music/"GTA III Radios"; O=/out
run() { local stem=$1 full=$2 prefix=$3 host=$4
  cp "$M/Grand Theft Auto III - $full.mp3" "$O/$stem.mp3"
  shopt -s nullglob; local tracks=("$M/[$prefix] "*.mp3)
  if ((${#tracks[@]})); then python /tools/align.py "$O/$stem.mp3" "$O/$stem.cue" "$host" "${tracks[@]}"; else echo "$stem: sin tracks sueltos"; fi; }
run HEAD  "Head Radio"      "Head Radio"   "Michael Hunt"
run CLASS "Double Clef FM"  "Double Clef"  "Morgan Merryweather"
run KJAH  "K-Jah 103"       "K-Jah Radio"  'Horace "Pepper" Walker'
run RISE  "Rise FM"         "Rise FM"      "Andre the Accelerator"
run LIPS  "Lips 106"        "Lips FM"      "Andee"
run GAME  "Game Radio FM"   "Game FM"      "Stretch Armstrong y Lord Sear"
run MSX   "MSX FM"          "MSX FM"       "MC Codebreaker"
run FLASH "Flashback FM"    "Flashback FM" "Toni"
run CHAT  "Chatterbox FM"   "Chatterbox"   "Lazlow"
