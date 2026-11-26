#!/usr/bin/env python3
"""Corta la lámina 3x3 de logos (images/VC/VC-radio-logos.png) en un PNG por emisora.

Algunos logos se pasan del borde de su celda (el "98.3" de Emotion), así que no se corta por celdas fijas:
se buscan las manchas opacas de la lámina, cada una se asigna a la celda donde cae su centro, y cada logo es
el recuadro que encierra sus manchas (las de menos de MIN_PX píxeles son motas sueltas y se ignoran). Los nombres coinciden con los .mp3 para que la app los tome solos.
"""
from collections import deque
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parent.parent / "images" / "VC"
sheet = Image.open(root / "VC-radio-logos.png").convert("RGBA")
MIN_PX = 40
grid = [["WILD", "FLASH", "KCHAT"], ["FEVER", "VROCK", "VCPR"], ["ESPANT", "EMOTION", "WAVE"]]
W, H = sheet.size
alpha = sheet.getchannel("A").load()
seen = [[False] * W for _ in range(H)]
owner = [[None] * W for _ in range(H)]  # celda a la que pertenece cada píxel opaco
boxes = {}  # (fila, col) -> [x0, y0, x1, y1]

for y in range(H):
    for x in range(W):
        if seen[y][x] or alpha[x, y] <= 16:
            continue
        q, pts = deque([(x, y)]), []
        seen[y][x] = True
        while q:
            px, py = q.popleft()
            pts.append((px, py))
            for nx, ny in ((px + 1, py), (px - 1, py), (px, py + 1), (px, py - 1)):
                if 0 <= nx < W and 0 <= ny < H and not seen[ny][nx] and alpha[nx, ny] > 16:
                    seen[ny][nx] = True
                    q.append((nx, ny))
        if len(pts) < MIN_PX:
            continue
        cx = sum(p[0] for p in pts) / len(pts)
        cy = sum(p[1] for p in pts) / len(pts)
        cell = (min(2, int(cy * 3 / H)), min(2, int(cx * 3 / W)))
        for px, py in pts:
            owner[py][px] = cell
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        b = boxes.setdefault(cell, [W, H, 0, 0])
        b[0], b[1] = min(b[0], min(xs)), min(b[1], min(ys))
        b[2], b[3] = max(b[2], max(xs) + 1), max(b[3], max(ys) + 1)

(root / "logos").mkdir(exist_ok=True)
for r, row in enumerate(grid):
    for c, name in enumerate(row):
        x0, y0, x1, y1 = boxes[(r, c)]
        x0, y0, x1, y1 = max(0, x0 - 2), max(0, y0 - 2), min(W, x1 + 2), min(H, y1 + 2)  # bordes suavizados
        logo = Image.new("RGBA", (x1 - x0, y1 - y0))
        # Copiar sólo los píxeles de manchas de esta celda (no los de un vecino que invada el recuadro).
        src = sheet.crop((x0, y0, x1, y1))
        mask = Image.new("L", src.size, 0)
        mp = mask.load()
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                o = owner[yy][xx]
                # opacos: sólo los de esta celda; bordes casi transparentes: los que caen dentro de la celda
                if o == (r, c) or (o is None and alpha[xx, yy] > 0
                                   and (min(2, int(yy * 3 / H)), min(2, int(xx * 3 / W))) == (r, c)):
                    mp[xx - x0, yy - y0] = 255
        logo.paste(src, (0, 0), mask)
        logo.save(root / "logos" / f"{name}.png")
        print(name, logo.size)
