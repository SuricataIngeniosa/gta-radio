"""Vice City desde la caja de 7 CD (FLAC): une los tracks de cada volumen en <STEM>.flac (sin pérdida) y escribe
<STEM>.cue con el inicio exacto de cada track (sumando las muestras). DJ y tandas (artista "Vice City") van como TALK.

Uso (en el contenedor gtaradio-align): python build_vc.py "/music/GTA VC Radios" /out
"""
import glob
import os
import subprocess
import sys

import mutagen

VOLUMES = {"V-Rock": ("VROCK", "Lazlow"), "Wave 103": ("WAVE", "Adam First"), "Emotion 98.3": ("EMOTION", "Fernando Martinez"),
           "Flash FM": ("FLASH", "Toni"), "Wildstyle Pirate Radio": ("WILD", "Mr. Magic"),
           "Fever 105": ("FEVER", "Oliver \"Ladykiller\" Biscuit"), "Radio Espantoso": ("ESPANT", "Pepe")}


# Etiquetas mal puestas en los FLAC de la caja: (artista, título del tag) -> título correcto.
FIX_TITLE = {("Cybotron", "Clean"): "Clear"}


def ts(samples, rate):
    f = samples * 75 // rate
    return f"{f // 4500:02d}:{f // 75 % 60:02d}:{f % 75:02d}"


def main(src, out, only=None):
    for vol in sorted(glob.glob(os.path.join(src, "Volume *"))):
        name = os.path.basename(vol).split(" - ", 1)[1]
        stem, host = VOLUMES[name]
        if only and stem != only:
            continue
        tracks = sorted(glob.glob(os.path.join(vol, "*.flac")))
        lines = ['REM GENERATOR "build_vc.py: caja oficial de 7 CD, inicios exactos por muestras"',
                 f'PERFORMER "{host}"', f'TITLE "{name}"', f'FILE "{stem}.flac" FLAC']
        pos, rate = 0, None
        n = 0
        for t in tracks:
            m = mutagen.File(t)
            rate = rate or m.info.sample_rate
            assert m.info.sample_rate == rate, t
            artist, title = m.tags["artist"][0], m.tags["title"][0]
            title = FIX_TITLE.get((artist, title), title)
            ad = artist == "Vice City" and title.startswith("Commercial")
            talk = artist == "Vice City" and not ad
            n += 1
            lines.append(f"  TRACK {n:02d} AUDIO")
            if ad:
                lines += [f'    TITLE "{title.split(":", 1)[1].strip()}"']
            elif not talk:
                lines += [f'    TITLE "{title}"', f'    PERFORMER "{artist}"']
            lines += [f"    REM TYPE {'AD' if ad else 'TALK' if talk else 'SONG'}", f"    INDEX 01 {ts(pos, rate)}"]
            pos += m.info.total_samples
        with open(os.path.join(out, f"{stem}.cue"), "w") as f:
            f.write("\n".join(lines) + "\n")
        lst = os.path.join(out, f"{stem}.txt")
        with open(lst, "w") as f:
            f.writelines("file '" + t.replace("'", "'\\''") + "'\n" for t in tracks)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-map", "0:a",
                        "-c:a", "flac", "-compression_level", "8", os.path.join(out, f"{stem}.flac")], check=True)
        os.remove(lst)
        print(f"{stem}: {len(tracks)} tracks, {n} tramos, {pos / rate / 60:.1f} min")


if __name__ == "__main__":
    main(*sys.argv[1:])
