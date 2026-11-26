#!/usr/bin/env bash
# Convierte cada FLAC de /in a FLAC 16 bit en /out (mismo nombre), 4 a la vez; saltea los ya hechos.
one() { o="/out/${1#./}"; [ -s "$o" ] && return 0
  ffmpeg -v error -y -i "$1" -map 0:a -af aresample=osf=s16:dither_method=triangular -c:a flac -compression_level 8 "$o.part.flac" && mv "$o.part.flac" "$o"; }
export -f one
cd /in && find . -maxdepth 1 -name '*.flac' -print0 | xargs -0 -P4 -I{} bash -c 'one "$1"' _ {}
