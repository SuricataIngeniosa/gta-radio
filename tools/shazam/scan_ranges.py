"""Como scan.py pero sólo en tramos dados (los huecos que el CD no cubre). Reanudable: agrega al .jsonl.
Uso: python scan_ranges.py <audio> <salida.jsonl> "a-b,c-d" [paso_s=10] [ventana_s=8]"""
import asyncio
import json
import subprocess
import sys
from pathlib import Path

from shazamio import Shazam

src, out, ranges = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
step = float(sys.argv[4]) if len(sys.argv) > 4 else 10
win = float(sys.argv[5]) if len(sys.argv) > 5 else 8
TMP = "/tmp/clip.wav"


def clip(t):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(t), "-t", str(win), "-i", str(src),
                    "-ac", "1", "-ar", "16000", TMP], check=True)
    return TMP


async def main():
    done = {json.loads(l)["t"] for l in out.open() if l.strip()} if out.exists() else set()
    sh = Shazam()
    with out.open("a") as f:
        for r in ranges.split(","):
            a, b = map(float, r.split("-"))
            t = round(a / step) * step
            while t < b:
                if t not in done:
                    res = {}
                    for attempt in range(4):
                        try:
                            res = await sh.recognize(clip(t)); break
                        except Exception as e:
                            print(f"t={t} error {e!r}, reintento", flush=True)
                            await asyncio.sleep(10 * (attempt + 1))
                    tr = res.get("track") or {}
                    rec = {"t": t, "title": tr.get("title"), "artist": tr.get("subtitle"), "key": tr.get("key")}
                    f.write(json.dumps(rec, ensure_ascii=False) + "\n"); f.flush()
                    print(f"{src.stem} {int(t // 60)}:{int(t % 60):02} {rec['artist']} - {rec['title']}", flush=True)
                    await asyncio.sleep(1.2)
                t += step


asyncio.run(main())
