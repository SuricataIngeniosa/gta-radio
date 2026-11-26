#!/usr/bin/env bash
# Corta GTA VI (FLAC del usuario + .cue de chapters_to_cue.py) en un archivo por tema. Salida: /out/<Emisora>/
# Correr en gtaradio-align: -v ~/Music/"GTA VI Radios":/music:ro -v <repo>:/p -v <salida>:/out
# AfroBank, Back Country, Gloriana, Symphony, The Vault y Thrillbilly: FLAC incompletos (2026-11-24), se cortan igual
# hasta donde llega el audio (pedido del usuario). Locutor desconocido (null en Stations.kt) = el nombre de la emisora.
set -euo pipefail
one() { [ -d "/out/$2" ] && { echo "ya está: $2"; return; }; python /p/tools/align/split.py "/music/$1.flac" "/p/work/vi/$1.cue" "/out/$2.tmp" "$2" "$3" && mv "/out/$2.tmp" "/out/$2"; }
one "VCRR"                    "VCRR"                    "JT y DJ Stevie J"
one "Dirty South Classics"    "Dirty South Classics"    "Trick Daddy y Trina"
one "Honey FM"                "Honey FM"                "Honey FM"
one "Cocoteo FM"              "Cocoteo FM"              "Bad Bunny y RaiNao"
one "Radio La Ola"            "Radio La Ola"            "Gloria y Emilio Estefan"
one "AfroBank FM"             "AfroBank FM"             "Burna Boy y Palms Trax"
one "Radio On-U"              "Radio On-U"              "Adrian Sherwood"
one "Flash FM"                "Flash FM"                "Robyn y Alex Consani"
one "Emotion 98.3"            "Emotion 98.3"            "Emotion 98.3"
one "Vice City FM"            "Vice City FM"            "Weird Al Yankovic y Brigitte Nielsen"
one "V-Rock"                  "V-Rock"                  "Lindsey Buckingham y Michael McDonald"
one "The Chamber 106.6"       "The Chamber 106.6"       "Kerry King y Tom Araya"
one "Back Country Radio"      "Back Country Radio"      "Morgan Wallen y Lana Del Rey"
one "Gloriana Blues Radio"    "Gloriana Blues Radio"    "Buddy Guy y Keith Richards"
one "Stockyard FM"            "Stockyard FM"            "The Dare y PinkPantheress"
one "Circoloco Records Radio" "CircoLoco Records Radio" "Seth Troxler y Antonio Carbonaro"
one "The Vault Radio"         "The Vault Radio"         "DJ Pretzel"
one "Worldwide FM"            "Worldwide FM"            "Gilles Peterson"
one "Kaleidoscope FM"         "Kaleidoscope FM"         "Jeremy Irons"
one "Leonida Jazz Radio"      "Leonida Jazz Radio"      "Randy Brecker y Stanley Clarke"
one "Symphony FM"             "Symphony FM"             "Yuja Wang"
one "WKTT"                    "WKTT"                    "WKTT"
one "The Wipe"                "The Wipe"                "The Wipe"
one "The Sniper of La Perle"     "The Sniper of La Perle"     "The Sniper of La Perle"
one "Thrillbilly Mud Club Radio" "Thrillbilly Mud Club Radio" "Thrillbilly Mud Club Radio"
