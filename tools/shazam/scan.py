"""Recorre una emisora en ventanas y pregunta a Shazam qué suena. Reanudable: guarda cada resultado.
Uso: python scan.py <archivo.mp3> <salida.jsonl> [paso_s=10] [ventana_s=8]"""
import asyncio, json, subprocess, sys
from pathlib import Path
from shazamio import Shazam

src, out = Path(sys.argv[1]), Path(sys.argv[2])
step = float(sys.argv[3]) if len(sys.argv) > 3 else 10
win = float(sys.argv[4]) if len(sys.argv) > 4 else 8

def duration(p):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(p)],
                       capture_output=True, text=True, check=True)
    return float(r.stdout)

TMP = "/tmp/clip.wav"

def clip(t):
    # A archivo, no a pipe: el WAV por pipe queda sin largo en la cabecera y el decodificador falla.
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(t), "-t", str(win), "-i", str(src),
                    "-ac", "1", "-ar", "16000", TMP], check=True)
    return TMP

async def main():
    done = set()
    if out.exists():
        done = {json.loads(l)["t"] for l in out.open() if l.strip()}
    total, sh = duration(src), Shazam()
    t = 0.0
    with out.open("a") as f:
        while t < total - 2:
            if t not in done:
                for attempt in range(4):
                    try:
                        r = await sh.recognize(clip(t))
                        break
                    except Exception as e:
                        print(f"t={t} error {e!r}, reintento", flush=True)
                        await asyncio.sleep(10 * (attempt + 1))
                else:
                    r = {}
                tr = r.get("track") or {}
                rec = {"t": t, "title": tr.get("title"), "artist": tr.get("subtitle"), "key": tr.get("key")}
                f.write(json.dumps(rec, ensure_ascii=False) + "\n"); f.flush()
                print(f"{src.stem} {int(t//60)}:{int(t%60):02} {rec['artist']} - {rec['title']}", flush=True)
                await asyncio.sleep(1.2)
            t += step

asyncio.run(main())
