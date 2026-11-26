#!/usr/bin/env bash
# Rehace Wildstyle, Wave 103 y Radio Espantoso sobre el audio del juego en FLAC (~/Music/GTA VC Radios/Juego):
# ubicar el CD -> híbrido -> (Wildstyle) Shazam en los huecos -> .cue -> cortar -> teléfono. Uso: tools/align/vc_redo.sh
set -uo pipefail
cd "$(dirname "$0")/../.."
P=$PWD; U="$(id -u):$(id -g)"
AL="docker run --rm -u $U -v $HOME/Music:/music:ro -v $P:/p gtaradio-align"
V="/music/GTA VC Radios"
one() { local s=$1 vol=$2 host=$3 key=$4 name=$5
  echo "== $s"
  $AL bash -c "python /p/tools/align/locate.py '$V/Juego/$s.flac' /p/work/vc-align/$s.loc.json '$V/$vol/'*.flac" | tail -3
  $AL bash -c "python /p/tools/align/hybrid.py '$V/Juego/$s.flac' /p/work/vc-hybrid/$s '$host' '$V/$vol/'*.flac" | tail -2
  if [ "$s" = WILD ]; then
    gaps=$(python3 -c "import json;print(','.join(f'{int(a)}-{int(b)}' for a,b in json.load(open('work/vc-hybrid/WILD.json'))['gaps']))")
    echo "huecos para Shazam: $gaps"
    docker run --rm -v "$HOME/Music/GTA VC Radios/Juego":/music:ro -v $P/tools/shazam:/work -v $P/data/vicecity/scan:/out gta-shazam \
      python scan_ranges.py /music/WILD.flac /out/WILD.jsonl "$gaps" 10 8 2>&1 | grep -v -E "Unclosed|client_session|connector|connections" | tail -3
  fi
  $AL python /p/tools/align/finalize.py /p/work/vc-hybrid/$s.json /p/data/vicecity/scan/$s.jsonl /p/data/vicecity/tracklist-uncensored.json $key /p/work/vc-hybrid/$s.cue /p/work/vc-align/$s.loc.json
  rm -rf "work/out/GTA VC Radios/$name"
  $AL python /p/tools/align/split.py /p/work/vc-hybrid/$s.flac /p/work/vc-hybrid/$s.cue "/p/work/out/GTA VC Radios/$name" "$name" "$host"
  adb shell "rm -rf '/sdcard/Music/GTA Vice City Radios/$name' && mkdir -p '/sdcard/Music/GTA Vice City Radios/$name'" && \
    adb push "work/out/GTA VC Radios/$name/." "/sdcard/Music/GTA Vice City Radios/$name/" | tail -1
}
one WILD "Volume 5 - Wildstyle Pirate Radio" "Mr. Magic" wildstyle "Wildstyle"
one WAVE "Volume 2 - Wave 103" "Adam First" wave103 "Wave 103"
one ESPANT "Volume 7 - Radio Espantoso" "Pepe" espantoso "Radio Espantoso"
echo VC_REDO_DONE
