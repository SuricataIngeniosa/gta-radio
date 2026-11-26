#!/usr/bin/env python3
"""Ícono de la app (adaptativo): fondo de atardecer (violeta noche -> fucsia -> naranja: Vice City, Leonida, Los Santos) y
encima una radio de auto clásica en blanco (parlante, dial, perillas y antena).
Salida: res/drawable-nodpi/ic_app_bg.png, ic_app_fg.png, ic_app_mono.png, res/mipmap-anydpi-v26/ic_launcher.xml
(la app pide Android 8+, así que alcanza con el adaptativo)."""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

res = Path(__file__).resolve().parent.parent / "android/app/src/main/res"
S = 432; SS = 4                       # se dibuja a 4x y se achica: bordes suaves
def big(): return Image.new("RGBA", (S * SS, S * SS))

def background():
    im = Image.new("RGB", (S, S)); d = ImageDraw.Draw(im)
    stops = [(0.0, (26, 11, 46)), (0.45, (95, 22, 96)), (0.72, (190, 52, 112)), (1.0, (255, 150, 80))]
    for y in range(S):
        t = y / (S - 1)
        for (a, c0), (b, c1) in zip(stops, stops[1:]):
            if a <= t <= b:
                k = (t - a) / (b - a); d.line([(0, y), (S, y)], fill=tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3)))
    # sol bajo: un resplandor cálido abajo al centro
    glow = Image.new("L", (S, S)); ImageDraw.Draw(glow).ellipse((S * 0.15, S * 0.62, S * 0.85, S * 1.32), fill=150)
    im.paste(Image.new("RGB", (S, S), (255, 200, 120)), mask=glow.filter(ImageFilter.GaussianBlur(40)))
    return im

def foreground(color=(255, 255, 255, 255)):
    im = big(); d = ImageDraw.Draw(im); c = S * SS / 2; u = SS
    # radio de auto clásica: cuerpo redondeado, parlante a la izquierda, dial a la derecha, antena
    k = 1.6; u = SS * k                                            # la radio sola, ocupando la zona segura
    w, h = 132 * u, 88 * u; x0, y0 = c - w / 2, c - h / 2 + 12 * SS     # un poco abajo: la antena equilibra arriba
    d.line((x0 + w - 26 * u, y0 + 4 * u, x0 + 30 * u, y0 - 22 * u), fill=color, width=int(8 * u))    # antena hacia arriba a la izq.
    d.ellipse((x0 + 22 * u, y0 - 30 * u, x0 + 38 * u, y0 - 14 * u), fill=color)
    d.rounded_rectangle((x0, y0, x0 + w, y0 + h), radius=16 * u, fill=color)
    hole = (0, 0, 0, 0)
    sp = (x0 + 14 * u, y0 + 14 * u, x0 + 14 * u + 60 * u, y0 + 14 * u + 60 * u)                       # parlante
    d.ellipse(sp, fill=hole); d.ellipse((sp[0] + 15 * u, sp[1] + 15 * u, sp[2] - 15 * u, sp[3] - 15 * u), fill=color)
    d.ellipse((sp[0] + 24 * u, sp[1] + 24 * u, sp[2] - 24 * u, sp[3] - 24 * u), fill=hole)
    d.rounded_rectangle((x0 + 86 * u, y0 + 16 * u, x0 + w - 12 * u, y0 + 36 * u), radius=5 * u, fill=hole)  # dial
    d.rectangle((x0 + 100 * u, y0 + 18 * u, x0 + 104 * u, y0 + 34 * u), fill=color)                     # aguja
    for k in range(2):                                                                                   # perillas
        cx = x0 + 94 * u + k * 22 * u
        d.ellipse((cx - 8 * u, y0 + 52 * u, cx + 8 * u, y0 + 68 * u), fill=hole)
    return im.resize((S, S), Image.LANCZOS)

(res / "drawable-nodpi").mkdir(exist_ok=True); (res / "mipmap-anydpi-v26").mkdir(exist_ok=True)
bg = background(); bg.save(res / "drawable-nodpi/ic_app_bg.png")
fg = foreground(); fg.save(res / "drawable-nodpi/ic_app_fg.png")
foreground().save(res / "drawable-nodpi/ic_app_mono.png")
(res / "mipmap-anydpi-v26/ic_launcher.xml").write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@drawable/ic_app_bg" />\n    <foreground android:drawable="@drawable/ic_app_fg" />\n'
    '    <monochrome android:drawable="@drawable/ic_app_mono" />\n</adaptive-icon>\n')
# vista previa: círculo (Pixel) y squircle (Samsung)
prev = Image.new("RGB", (460, 230), (40, 40, 40))
for i, shape in enumerate(("circle", "squircle")):
    t = bg.convert("RGBA"); t.alpha_composite(fg); mm = Image.new("L", (S, S)); dd = ImageDraw.Draw(mm)
    (dd.ellipse((0, 0, S - 1, S - 1), fill=255) if shape == "circle" else dd.rounded_rectangle((0, 0, S - 1, S - 1), radius=S * 0.3, fill=255))
    t.putalpha(mm); t = t.resize((210, 210), Image.LANCZOS); prev.paste(t, (10 + i * 230, 10), t)
prev.save("/tmp/claude-1000/-home-bis/5e3ff4ab-19b6-415e-9477-c8ef936e9c4e/scratchpad/app_icon.png")
print("ok")
