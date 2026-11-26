#!/usr/bin/env python3
"""Arma un .cue por emisora a partir del escaneo de Shazam (data/vicecity/scan/<EST>.jsonl).

Cada ventana reconocida se contrasta con tracklist-uncensored.json: lo que no coincide con una
canción de la emisora (falsos positivos sobre locutores, comerciales) cuenta como TALK.
Uso: python3 -I tools/build_cue.py [EST ...]     (sin argumentos: todas las que tengan escaneo)
"""
import json, re, sys, unicodedata
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "data" / "vicecity"
STEP = 10.0  # paso del escaneo (scan.py)
IDS = {"FLASH": "flash", "VROCK": "vrock", "WAVE": "wave103", "EMOTION": "emotion",
       "FEVER": "fever", "ESPANT": "espantoso", "WILD": "wildstyle", "KCHAT": "kchat", "VCPR": "vcpr"}


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    s = re.sub(r"\(.*?\)|\[.*?\]|feat\..*|&|\band\b|\bthe\b", " ", s)
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def match(rec, tracks):
    """Índice de la canción de la emisora que corresponde a esta ventana, o None."""
    if not rec.get("title"):
        return None
    t, a = norm(rec["title"]), norm(rec["artist"])
    best, score = None, 0.0
    for i, tr in enumerate(tracks):
        st = SequenceMatcher(None, t, norm(tr["title"])).ratio()
        sa = SequenceMatcher(None, a, norm(tr["artist"])).ratio()
        s = st * 0.7 + sa * 0.3
        if s > score:
            best, score = i, s
    return best if score >= 0.6 else None


def smooth(labels, max_gap=3):
    """Rellena huecos de hasta max_gap ventanas sin reconocer dentro de la misma canción (partes instrumentales que
    Shazam no identifica) y borra 'blips' de 1 ventana."""
    lab = labels[:]
    i = 0
    while i < len(lab):
        if lab[i] is None and i > 0 and lab[i - 1] is not None:
            j = i
            while j < len(lab) and lab[j] is None:
                j += 1
            if j < len(lab) and j - i <= max_gap and lab[j] == lab[i - 1]:
                for k in range(i, j):
                    lab[k] = lab[i - 1]
            i = j
        else:
            i += 1
    for i in range(1, len(lab) - 1):
        if lab[i] is not None and lab[i] != lab[i - 1] and lab[i] != lab[i + 1]:
            lab[i] = None
    return lab


def cue_time(sec):
    f = round(sec * 75)
    return f"{f // 4500:02d}:{f // 75 % 60:02d}:{f % 75:02d}"


def build(est):
    station = json.loads((ROOT / "tracklist-uncensored.json").read_text())["stations"][IDS[est]]
    tracks, host = station["tracks"], station["host"]
    recs = sorted((json.loads(l) for l in (ROOT / "scan" / f"{est}.jsonl").open() if l.strip()),
                  key=lambda r: r["t"])
    labels = smooth([match(r, tracks) for r in recs])

    segs = []  # (start_s, idx | None)
    for r, lab in zip(recs, labels):
        if not segs or segs[-1][1] != lab:
            # El cambio ocurrió entre la ventana anterior y ésta: tomamos el punto medio.
            start = 0.0 if not segs else max(r["t"] - STEP / 2, segs[-1][0])
            segs.append((start, lab))

    lines = [f'REM GENERATOR "build_cue.py desde escaneo Shazam (paso {STEP:g} s, precision ~ +/-5 s)"',
             f'PERFORMER "{host}"', f'TITLE "{est}"', f'FILE "{est}.mp3" MP3']
    for n, (start, lab) in enumerate(segs, 1):
        lines.append(f"  TRACK {n:02d} AUDIO")
        if lab is None:
            lines += ['    TITLE "Locutor / tanda"', f'    PERFORMER "{host}"', "    REM TYPE TALK"]
        else:
            tr = tracks[lab]
            lines += [f'    TITLE "{tr["title"]}"', f'    PERFORMER "{tr["artist"]}"', "    REM TYPE SONG"]
        lines.append(f"    INDEX 01 {cue_time(start)}")
    (ROOT / "cue" / f"{IDS[est]}.cue").write_text("\n".join(lines) + "\n", encoding="utf-8")

    found = {lab for _, lab in segs if lab is not None}
    played = [tracks[l]["title"] for _, l in segs if l is not None]
    missing = [tracks[i]["title"] for i in range(len(tracks)) if i not in found]
    repeats = sorted({t for t in played if played.count(t) > 1})
    print(f"{est}: {len(recs)} ventanas, {len(segs)} segmentos, {len(found)}/{len(tracks)} canciones"
          + (f" | sin detectar: {missing}" if missing else "")
          + (f" | repetidas: {repeats}" if repeats else ""))


if __name__ == "__main__":
    ests = sys.argv[1:] or [p.stem for p in sorted((ROOT / "scan").glob("*.jsonl"))]
    for e in ests:
        build(e)
