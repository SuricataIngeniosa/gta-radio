#!/usr/bin/env bash
# Corta GTA III y Vice City en un archivo por tema/comentario/tanda (carpeta por emisora). Salida: /out/<juego>/<Emisora>/
set -euo pipefail
W=/p/work; S=/p/tools/align/split.py
talk_cue() { printf 'FILE "x" MP3\n  TRACK 01 AUDIO\n    REM TYPE TALK\n    INDEX 01 00:00:00\n' > "$1"; }
one() { python $S "$1" "$2" "/out/$3/$4" "$4" "$5"; }
G="GTA III Radios"
one $W/iii/HEAD.mp3  $W/iii/HEAD.cue  "$G" "Head Radio"      "Michael Hunt"
one $W/iii/CLASS.mp3 $W/iii/CLASS.cue "$G" "Double Clef FM"  "Morgan Merryweather"
one $W/iii/KJAH.mp3  $W/iii/KJAH.cue  "$G" "K-Jah"           'Horace "Pepper" Walker'
one $W/iii/RISE.mp3  $W/iii/RISE.cue  "$G" "Rise FM"         "Andre the Accelerator"
one $W/iii/LIPS.mp3  $W/iii/LIPS.cue  "$G" "Lips 106"        "Andee"
one $W/iii/GAME.mp3  $W/iii/GAME.cue  "$G" "Game Radio FM"   "Stretch Armstrong y Lord Sear"
one $W/iii/MSX.mp3   $W/iii/MSX.cue   "$G" "MSX FM"          "MC Codebreaker"
one $W/iii/FLASH.mp3 $W/iii/FLASH.cue "$G" "Flashback 95.6"  "Toni"
talk_cue /tmp/talk.cue
one $W/iii/CHAT.mp3  /tmp/talk.cue    "$G" "Chatterbox FM"   "Lazlow"
G="GTA VC Radios"
for p in "FLASH|Flash FM|Toni" "VROCK|V-Rock|Lazlow" "WAVE|Wave 103|Adam First" "EMOTION|Emotion 98.3|Fernando Martinez" \
         'FEVER|Fever 105|Oliver "Ladykiller" Biscuit' "ESPANT|Radio Espantoso|Pepe"; do
  IFS='|' read -r s n h <<< "$p"
  [ -f $W/vc-hybrid/$s.cue ] && one $W/vc-hybrid/$s.flac $W/vc-hybrid/$s.cue "$G" "$n" "$h"
done
one $W/vc-flac/WILD.flac $W/vc-flac/WILD.cue "$G" "Wildstyle" "Mr. Magic"
one $W/vc-game/KCHAT.mp3 /tmp/talk.cue "$G" "K-Chat" "Amy Sheckenhausen"
one $W/vc-game/VCPR.mp3  /tmp/talk.cue "$G" "VCPR"   "Maurice Chavez"
