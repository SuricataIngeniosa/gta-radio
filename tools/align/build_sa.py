"""San Andreas desde la caja de 8 CD (mp3): une los tracks de cada emisora en <STEM>.mp3 (sin recodificar) y escribe
<STEM>.cue. Artista y título salen de la lista del juego (los tags de la caja no traen artista). Intros, "You're On",
"That Was" y tandas son TALK. El disco 1 trae Playback FM y, después, canciones de Radio Los Santos (sin DJ).

Uso (contenedor gtaradio-align): python build_sa.py "/music/GTA SA Radios" /out /tools/sa_tracklist.json
"""
import glob
import json
import os
import re
import subprocess
import sys
import unicodedata
from difflib import SequenceMatcher

DISCS = {1: ("PLAYBACK", "Playback FM"), 2: ("KROSE", "K-Rose"), 3: ("BOUNCE", "Bounce FM"), 4: ("KDST", "K-DST"),
         5: ("MASTER", "Master Sounds 98.3"), 6: ("CSR", "CSR 103.9"), 7: ("KJAH", "K-Jah West"), 8: ("RADIOX", "Radio X")}


def norm(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    s = re.sub(r"\(.*?\)", "", s).replace("&", "and")
    s = re.sub(r"^the ", "", s.strip())
    return re.sub(r"[^a-z0-9]", "", s)


def ts(samples, rate):
    f = samples * 75 // rate
    return f"{f // 4500:02d}:{f // 75 % 60:02d}:{f % 75:02d}"


def samples(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", "44100", "-f", "s16le", "-"],
                         check=True, capture_output=True).stdout
    return len(raw) // 2


def main(src, out, tl_path):
    tl = json.load(open(tl_path))
    stations = {}
    for f in sorted(glob.glob(os.path.join(src, "*.mp3"))):
        disc, title = re.match(r"(\d)-\d+ (.*)\.mp3$", os.path.basename(f)).groups()
        stem, name = DISCS[int(disc)]
        # Canción: primero la lista de la emisora del disco; en el disco 1, también la de Radio Los Santos.
        cands = [name] + (["Radio Los Santos"] if stem == "PLAYBACK" else [])
        hit = None
        for st in cands:
            for artist, t in tl[st]:
                a, b = norm(t), norm(title)
                if a == b or (len(b) > 5 and (a.startswith(b) or b.startswith(a))) or SequenceMatcher(None, a, b).ratio() >= 0.8:
                    hit = (st, artist, t); break
            if hit:
                break
        if hit:
            st_name, artist, t = hit
            key = "RLS" if st_name == "Radio Los Santos" else stem
            stations.setdefault(key, []).append((f, "SONG", artist, t))
        else:
            stations.setdefault(stem, []).append((f, "TALK", None, title))
    for stem, items in stations.items():
        lines = ['REM GENERATOR "build_sa.py: caja oficial de 8 CD; artistas de la lista del juego"', f'FILE "{stem}.mp3" MP3']
        pos, n, prev_talk = 0, 0, False
        for f, kind, artist, title in items:
            talk = kind == "TALK"
            if not (talk and prev_talk):
                n += 1
                lines.append(f"  TRACK {n:02d} AUDIO")
                if not talk:
                    lines += [f'    TITLE "{title}"', f'    PERFORMER "{artist}"']
                lines += [f"    REM TYPE {kind}", f"    INDEX 01 {ts(pos, 44100)}"]
            prev_talk = talk
            pos += samples(f)
        open(os.path.join(out, f"{stem}.cue"), "w").write("\n".join(lines) + "\n")
        lst = os.path.join(out, f"{stem}.txt")
        open(lst, "w").writelines("file '" + f.replace("'", "'\\''") + "'\n" for f, *_ in items)
        # Sin recodificar (copy): no se pierde calidad; cada unión suma ~26 ms de relleno del mp3.
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-map", "0:a",
                        "-c:a", "copy", os.path.join(out, f"{stem}.mp3")], check=True)
        os.remove(lst)
        songs = [t for _, k, _, t in items if k == "SONG"]
        talk = [t for _, k, _, t in items if k == "TALK"]
        print(f"{stem}: {len(songs)} canciones, {pos / 44100 / 60:.1f} min; charla/tandas: {talk}")
    # Lo que la caja no trae, por emisora.
    print("\nFALTAN (están en el juego, no en la caja):")
    have = {norm(t) for items in stations.values() for _, k, _, t in items if k == "SONG"}
    for st, tracks in tl.items():
        miss = [f"{a} - {t}" for a, t in tracks
                if not any(norm(t) == h or SequenceMatcher(None, norm(t), h).ratio() >= 0.8 or (len(h) > 5 and norm(t).startswith(h)) for h in have)]
        print(f"  {st} ({len(tracks) - len(miss)}/{len(tracks)}): " + "; ".join(miss))


if __name__ == "__main__":
    main(*sys.argv[1:])
