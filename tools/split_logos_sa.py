#!/usr/bin/env python3
"""Corta la lámina de logos de San Andreas (images/SA/GTASARadiosLogos.png, 4x3 sobre negro) en un PNG por emisora
con fondo transparente, para que floten sobre el wallpaper.

El fondo se saca rellenando desde el borde de cada celda los píxeles casi negros (así el negro DENTRO del logo, como
las letras de Radio X, se conserva). El borde del logo se suaviza con un alfa según el brillo. Nombres = archivos de la app.
"""
from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter

root = Path(__file__).resolve().parent.parent / "images" / "SA"
out = root / "logos"
out.mkdir(exist_ok=True)
sheet = Image.open(root / "GTASARadiosLogos.png").convert("RGB")
grid = [["PLAYBACK", "KROSE", "KDST", "BOUNCE"], ["SFUR", "RLS", "RADIOX", "CSR"], ["KJAH", "MASTER", "WCTR", None]]
BG = 45      # brillo máximo de un píxel de fondo
# Radio de cierre (px de la lámina) para conservar el negro interno de cada logo; 0 = sólo el relleno desde el borde.
CLOSE = {"KDST": 14, "BOUNCE": 16, "SFUR": 7, "RADIOX": 12, "CSR": 40}
W, H = sheet.size
cw, ch = W // 4, H // 3

for r, row in enumerate(grid):
    for c, name in enumerate(row):
        if not name:
            continue  # "Track Player" no es una emisora
        cell = sheet.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch))
        px = cell.load()
        w, h = cell.size
        bright = [[max(px[x, y]) for x in range(w)] for y in range(h)]
        bg = [[False] * w for _ in range(h)]
        q = deque((x, y) for x in range(w) for y in (0, h - 1)) + deque((x, y) for y in range(h) for x in (0, w - 1))
        while q:
            x, y = q.popleft()
            if bg[y][x] or bright[y][x] > BG:
                continue
            bg[y][x] = True
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h and not bg[ny][nx]:
                    q.append((nx, ny))
        # Motas sueltas (manchas de menos de 0,2 % de la celda, separadas del logo) también son fondo.
        seen = [[False] * w for _ in range(h)]
        for y0 in range(h):
            for x0 in range(w):
                if bg[y0][x0] or seen[y0][x0]:
                    continue
                comp, q2 = [], deque([(x0, y0)])
                seen[y0][x0] = True
                while q2:
                    x, y = q2.popleft(); comp.append((x, y))
                    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                        if 0 <= nx < w and 0 <= ny < h and not bg[ny][nx] and not seen[ny][nx]:
                            seen[ny][nx] = True; q2.append((nx, ny))
                if len(comp) < w * h * 0.002:
                    for x, y in comp:
                        bg[y][x] = True
        # Negro que es parte del logo (no fondo): en algunos logos el negro interno toca el fondo (la cola de K-DST, el palo
        # de la "b" de Bounce, los edificios de CSR, los huecos entre cables de SF-UR y ondas de Radio X). Se cierra la
        # silueta (dilatar y erosionar CLOSE[name] px) y se rellenan sus agujeros: el negro que queda adentro se conserva.
        rad = CLOSE.get(name, 0)
        if rad:
            fg = Image.new("L", (w, h))
            fg.putdata([0 if bg[y][x] else 255 for y in range(h) for x in range(w)])
            closed = fg
            for _ in range(rad):
                closed = closed.filter(ImageFilter.MaxFilter(3))
            for _ in range(rad):
                closed = closed.filter(ImageFilter.MinFilter(3))
            cp = closed.load()
            out_ = [[False] * w for _ in range(h)]
            q3 = deque((x, y) for x in range(w) for y in (0, h - 1) if cp[x, y] < 128)
            q3 += deque((x, y) for y in range(h) for x in (0, w - 1) if cp[x, y] < 128)
            while q3:
                x, y = q3.popleft()
                if out_[y][x] or cp[x, y] >= 128:
                    continue
                out_[y][x] = True
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < w and 0 <= ny < h and not out_[ny][nx]:
                        q3.append((nx, ny))
            for y in range(h):
                for x in range(w):
                    if not out_[y][x]:
                        bg[y][x] = False
        # CSR: los edificios son siluetas negras contra el cielo fucsia y los de las puntas dan al fondo: se rellena de negro
        # todo el ancho del cielo, desde el cielo hasta las letras (como se ve en el original).
        if name == "CSR":
            # Cielo: fucsia, también el oscuro de arriba del degradé (rojo y azul altos respecto del verde).
            mag = [(x, y) for y in range(int(h * 0.5)) for x in range(w)
                   if px[x, y][0] > 50 and px[x, y][0] > 2.5 * px[x, y][1] and px[x, y][2] > 1.5 * px[x, y][1]]
            bx0, bx1 = min(x for x, _ in mag), max(x for x, _ in mag)
            by0 = min(y for _, y in mag)
            # Donde empiezan las letras: la primera fila con mucho blanco (las ventanas son puntos sueltos).
            letters = next(y for y in range(by0, h) if sum(min(px[x, y]) > 200 for x in range(bx0, bx1 + 1)) > (bx1 - bx0) * 0.2)
            # Hasta los números "103:9": el negro baja entre las letras hasta chocar con el "1" y la pata de la R.
            digits = next(y for y in range(letters, h) if all(min(px[x, y]) < 200 for x in range(bx0, bx1 + 1))
                          and any(px[x, y][0] > 150 and px[x, y][1] < 80 for x in range(bx0, bx1 + 1)))
            for y in range(by0, digits):
                for x in range(bx0, bx1 + 1):
                    bg[y][x] = False
        # SF-UR: el hueco del centro entre la S, la F y la U se rellena de negro (sólo dentro de las letras celestes).
        if name == "SFUR":
            blue = [(x, y) for y in range(h) for x in range(w) if px[x, y][2] > 150 and px[x, y][0] < 90]
            lx0, lx1 = min(x for x, _ in blue), max(x for x, _ in blue)
            ly0, ly1 = min(y for _, y in blue), max(y for _, y in blue)
            cx0, cx1 = lx0 + (lx1 - lx0) * 0.05, lx1
            cy0, cy1 = ly0 + (ly1 - ly0) * 0.25, ly0 + (ly1 - ly0) * 0.75
            for y in range(int(cy0), int(cy1)):
                for x in range(int(cx0), int(cx1)):
                    bg[y][x] = False
        rgba = Image.new("RGBA", (w, h))
        o = rgba.load()
        for y in range(h):
            for x in range(w):
                if bg[y][x]:
                    o[x, y] = (0, 0, 0, 0)
                else:
                    # Borde: los píxeles oscuros que tocan el fondo se vuelven semitransparentes (antialias).
                    edge = any(0 <= x + dx < w and 0 <= y + dy < h and bg[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    a = 255 if not edge else min(255, int(bright[y][x] * 255 / 90))
                    o[x, y] = (*px[x, y], a)
        bbox = rgba.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
        logo = rgba.crop(bbox)
        # Contorno negro grueso (como el trazo de la ilustración de SA): el alfa dilatado, en negro, debajo del logo.
        pad = 16
        canvas = Image.new("RGBA", (logo.width + 2 * pad, logo.height + 2 * pad))
        canvas.paste(logo, (pad, pad))
        ring = canvas.getchannel("A").point(lambda v: 255 if v > 60 else 0).filter(ImageFilter.MaxFilter(19)).filter(ImageFilter.GaussianBlur(1.2))
        stroke = Image.new("RGBA", canvas.size, (12, 10, 8, 255))
        stroke.putalpha(ring)
        logo = Image.alpha_composite(stroke, canvas)
        logo.save(out / f"{name}.png")
        print(name, logo.size)
