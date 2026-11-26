#!/usr/bin/env python3
"""Renombra los íconos de radio de GTA III (images/III/logos/fe_radioN.png, 256x256) al nombre de su audio
(HEAD.png, ...) recortando el margen transparente, para que la app los tome de la carpeta de audios.
La numeración fe_radioN es la de los archivos, no el orden del dial; mp3logo.png (MP3 Player) no es una emisora."""
from pathlib import Path
from PIL import Image

d = Path(__file__).resolve().parent.parent / "images" / "III" / "logos"
NAMES = {1: "HEAD", 2: "CLASS", 3: "GAME", 4: "MSX", 5: "KJAH", 6: "FLASH", 7: "RISE", 8: "LIPS", 9: "CHAT"}
for n, name in NAMES.items():
    im = Image.open(d / f"fe_radio{n}.png").convert("RGBA")
    logo = im.crop(im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox())
    logo.save(d / f"{name}.png")
    print(f"fe_radio{n}.png -> {name}.png {logo.size}")
