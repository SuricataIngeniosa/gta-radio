"""Arma los .cue de GTA VI con los capítulos de los videos "Full Radio Station" (info.json de yt-dlp, carpeta
Metadata_Timestamps), sin Shazam. Los audios del usuario arrancan igual que los videos (verificado con Shazam);
algunos siguen después con una repetición del final: el .cue termina donde termina el video (REM END).

Capítulos: "Tema - Artista1 / Artista2" = canción; "Track N" = tema sin identificar (en The Vault todos; en otras,
el número de archivo del juego, temas de 2-3 min): canción "Track N" con la emisora como artista; el nombre de la
emisora o "Part N" (WKTT, The Wipe) = charla (la app muestra radio + locutor).

Uso: python chapters_to_cue.py CARPETA_METADATA CARPETA_SALIDA
"""
import glob
import json
import os
import re
import sys


def ts(sec):
    f = int(round(max(0.0, sec) * 75))
    return f"{f // 4500:02d}:{f // 75 % 60:02d}:{f % 75:02d}"


def q(s):
    return s.replace('"', "'")


def main(meta, out):
    os.makedirs(out, exist_ok=True)
    for f in sorted(glob.glob(os.path.join(meta, "GTA 6 - *.info.json"))):
        station = re.match(r".*GTA 6 - (.*?) ｜", os.path.basename(f)).group(1)
        d = json.load(open(f, encoding="utf-8"))
        lines = [f'TITLE "{q(station)}"', f'FILE "{q(station)}.flac" WAVE', f"REM END {d['duration']}"]
        for i, c in enumerate(d["chapters"]):
            t = c["title"].strip()
            if " - " in t:
                title, artist = t.rsplit(" - ", 1)
                kind, artist = "SONG", ", ".join(a.strip() for a in artist.split(" / "))
            elif re.fullmatch(r"Track \d+", t):
                kind, title, artist = "SONG", t, station
            else:
                kind, title, artist = "TALK", "", ""
            lines += [f"  TRACK {i + 1:02d} AUDIO", f"    REM TYPE {kind}", f'    TITLE "{q(title)}"',
                      f'    PERFORMER "{q(artist)}"', f"    INDEX 01 {ts(c['start_time'])}"]
        open(os.path.join(out, f"{station}.cue"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
        n = sum(1 for c in d["chapters"] if " - " in c["title"])
        print(f"{station}: {len(d['chapters'])} tramos ({n} con nombre)")


if __name__ == "__main__":
    main(*sys.argv[1:])
