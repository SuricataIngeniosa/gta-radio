"""Arma el .cue de una emisora híbrida: canciones del CD (tags), temas que sólo están en el juego (Shazam, con el
nombre de la lista del juego si coincide) y el resto como TALK (la app muestra radio + locutor).

Uso: python finalize.py STEM.json SHAZAM.jsonl tracklist.json clave_lista SALIDA.cue
Los tiempos de Shazam son del archivo del juego; se pasan al de salida con los cortes que guardó hybrid.py.
"""
import json
import re
import sys
import unicodedata


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]", "", re.sub(r"\(.*?\)", "", s))


def ts(sec):
    f = int(round(max(0.0, sec) * 75))
    return f"{f // 4500:02d}:{f // 75 % 60:02d}:{f % 75:02d}"


def main():
    hj, shz, tl_path, tl_key, out, *loc = sys.argv[1:]
    d = json.load(open(hj))
    kept = sorted([i for i in d["items"] if "o_in" in i], key=lambda i: i["g_in"])

    def to_out(gt):
        delta = 0.0
        for i in kept:
            if gt < i["g_in"]:
                break
            if gt <= i["g_out"]:
                return i["o_in"] + (gt - i["g_in"]) * (i["o_out"] - i["o_in"]) / max(1e-6, i["g_out"] - i["g_in"])
            delta = i["o_out"] - i["g_out"]
        return gt + delta

    segs = [dict(start=s["start"], end=s["end"], kind=s["kind"] if s["kind"] in ("SONG", "AD") else "TALK", artist=s["artist"],
                 title=re.sub(r"^Commercial:\s*", "", s["title"]) if s["kind"] == "AD" else s["title"], src="CD") for s in d["segments"]]

    # Canciones del CD que se ubicaron pero quedaron con el audio del juego (cortes dudosos): igual llevan su nombre.
    named = {(s["artist"], s["title"]) for s in segs}
    for lp in loc:
        for r in json.load(open(lp)):
            m = re.match(r"\d+\.\s*(.+?) - (.+)\.flac$", r["file"])
            if not m or r.get("votes", 0) < 5 or "offset" not in r:
                continue
            artist, title = m.groups()
            if artist in ("Vice City", "Commercial") or artist.startswith(("DJ ", "Mr. Magic")) or (artist, title) in named:
                continue
            if any(norm(title) == norm(s["title"]) for s in segs):
                continue
            g0, g1 = max(0.0, r["offset"]), min(d["game_duration"], r["offset"] + r["dur"])
            segs.append(dict(start=round(to_out(g0), 2), end=round(to_out(g1), 2), kind="SONG", artist=artist.replace("Kate Bush 14", "Kate Bush"),
                             title=title, src="CD (audio del juego)"))

    def covered(t):
        return any(s["start"] - 5 <= t <= s["end"] + 5 for s in segs)

    # Temas sólo del juego: grupos de aciertos seguidos de Shazam con la misma canción, fuera de lo que cubre el CD.
    hits = []
    try:
        hits = [json.loads(l) for l in open(shz) if l.strip()]
    except FileNotFoundError:
        pass
    hits = sorted([h for h in hits if h.get("key")], key=lambda h: h["t"])
    groups = []
    for h in hits:
        ot = to_out(h["t"])
        if covered(ot) or covered(to_out(h["t"] + 8)):
            continue
        g = groups[-1] if groups else None
        if g and g["key"] == h["key"] and h["t"] - g["t1"] <= 25:
            g["t1"] = h["t"]; g["n"] += 1
        else:
            groups.append(dict(key=h["key"], title=h["title"], artist=h["artist"], t0=h["t"], t1=h["t"], n=1))
    tl = json.load(open(tl_path))["stations"].get(tl_key, {}).get("tracks", [])
    game_only = []
    for g in groups:
        if g["n"] < 3:  # menos de ~30 s seguidos: cortina o DJ con un fragmento, no un tema
            continue
        name = next((t for t in tl if norm(t["title"])[:8] and (norm(t["title"])[:8] in norm(g["title"]) or norm(g["title"])[:8] in norm(t["title"]))), None)
        if tl and not name:
            print(f"   (descartado, no está en la lista del juego: {g['artist']} - {g['title']} en {to_out(g['t0']) / 60:.1f} min)")
            continue
        artist, title = (name["artist"], name["title"]) if name else (g["artist"], g["title"])
        start, end = to_out(g["t0"]) - 4, to_out(g["t1"] + 8)
        game_only.append(dict(start=start, end=end, kind="SONG", artist=artist, title=title, src="juego (Shazam)", n=g["n"]))
    # Un tema del juego suena una vez por vuelta: si Shazam lo da en dos lugares lejanos, queda el de más aciertos.
    best = {}
    for s_ in game_only:
        if s_["title"] not in best or s_["n"] > best[s_["title"]]["n"]:
            best[s_["title"]] = s_
    game_only = [s_ for s_ in game_only if best[s_["title"]] is s_ or abs(s_["start"] - best[s_["title"]]["end"]) < 40]
    # Si el mismo tema aparece en dos grupos pegados (un hueco de Shazam), se unen.
    game_only.sort(key=lambda s: s["start"])
    merged = []
    for s in game_only:
        if merged and merged[-1]["title"] == s["title"] and s["start"] - merged[-1]["end"] < 40:
            merged[-1]["end"] = s["end"]
        else:
            merged.append(s)
    segs += merged
    segs.sort(key=lambda s: s["start"])

    total = d["duration"]
    lines = ['REM GENERATOR "hybrid.py + finalize.py: juego + canciones del CD en FLAC; temas sólo del juego por Shazam (±5 s)"',
             f'PERFORMER "{d["host"]}"', f'FILE "{hj.rsplit("/", 1)[-1].replace(".json", ".flac")}" FLAC']
    n, cur, timeline = 0, 0.0, []
    for s in segs:
        # Arranca donde arranca (si se pisa con la anterior, la corta), siempre después del tramo previo.
        st = max(s["start"], timeline[-1][0] + 1.0) if timeline else s["start"]
        if st - cur > 1.0 or n == 0:
            timeline.append((cur if n else 0.0, "TALK", None, None))
            n += 1
        timeline.append((max(st, 0.0), s["kind"], s["artist"], s["title"]))
        n += 1
        cur = max(cur, s["end"])
    if total - cur > 1.0:
        timeline.append((cur, "TALK", None, None))
    # Dos TALK seguidos se unen.
    clean = []
    for t in timeline:
        if clean and t[1] == "TALK" and clean[-1][1] == "TALK":
            continue
        clean.append(t)
    for i, (st, kind, artist, title) in enumerate(clean, 1):
        lines.append(f"  TRACK {i:02d} AUDIO")
        if kind == "SONG":
            lines += [f'    TITLE "{title}"', f'    PERFORMER "{artist}"']
        elif kind == "AD":
            lines += [f'    TITLE "{title}"']
        lines += [f"    REM TYPE {kind}", f"    INDEX 01 {ts(st)}"]
    open(out, "w").write("\n".join(lines) + "\n")
    print(f"{out}: {sum(1 for c in clean if c[1] == 'SONG')} canciones "
          f"({sum(1 for s in segs if s['src'] == 'CD' and s['kind'] == 'SONG')} del CD, {len(merged)} sólo del juego)")
    for s in merged:
        print(f"   juego: {s['start'] / 60:5.1f} min  {s['artist']} - {s['title']}")
    named_titles = {norm(s["title"]) for s in segs if s["kind"] == "SONG"}
    missing = [f'{t["artist"]} - {t["title"]}' for t in tl
               if not any(norm(t["title"])[:8] in n or n[:8] in norm(t["title"]) for n in named_titles)]
    if missing:
        print(f"   de la lista del juego sin ubicar: {missing}")
    uncovered, cur = [], 0.0
    for s in segs:
        if s["start"] - cur > 90:
            uncovered.append((round(cur), round(s["start"])))
        cur = max(cur, s["end"])
    if total - cur > 90:
        uncovered.append((round(cur), round(total)))
    if uncovered:
        print(f"   huecos largos sin nombre (s): {uncovered}")


if __name__ == "__main__":
    main()
