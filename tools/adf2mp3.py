#!/usr/bin/env python3
"""GTA Vice City (PC) .adf -> .mp3: el ADF es un MP3 con cada byte XOR 0x22 (34).
Uso: python3 adf2mp3.py <carpeta Audio del juego> [carpeta salida]"""
import sys
from pathlib import Path

src, dst = Path(sys.argv[1]), Path(sys.argv[2] if len(sys.argv) > 2 else ".")
dst.mkdir(parents=True, exist_ok=True)
table = bytes(b ^ 0x22 for b in range(256))
for adf in sorted(p for p in src.iterdir() if p.suffix.lower() == ".adf"):
    out = dst / (adf.stem.lower() + ".mp3")
    with adf.open("rb") as f, out.open("wb") as g:
        while chunk := f.read(1 << 20):
            g.write(chunk.translate(table))
    print(f"{adf.name} -> {out}")
