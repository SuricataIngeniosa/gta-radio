#!/usr/bin/env bash
# Copia al teléfono las emisoras completas de SA, IV y V, una carpeta por emisora con un solo archivo
# "01_<Emisora>_Comentario_<locutor>.mp3" (sin nombres de temas todavía). Uso provisorio para probar.
set -uo pipefail
A="adb"; M="$HOME/Music"
put() { # juego_tel  archivo_origen  "Emisora (nombre de la app)"  "locutor"
  local dir="/sdcard/Music/$1/$3"
  $A shell "mkdir -p '$dir' && rm -f '$dir'/*" && $A push "$2" "$dir/01_$3_Comentario_$4.mp3" >/dev/null && echo "ok $1/$3"; }
SA="$M/GTA SA Radios/full tracks"; G="GTA SA Radios"
$A shell "cd '/sdcard/Music/$G' 2>/dev/null && rm -f *.mp3 *.cue"
put "$G" "$SA/Playback FM.mp3" "Playback FM" "Forth Right MC"
put "$G" "$SA/K-Rose.mp3" "K-Rose" "Mary-Beth Maybell"
put "$G" "$SA/K-DST.mp3" "K-DST" "Tommy The Nightmare Smith"
put "$G" "$SA/Bounce FM.mp3" "Bounce FM" "The Funktipus"
put "$G" "$SA/SF-UR.mp3" "SF-UR" "Hans Oberlander"
put "$G" "$SA/Radio Los Santos.mp3" "Radio Los Santos" "Julio G"
put "$G" "$SA/Radio X.mp3" "Radio X" "Sage"
put "$G" "$SA/CSR 103.9.mp3" "CSR 103.9" "Phillip PM Michaels"
put "$G" "$SA/K-JAH West.mp3" "K-Jah West" "Marshall Peters y Johnny Lawton"
put "$G" "$SA/Master Sounds 98.3.mp3" "Master Sounds 98.3" "Johnny The Love Giant Parkinson"
put "$G" "$SA/West Coast Talk Radio.mp3" "WCTR" "WCTR"
IV="$M/GTA IV Radios/GTA_iv_Radio"; G="GTA IV Radios"
for p in "Massive B Soundsystem 96.9|Massive B Soundsystem 96.9|Bobby Konders" "Liberty Rock Radio|Liberty Rock Radio 97.8|Iggy Pop" \
  "The Vibe 98.8|The Vibe 98.8|Vaughn Harper" "The Beat 102.7|The Beat 102.7|DJ Green Lantern y Mister Cee" "The Classics 104.1|The Classics 104.1|DJ Premier" \
  "Jazz Nation Radio 108.5|Jazz Nation Radio 108.5|Roy Haynes" "K109 The Studio|K109 The Studio|Karl Lagerfeld" "Vladivostok FM|Vladivostok FM|Ruslana" \
  "WKTT Radio|WKTT Radio|WKTT" "Electro-Choc|Electro-Choc|François K" "Liberty City Hardcore|L.C.H.C.|Jimmy Gestapo" "The Journey|The Journey|The Journey" \
  "Radio Broker|Radio Broker|Juliette Lewis" "San Juan Sounds|San Juan Sounds|Daddy Yankee" "Tuff Gong Radio|Tuff Gong Radio|Carl Bradshaw" \
  "Integrity 2.0|Integrity 2.0|Lazlow" "Public Liberty Radio|Public Liberty Radio|PLR" "RamJam FM|RamJam FM|David Rodigan" \
  "Vice City FM|Vice City FM|Fernando Martinez" "Self-Actualization FM|Self-Actualization FM|Audrey"; do
  IFS='|' read -r f n h <<< "$p"; put "$G" "$IV/$f.mp3" "$n" "$h"; done
V="$M/GTA V Radios"; G="GTA V Radios"
for p in "Los Santos Rock Radio|Los Santos Rock Radio|Kenny Loggins" "Non-Stop-Pop FM|Non Stop Pop FM|Cara Delevingne" "Radio Los Santos|Radio Los Santos|Big Boy" \
  "Channel X|Channel X|Keith Morris" "West Coast Talk Radio|WCTR|WCTR" "Rebel Radio|Rebel Radio|Jesco White" "Soulwax FM|Soulwax FM|Soulwax" \
  "East Los FM|East Los FM|Don Cheto y Camilo Lara" "West Coast Classics|West Coast Classics|DJ Pooh" "Blue Ark|The Blue Ark|Lee Scratch Perry" \
  "WorldWide FM|Worldwide FM|Gilles Peterson" "FlyLo FM|FlyLo FM|Flying Lotus" "Radio Mirror Park|Radio Mirror Park|Twin Shadow" "Space 103.2|Space 103.2|Bootsy Collins" \
  "Vinewood Boulevard Radio|Vinewood Boulevard Radio|Nate y Steve" "Blaine County Radio|Blaine County Radio|Blaine County Radio" "The Lab|The Lab|The Lab" \
  "Blonded Radio|blonded Los Santos 97.8|Frank Ocean" "Los Santos Underground Radio|Los Santos Underground Radio|Los Santos Underground Radio" "The Lowdown 91.1|The Lowdown 91.1|Mama G"; do
  IFS='|' read -r f n h <<< "$p"; put "$G" "$V/$f.mp3" "$n" "$h"; done
for G in "GTA SA Radios" "GTA IV Radios" "GTA V Radios"; do $A shell "echo > '/sdcard/Music/$G/.nomedia'"; done
echo PUSH_DONE
