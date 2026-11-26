"""Ubica cada canción suelta dentro del archivo completo de la emisora y escribe el .cue.

Uso (en el contenedor gtaradio-align):
  python align.py EMISORA.mp3 SALIDA.cue "Host" track1 track2 ...

Cada track trae título y artista (etiquetas o nombre "NN. Artista - Título"). Los que no son canciones
(DJ, intro, outro, tandas) no se buscan: lo que queda entre canciones es TALK (nombre de la radio + locutor).
Para cada canción se buscan tres muestras de 6 s (cerca del principio, en el medio y cerca del final)
por correlación normalizada; inicio y fin salen de la primera y la última muestra que coinciden.
"""
import json
import os
import re
import subprocess
import sys

import mutagen
import numpy as np
from scipy import fft

SR = 4000
PROBE = 6.0
MIN_NCC = 0.35

NOT_SONG = re.compile(r"\b(intro|outro|halftime|interlude|commercial)\b", re.I)


def decode(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()


def meta(path):
    """(artista, título) de las etiquetas; si no hay, del nombre 'NN. Artista - Título'."""
    m = mutagen.File(path, easy=True)
    tags = (m.tags if m else None) or {}
    artist = (tags.get("artist") or [None])[0]
    title = (tags.get("title") or [None])[0]
    if not (artist and title):
        stem = re.sub(r"^\d+\.\s*", "", os.path.splitext(os.path.basename(path))[0])
        if " - " in stem:
            artist, title = stem.split(" - ", 1)
    return artist, title


def is_song(path, artist, title):
    name = os.path.basename(path)
    if NOT_SONG.search(name) or NOT_SONG.search(title or ""):
        return False
    return not (artist or "").startswith("DJ ") and artist not in ("Commercial", "Vice City")


class Station:
    def __init__(self, x):
        self.x = x
        self.n = 1 << int(np.ceil(np.log2(len(x) + int(PROBE * SR))))
        self.X = fft.rfft(x, self.n, workers=-1)
        self.energy = np.concatenate([[0.0], np.cumsum(x.astype(np.float64) ** 2)])

    def find(self, probe):
        """(segundo donde empieza la mejor coincidencia, correlación normalizada 0-1)."""
        L = len(probe)
        p = probe - probe.mean()
        corr = fft.irfft(self.X * np.conj(fft.rfft(p, self.n, workers=-1)), self.n, workers=-1)[: len(self.x) - L]
        local = np.sqrt(np.maximum(self.energy[L:L + len(corr)] - self.energy[: len(corr)], 1e-9))
        ncc = corr / (local * np.linalg.norm(p) + 1e-9)
        k = int(np.argmax(ncc))
        return k / SR, float(ncc[k])


def ts(sec):
    f = int(round(sec * 75))
    return f"{f // 4500:02d}:{f // 75 % 60:02d}:{f % 75:02d}"


def main():
    station_path, out_cue, host, *tracks = sys.argv[1:]
    st = Station(decode(station_path))
    total = len(st.x) / SR
    found, report = [], []
    for t in tracks:
        artist, title = meta(t)
        if not is_song(t, artist, title):
            continue
        y = decode(t)
        dur = len(y) / SR
        edge = min(20.0, dur * 0.15)
        hits = []
        for at in (edge, dur / 2 - PROBE / 2, dur - edge - PROBE):
            if at < 0 or at + PROBE > dur:
                continue
            pos, ncc = st.find(y[int(at * SR): int((at + PROBE) * SR)])
            hits.append({"at": round(at, 1), "pos": round(pos, 2), "start": round(pos - at, 2), "ncc": round(ncc, 3)})
        good = [h for h in hits if h["ncc"] >= MIN_NCC]
        rec = {"file": os.path.basename(t), "artist": artist, "title": title, "dur": round(dur, 1), "hits": hits}
        if good:
            # Las muestras de una misma canción tienen que caer en el mismo lugar (±40 s: edición de radio).
            ref = max(good, key=lambda h: h["ncc"])
            same = [h for h in good if abs(h["start"] - ref["start"]) < 40]
            start = max(0.0, same[0]["pos"] - same[0]["at"])
            end = min(total, same[-1]["pos"] + (dur - same[-1]["at"]))
            rec.update(start=round(start, 2), end=round(end, 2), matched=len(same))
            found.append((start, end, artist, title))
        report.append(rec)

    found.sort()
    segs, cur = [], 0.0
    for start, end, artist, title in found:
        # Si se pisa con la anterior (mezcla de DJ, como en MSX), la nueva arranca donde arranca y corta a la anterior.
        if start - cur > 1.0 or not segs:
            segs.append((cur, "TALK", None, None))
        segs.append((start, "SONG", artist, title))
        cur = max(cur, end)
    if total - cur > 1.0:
        segs.append((cur, "TALK", None, None))
    # El primer tramo arranca en 0 aunque la canción empiece ahí.
    if segs and segs[0][0] > 0:
        segs.insert(0, (0.0, "TALK", None, None))

    fname = os.path.basename(station_path)
    lines = [f'REM GENERATOR "align.py: correlación contra los tracks sueltos (precisión ~0,1 s)"',
             f'PERFORMER "{host}"', f'TITLE "{os.path.splitext(fname)[0]}"', f'FILE "{fname}" MP3']
    for i, (start, typ, artist, title) in enumerate(segs, 1):
        lines += [f"  TRACK {i:02d} AUDIO"]
        if typ == "SONG":
            lines += [f'    TITLE "{title}"', f'    PERFORMER "{artist}"']
        lines += [f"    REM TYPE {typ}", f"    INDEX 01 {ts(start)}"]
    with open(out_cue, "w") as f:
        f.write("\n".join(lines) + "\n")
    with open(os.path.splitext(out_cue)[0] + ".json", "w") as f:
        json.dump({"station": fname, "duration": round(total, 1), "tracks": report}, f, ensure_ascii=False, indent=1)
    songs = [r for r in report if "start" in r]
    print(f"{fname}: {len(songs)}/{len(report)} canciones ubicadas, {len(segs)} tramos, {total / 60:.1f} min")
    for r in report:
        flag = "OK " if "start" in r else "-- "
        print(f"  {flag}{r.get('start', 0):8.1f}  {r['artist']} - {r['title']}  {[h['ncc'] for h in r['hits']]}")


if __name__ == "__main__":
    main()
