"""Corta una emisora (un audio + su .cue) en un archivo por tema, comentario o tanda:
    [orden]_[Emisora]_[Tema | Comentario | Tanda - X]_[Artista | locutor | Comercial].ext

FLAC -> FLAC (sin pérdida). mp3 -> mp3 320 kbps, cada archivo codificado aparte (con su cabecera LAME de retardo y
relleno), así el reproductor los encadena sin huecos ni glitches. Los cortes son exactos a la muestra.

Uso: python split.py AUDIO CUE CARPETA_SALIDA "Emisora" "Locutor"
"""
import os
import re
import subprocess
import sys


def parse_cue(path):
    segs, cur = [], None
    for line in open(path, encoding="utf-8"):
        t = line.strip()
        if t.startswith("TRACK "):
            cur = {"type": "SONG", "title": "", "artist": ""}
        elif cur is None:
            continue
        elif t.startswith("REM TYPE "):
            cur["type"] = t[9:].strip()
        elif t.startswith("TITLE "):
            cur["title"] = t[6:].strip().strip('"')
        elif t.startswith("PERFORMER "):
            cur["artist"] = t[10:].strip().strip('"')
        elif t.startswith("INDEX 01 "):
            m, s, f = map(int, t[9:].split(":"))
            cur["start"] = m * 60 + s + f / 75
            segs.append(cur)
    return sorted(segs, key=lambda x: x["start"])


def clean(s):
    s = re.sub(r'[\\/:*?"<>|]', "-", s or "").replace("_", "-")
    return re.sub(r"\s+", " ", s).strip(" .") or "-"


def duration(path):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                       capture_output=True, text=True, check=True)
    try:
        return float(r.stdout)
    except ValueError:
        # Sin duración en la cabecera (FLAC cortado a medias): se decodifica entero y se toma el último time=.
        e = subprocess.run(["ffmpeg", "-i", path, "-map", "0:a", "-f", "null", "-"], capture_output=True, text=True).stderr
        h, m, sec = re.findall(r"time=(\d+):(\d+):([\d.]+)", e)[-1]
        return int(h) * 3600 + int(m) * 60 + float(sec)


def main(audio, cue, out_dir, station, host):
    os.makedirs(out_dir, exist_ok=True)
    segs = parse_cue(cue)
    total = duration(audio)
    # REM END: el .cue cubre sólo hasta ahí (GTA VI: lo que sigue es una repetición); no se corta más allá.
    end_rem = [float(l.split()[2]) for l in open(cue, encoding="utf-8") if l.strip().startswith("REM END ")]
    if end_rem:
        total = min(total, end_rem[0])
    # Audio incompleto: sólo los tramos que llegan a tener al menos 1 s (el último puede quedar cortado).
    segs = [x for x in segs if x["start"] < total - 1]
    flac = audio.lower().endswith(".flac")
    ext = "flac" if flac else "mp3"
    # Si dos tramos arrancan a menos de 1 s, queda el segundo (p. ej. charla de 0 s seguida del tema que abre el archivo).
    merged = []
    for s in segs:
        if merged and s["start"] - merged[-1]["start"] < 1.0:
            s = dict(s, start=merged[-1]["start"])
            merged[-1] = s
            continue
        merged.append(s)
    width = max(2, len(str(len(merged))))
    for i, s in enumerate(merged):
        end = merged[i + 1]["start"] if i + 1 < len(merged) else total
        if s["type"] == "SONG":
            title, who = s["title"], s["artist"]
        elif s["type"] == "AD":
            title, who = f"Tanda - {s['title']}" if s["title"] else "Tanda", "Comercial"
        else:
            title, who = "Comentario", host
        name = f"{i + 1:0{width}d}_{clean(station)}_{clean(title)}_{clean(who)}.{ext}"
        codec = ["-c:a", "flac", "-compression_level", "8"] if flac else ["-c:a", "libmp3lame", "-b:a", "320k"]
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{s['start']:.4f}", "-t", f"{end - s['start']:.4f}", "-i", audio,
                        "-map", "0:a", "-map_metadata", "-1", *codec,
                        "-metadata", f"title={title}", "-metadata", f"artist={who}", "-metadata", f"album={station}",
                        "-metadata", f"track={i + 1}", os.path.join(out_dir, name)], check=True)
    print(f"{station}: {len(merged)} archivos en {out_dir}")


if __name__ == "__main__":
    main(*sys.argv[1:])
