"""Arma el .cue de una emisora compilada (SA/IV/V) con lo que reconoció Shazam (scan_songs.py) y, si hay, los tracks
de un CD ubicados con locate.py (nombres exactos, también tandas y presentaciones del DJ).

Uso: python songs_to_cue.py SCAN.json SALIDA.cue [--loc LOC.json --tracklist lista.json --station "Nombre"]
Lo que no es canción ni tanda queda como TALK (la app muestra radio + locutor).
"""
import argparse
import json
import re
import unicodedata
from difflib import SequenceMatcher


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    s = re.sub(r"\(.*?\)|\[.*?\]", "", s).replace("&", "and")
    s = re.sub(r"\b(feat|ft)\b.*", "", s)
    return re.sub(r"[^a-z0-9]", "", re.sub(r"^the ", "", s.strip()))


def same(a, b):
    a, b = norm(a), norm(b)
    return bool(a and b) and (a == b or SequenceMatcher(None, a, b).ratio() >= 0.8 or (len(b) > 5 and (a.startswith(b) or b.startswith(a))))


def ts(sec):
    f = int(round(max(0.0, sec) * 75))
    return f"{f // 4500:02d}:{f // 75 % 60:02d}:{f % 75:02d}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("scan"); ap.add_argument("out")
    ap.add_argument("--loc"); ap.add_argument("--tracklist"); ap.add_argument("--station")
    a = ap.parse_args()
    scan = json.load(open(a.scan))
    total = scan["duration"]
    tl = json.load(open(a.tracklist)).get(a.station, []) if a.tracklist else []
    all_tl = [t for v in json.load(open(a.tracklist)).values() for t in v] if a.tracklist else []
    items = []
    # 1) Tracks del CD ubicados: nombres exactos.
    if a.loc:
        for r in json.load(open(a.loc)):
            if r.get("votes", 0) < max(4, r.get("probes", 8) // 2) or "offset" not in r:
                continue
            title = re.sub(r"^\d-\d+ ", "", r["file"]).rsplit(".", 1)[0]
            start, end = max(0.0, r["offset"]), min(total, r["offset"] + r["dur"])
            if re.search(r"\(Intro\)|^You're On|^That Was", title):
                items.append(dict(start=start, end=end, kind="TALK", title="", artist="", src="cd"))
                continue
            hit = next(((ar, t) for ar, t in tl if same(t, title)), None) or next(((ar, t) for ar, t in all_tl if same(t, title)), None)
            if hit:
                items.append(dict(start=start, end=end, kind="SONG", artist=hit[0], title=hit[1], src="cd"))
            else:
                items.append(dict(start=start, end=end, kind="AD", title=title, artist="", src="cd"))
    # 2) Shazam: lo que no se pisa con lo anterior. Con lista del juego, el nombre de la lista.
    for s in scan["songs"]:
        if any(min(s["end"], i["end"]) - max(s["start"], i["start"]) > 15 for i in items):
            continue
        artist, title = s["artist"], s["title"]
        if tl:
            hit = next(((ar, t) for ar, t in tl if same(t, title)), None)
            # Si el título no coincide (otra versión, p. ej. "Pacific State" = "Pacific 202") pero el artista tiene un
            # solo tema en la emisora, es ese.
            by_artist = [(ar, t) for ar, t in tl if norm(ar) and norm(ar) == norm(artist)]
            if not hit and len(by_artist) == 1:
                hit = by_artist[0]
            if hit:
                artist, title = hit
        items.append(dict(start=s["start"], end=s["end"], kind="SONG", artist=artist, title=title, src="shazam"))
    items.sort(key=lambda x: x["start"])
    # Shazam a veces parte un tema (otra versión, o alterna con otro en una mezcla): se juntan los temas seguidos con el
    # mismo título o artista, y un tramo de menos de 50 s pegado a otro tema se absorbe en el anterior.
    merged = []
    for it in items:
        prev = merged[-1] if merged else None
        if prev and prev["kind"] == it["kind"] == "SONG" and it["start"] - prev["end"] < 20 and (
                same(prev["title"], it["title"]) or norm(prev["artist"]) == norm(it["artist"])):
            prev["end"] = max(prev["end"], it["end"]); continue
        if prev and prev["kind"] == it["kind"] == "SONG" and it["end"] - it["start"] < 50 and it["start"] - prev["end"] < 20:
            prev["end"] = max(prev["end"], it["end"]); continue
        merged.append(it)
    items = merged
    timeline, cur = [], 0.0
    for it in items:
        st = max(it["start"], timeline[-1][0] + 1.0) if timeline else it["start"]
        if st - cur > 1.0 or not timeline:
            if not timeline or timeline[-1][1] != "TALK":
                timeline.append((cur if timeline else 0.0, "TALK", "", ""))
        if it["kind"] == "TALK":
            if timeline[-1][1] != "TALK":
                timeline.append((st, "TALK", "", ""))
        else:
            timeline.append((st, it["kind"], it["artist"], it["title"]))
        cur = max(cur, it["end"])
    if total - cur > 1.0 and timeline[-1][1] != "TALK":
        timeline.append((cur, "TALK", "", ""))
    lines = ['REM GENERATOR "songs_to_cue.py: Shazam (+ CD ubicado)"']
    for i, (st, kind, artist, title) in enumerate(timeline, 1):
        lines.append(f"  TRACK {i:02d} AUDIO")
        if kind == "SONG":
            lines += [f'    TITLE "{title}"', f'    PERFORMER "{artist}"']
        elif kind == "AD":
            lines.append(f'    TITLE "{title}"')
        lines += [f"    REM TYPE {kind}", f"    INDEX 01 {ts(st)}"]
    open(a.out, "w").write("\n".join(lines) + "\n")
    songs = [t for t in timeline if t[1] == "SONG"]
    print(f"{a.out}: {len(songs)} canciones, {sum(1 for t in timeline if t[1] == 'AD')} tandas con nombre, {len(timeline)} archivos")
    if tl:
        miss = [f"{ar} - {t}" for ar, t in tl if not any(same(t, s[3]) for s in songs)]
        if miss:
            print(f"   de la lista del juego sin ubicar: {miss}")


if __name__ == "__main__":
    main()
