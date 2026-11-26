#!/usr/bin/env python3
"""A medida que Shazam termina una emisora (SA, IV o V), la corta en temas con nombre y la copia al teléfono,
reemplazando el archivo provisorio. Corre en bucle hasta que no quede nada pendiente. Uso: tools/pipeline.py

Por emisora: work/<dir>/<archivo>.json (scan_songs.py) -> songs_to_cue.py -> .cue -> split.py -> work/out-<dir>/<Emisora>/
-> adb push a /sdcard/Music/<carpeta del juego>/<Emisora>/. Marca .done para no repetir.
"""
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HOME = Path.home()
ADB = ["adb"]  # usa ANDROID_SERIAL si hay varios equipos
DOCKER = ["docker", "run", "--rm", "-u", f"{subprocess.check_output(['id', '-u']).decode().strip()}:{subprocess.check_output(['id', '-g']).decode().strip()}",
          "-v", f"{ROOT}:/p", "-v", f"{HOME}/Music:/music:ro", "gtaradio-align", "python"]

# archivo de origen -> (nombre de la emisora en la app, locutor)
GAMES = {
    "sa-full": ("GTA SA Radios", "GTA SA Radios/full tracks", {
        "Playback FM": ("Playback FM", "Forth Right MC"), "K-Rose": ("K-Rose", "Mary-Beth Maybell"),
        "K-DST": ("K-DST", "Tommy The Nightmare Smith"), "Bounce FM": ("Bounce FM", "The Funktipus"),
        "SF-UR": ("SF-UR", "Hans Oberlander"), "Radio Los Santos": ("Radio Los Santos", "Julio G"), "Radio X": ("Radio X", "Sage"),
        "CSR 103.9": ("CSR 103.9", "Phillip PM Michaels"), "K-JAH West": ("K-Jah West", "Marshall Peters y Johnny Lawton"),
        "Master Sounds 98.3": ("Master Sounds 98.3", "Johnny The Love Giant Parkinson")}),
    "iv": ("GTA IV Radios", "GTA IV Radios/GTA_iv_Radio", {
        "Massive B Soundsystem 96.9": ("Massive B Soundsystem 96.9", "Bobby Konders"), "Liberty Rock Radio": ("Liberty Rock Radio 97.8", "Iggy Pop"),
        "The Vibe 98.8": ("The Vibe 98.8", "Vaughn Harper"), "The Beat 102.7": ("The Beat 102.7", "DJ Green Lantern y Mister Cee"),
        "The Classics 104.1": ("The Classics 104.1", "DJ Premier"), "Jazz Nation Radio 108.5": ("Jazz Nation Radio 108.5", "Roy Haynes"),
        "K109 The Studio": ("K109 The Studio", "Karl Lagerfeld"), "Vladivostok FM": ("Vladivostok FM", "Ruslana"),
        "Electro-Choc": ("Electro-Choc", "François K"), "Liberty City Hardcore": ("L.C.H.C.", "Jimmy Gestapo"),
        "The Journey": ("The Journey", "The Journey"), "Radio Broker": ("Radio Broker", "Juliette Lewis"),
        "San Juan Sounds": ("San Juan Sounds", "Daddy Yankee"), "Tuff Gong Radio": ("Tuff Gong Radio", "Carl Bradshaw"),
        "RamJam FM": ("RamJam FM", "David Rodigan"), "Vice City FM": ("Vice City FM", "Fernando Martinez"),
        "Self-Actualization FM": ("Self-Actualization FM", "Audrey"),
        "IF99": ("IF99", "Femi Kuti"), "FusionFM": ("Fusion FM", "Roy Ayers"),
        "IndependenceFMDJGarySheen": ("Independence FM", "Gary Sheen")}),
    "v": ("GTA V Radios", "GTA V Radios", {
        "Los Santos Rock Radio": ("Los Santos Rock Radio", "Kenny Loggins"), "Non-Stop-Pop FM": ("Non Stop Pop FM", "Cara Delevingne"),
        "Radio Los Santos": ("Radio Los Santos", "Big Boy"), "Channel X": ("Channel X", "Keith Morris"), "Rebel Radio": ("Rebel Radio", "Jesco White"),
        "Soulwax FM": ("Soulwax FM", "Soulwax"), "East Los FM": ("East Los FM", "Don Cheto y Camilo Lara"),
        "West Coast Classics": ("West Coast Classics", "DJ Pooh"), "Blue Ark": ("The Blue Ark", "Lee Scratch Perry"),
        "WorldWide FM": ("Worldwide FM", "Gilles Peterson"), "FlyLo FM": ("FlyLo FM", "Flying Lotus"),
        "Radio Mirror Park": ("Radio Mirror Park", "Twin Shadow"), "Space 103.2": ("Space 103.2", "Bootsy Collins"),
        "Vinewood Boulevard Radio": ("Vinewood Boulevard Radio", "Nate y Steve"), "The Lab": ("The Lab", "The Lab"),
        "Blonded Radio": ("blonded Los Santos 97.8", "Frank Ocean"), "Los Santos Underground Radio": ("Los Santos Underground Radio", "Los Santos Underground Radio"),
        "The Lowdown 91.1": ("The Lowdown 91.1", "Mama G")}),
}
SA_TRACKLIST_KEY = {"K-JAH West": "K-Jah West"}


def process(d, src_name, scan):
    phone_dir, music_dir, mapping = GAMES[d]
    station, host = mapping[src_name]
    out = ROOT / f"work/out-{d}/{station}"
    if (out / ".done").exists():
        return False
    cue = ROOT / f"work/{d}/{src_name}.cue"
    extra = []
    if d == "sa-full":
        loc = ROOT / f"work/sa-full/{src_name}.loc.json"
        if loc.exists():
            extra += ["--loc", f"/p/{loc.relative_to(ROOT)}"]
        extra += ["--tracklist", "/p/tools/align/sa_tracklist.json", "--station", SA_TRACKLIST_KEY.get(src_name, src_name)]
    r = subprocess.run(DOCKER + ["/p/tools/align/songs_to_cue.py", f"/p/{scan.relative_to(ROOT)}", f"/p/{cue.relative_to(ROOT)}", *extra],
                       capture_output=True, text=True)
    print(r.stdout.strip() or r.stderr.strip()[-400:], flush=True)
    if r.returncode:
        return False
    if out.exists():
        for f in out.iterdir():
            f.unlink()
    r = subprocess.run(DOCKER + ["/p/tools/align/split.py", f"/music/{music_dir}/{src_name}.mp3", f"/p/{cue.relative_to(ROOT)}",
                                 f"/p/{out.relative_to(ROOT)}", station, host], capture_output=True, text=True)
    print(r.stdout.strip() or r.stderr.strip()[-400:], flush=True)
    if r.returncode:
        return False
    dest = f"/sdcard/Music/{phone_dir}/{station}"
    if subprocess.run(ADB + ["get-state"], capture_output=True).returncode:
        print("teléfono no conectado; queda para la próxima vuelta", flush=True)
        return False
    # Si desenchufan el teléfono a mitad de la copia, no caerse: queda para la próxima vuelta (sin .done).
    try:
        subprocess.run(ADB + ["shell", f"mkdir -p '{dest}' && rm -f '{dest}'/*"], check=True, timeout=60)
        subprocess.run(ADB + ["push", f"{out}/.", f"{dest}/"], check=True, capture_output=True, timeout=1800)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        print(f"falló la copia al teléfono de {station}; queda para la próxima vuelta", flush=True)
        return False
    (out / ".done").touch()
    print(f"OK en el teléfono: {phone_dir}/{station} ({len(list(out.glob('*.mp3')))} archivos)", flush=True)
    return True


def main():
    while True:
        pending = 0
        for d, (_, _, mapping) in GAMES.items():
            for src_name in mapping:
                scan = ROOT / f"work/{d}/{src_name}.json"
                if not (ROOT / f"work/out-{d}/{mapping[src_name][0]}/.done").exists():
                    if scan.exists():
                        process(d, src_name, scan)
                    else:
                        pending += 1
        if not pending:
            print("PIPELINE_DONE", flush=True)
            return
        time.sleep(300)


if __name__ == "__main__":
    main()
