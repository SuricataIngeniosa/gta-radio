"""Emisora de Vice City fiel al juego con las canciones del CD en FLAC.

Base: el audio del juego (orden, DJ, tandas y temas que el CD no trae). Cada canción o tanda que también está en
el CD se reemplaza por la del FLAC, llevada a la velocidad del juego (algunas transferencias difieren hasta un 3 %),
con el mismo volumen y polaridad. El corte se hace donde juego y CD suenan igual: si el DJ habla sobre la intro o
el final, esa parte queda del juego. Fundido de 25 ms en cada corte.

Uso: python hybrid.py JUEGO.mp3 SALIDA_SIN_EXT "Locutor" track1.flac ...
Escribe SALIDA.flac, SALIDA.json (tramos, cortes y huecos sin identificar) y un log en stdout.
"""
import json
import os
import subprocess
import sys

import mutagen
import numpy as np
from scipy import signal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from locate import HOP, SR, Index, features  # noqa: E402

HI = 44100
FADE = int(0.025 * HI)
FPS = SR / HOP


def decode(path, sr, ch, dtype="f32le", np_dtype=np.float32):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", str(ch), "-ar", str(sr), "-f", dtype, "-"],
                         check=True, capture_output=True).stdout
    a = np.frombuffer(raw, dtype=np_dtype).copy()
    return a.reshape(-1, ch) if ch > 1 else a


def meta(path):
    m = mutagen.File(path)
    t = m.tags or {}
    artist, title = t.get("artist", [""])[0], t.get("title", [""])[0]
    if (artist, title) == ("Cybotron", "Clean"):
        title = "Clear"
    if artist == "Vice City":
        kind = "AD" if title.startswith("Commercial") else "DJ"
    else:
        kind = "SONG"
    return artist, title, kind


def votes_at(idx, F, n=8):
    L = int(8 * FPS)
    if F.shape[1] < L + 4 * FPS:
        return 0, 0.0, []
    starts = np.linspace(2 * FPS, F.shape[1] - L - 2 * FPS, n).astype(int)
    hits = [(s / FPS, idx.find(F[:, s:s + L])[0] / FPS) for s in starts]
    offs = np.array([g - c for c, g in hits])
    o = max(offs, key=lambda v: np.sum(np.abs(offs - v) < 0.5))
    good = [(c, g) for c, g in hits if abs(g - c - o) < 0.5]
    return len(good), float(o), good


def speed_of(cd8, idx):
    """Velocidad del juego respecto del CD (game_dt / cd_dt) y anclas [(cd_t, game_t)]."""
    v, o, good = votes_at(idx, features(cd8), 12)
    if v >= 6:
        a = np.array(good)
        s = np.polyfit(a[:, 0], a[:, 1], 1)[0] if len(a) >= 3 else 1.0
        return float(s), v, 12
    best = (0, 1.0)
    for sp in np.arange(0.94, 1.0601, 0.005):
        y = signal.resample_poly(cd8, int(round(sp * 2000)), 2000)
        vv, _, _ = votes_at(idx, features(y), 8)
        if vv > best[0]:
            best = (vv, sp)
    if best[0] < 5:
        return None, best[0], 8
    # Refinar la pendiente con anclas a esa velocidad.
    y = signal.resample_poly(cd8, int(round(best[1] * 2000)), 2000)
    vv, o, good = votes_at(idx, features(y), 12)
    a = np.array(good)
    s2 = np.polyfit(a[:, 0], a[:, 1], 1)[0] if len(a) >= 3 else 1.0
    return float(best[1] * s2), vv, 12


def xalign(game8, ref8, g_pred, search):
    """Ubica ref8 dentro de game8 cerca de g_pred (s). Devuelve (t_inicio, correlación con signo)."""
    w = len(ref8)
    lo = max(0, int((g_pred - search) * SR))
    seg = game8[lo: lo + w + int(2 * search * SR)]
    if len(seg) <= w:
        return g_pred, 0.0
    c = signal.correlate(seg, ref8, mode="valid", method="fft")
    e = np.sqrt(np.maximum(np.convolve(seg.astype(np.float64) ** 2, np.ones(w), "valid"), 1e-12)) * np.linalg.norm(ref8) + 1e-9
    n = c / e
    k = int(np.argmax(np.abs(n)))
    return (lo + k) / SR, float(n[k])


def local_sim(Fc, Fg, c, g, half=2.0):
    """Parecido espectral entre el CD alrededor de c y el juego alrededor de g (busca ±50 ms)."""
    i0, j0, n = int((c - half) * FPS), int((g - half) * FPS), int(2 * half * FPS)
    if i0 < 0 or j0 - 5 < 0 or i0 + n > Fc.shape[1] or j0 + n + 5 > Fg.shape[1]:
        return 0.0
    a = Fc[:, i0:i0 + n].ravel()
    return max(float(np.corrcoef(a, Fg[:, j0 + k:j0 + k + n].ravel())[0, 1]) for k in range(-5, 6))


def main():
    game_path, out_base, host, *tracks = sys.argv[1:]
    game = decode(game_path, HI, 2, "s16le", np.int16)
    game8 = decode(game_path, SR, 1)
    Fg = features(game8)
    idx = Index(Fg)
    total = len(game) / HI
    items = []
    for t in tracks:
        artist, title, kind = meta(t)
        if kind == "DJ":
            continue
        cd8 = decode(t, SR, 1)
        s, v, n = speed_of(cd8, idx)
        name = os.path.basename(t)
        if s is None:
            print(f"  --  no está en el juego ({v}/{n}): {name}", flush=True)
            continue
        cdp8 = signal.resample_poly(cd8, int(round(s * 20000)), 20000)
        # Ubicación gruesa a la velocidad del juego (mediana de varios parches).
        _, o, _ = votes_at(idx, features(cdp8), 12)
        dur = len(cdp8) / SR
        # Curva de parecido por ventanas de 1 s: el DJ hablando encima la baja.
        Fc = features(cdp8)
        W = int(FPS)
        # Cada ventana busca su mejor desfase (±0,6 s, siguiendo al anterior): tolera deriva despareja (Billie Jean).
        sims, lags, lag, LAG = [], [], 0, int(0.6 * FPS)
        for i in range(0, Fc.shape[1] - W, W):
            g0 = int(round((o + i / FPS) * FPS)) + lag
            lo, hi = g0 - 15, g0 + W + 15
            if i == 0:
                lo, hi = g0 - LAG, g0 + W + LAG
            if lo < 0 or hi > Fg.shape[1]:
                sims.append(-1.0); lags.append(lag); continue
            a = Fc[:, i:i + W]
            seg = Fg[:, lo:hi]
            best, bk = -1.0, 0
            for k in range(0, seg.shape[1] - W + 1):
                c = float(np.mean(a * seg[:, k:k + W]))
                if c > best:
                    best, bk = c, k
            sims.append(best)
            if best > 0.3:
                lag += (lo + bk) - g0
            lags.append(lag)
        sims = np.array(sims)
        valid = sims[sims > -1]
        thr = max(0.2, 0.6 * np.median(valid)) if len(valid) else 1
        ok = sims >= thr
        run = 4
        first = next((i for i in range(len(ok) - run) if ok[i:i + run].all()), None)
        last = next((i for i in range(len(ok) - 1, run - 1, -1) if ok[i - run + 1:i + 1].all()), None)
        if first is None or last is None or last - first < 10:
            print(f"  --  sin tramo limpio: {name}", flush=True)
            continue
        c_in, c_out = first + 0.5, last + 0.5            # segundos en el CD (a velocidad del juego)
        # Afinar los dos cortes con la forma de onda (ventana 1 s, búsqueda ±0,4 s).
        def cut(c, li, back):
            """Corte en el segundo c del CD: posición en el juego y si ahí suenan igual (±2 s alrededor)."""
            p = o + c - back + lags[min(li, len(lags) - 1)] / FPS
            g, r = xalign(game8, cdp8[int((c - back) * SR): int((c - back + 1) * SR)], p, 0.15)
            if abs(r) < 0.4:  # forma de onda poco clara (otra fase o mezcla): posición del espectro (10 ms)
                g = p
            g += back
            sim = local_sim(Fc, Fg, c, g)
            return g, r, sim
        # Si en el corte no suenan casi igual, probar 5, 10... 30 s hacia adentro: el primero que coincide, o el mejor.
        def pick(c0, step, back):
            best = None
            for k in range(7):
                c = c0 + k * step
                if not (first + 0.5 <= c <= last + 0.5):
                    break
                g, r, sm = cut(c, int(c), back)
                if best is None or sm > best[3]:
                    best = (c, g, r, sm)
                if sm >= 0.88:
                    break
            return best
        c_in, g_in, r_in, s_in = pick(c_in, 5, 0.0)
        c_out, g_out, r_out, s_out = pick(c_out, -5, 1.0)
        if min(s_in, s_out) < 0.78:
            print(f"  ~~  cortes dudosos ({s_in:.2f} {s_out:.2f}), queda la del juego: {name}", flush=True)
            continue
        if c_out - c_in < 20:
            print(f"  --  sin cortes limpios: {name}", flush=True)
            continue
        pol = 1.0 if (r_in if abs(r_in) >= abs(r_out) else r_out) >= 0 else -1.0
        items.append(dict(file=name, artist=artist, title=title, kind=kind, speed=round(s, 5), votes=v,
                          c_in=c_in, c_out=c_out, g_in=g_in, g_out=g_out, r_in=r_in, r_out=r_out, pol=pol,
                          g_start=o, g_end=o + dur, path=t))
        print(f"  OK  {name[:52]:52s} vel {s:.4f}  juego {o/60:5.1f}-{(o+dur)/60:5.1f} min  limpio {c_in:5.1f}-{c_out:5.1f}/{dur:5.1f} s"
              f"  r {r_in:+.2f} {r_out:+.2f}  parecido {s_in:.2f} {s_out:.2f}", flush=True)

    items.sort(key=lambda d: d["g_in"])
    kept, last_out = [], -1
    for d in items:
        if d["g_in"] < last_out + 0.5:
            print(f"  !! se pisa con el anterior, queda del juego: {d['file']}")
            continue
        kept.append(d); last_out = d["g_out"]

    # Armar la salida: juego | CD | juego | CD ... con fundidos.
    enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(HI), "-ac", "2", "-i", "-",
                            "-c:a", "flac", "-compression_level", "8", out_base + ".flac"], stdin=subprocess.PIPE)
    out_pos = 0          # muestras escritas
    gmap = []            # (game_t, out_t) puntos para mapear tiempos del juego a la salida
    prev_tail = None     # últimas FADE muestras pendientes (float) para el fundido

    def emit(x):
        nonlocal out_pos, prev_tail
        x = x.astype(np.float32)
        if prev_tail is not None:
            ramp = np.linspace(0, 1, FADE, dtype=np.float32)[:, None]
            x[:FADE] = prev_tail * (1 - ramp) + x[:FADE] * ramp
        body, prev_tail = x[:-FADE], x[-FADE:]
        enc.stdin.write(np.clip(body, -32768, 32767).astype(np.int16).tobytes())
        out_pos += len(body)

    gpos = 0
    for d in kept:
        a = int(d["g_in"] * HI)
        gmap.append((gpos / HI, out_pos / HI))
        emit(game[gpos:a + FADE])
        cd = decode(d["path"], HI, 2)
        cdp = signal.resample_poly(cd, int(round(d["speed"] * 20000)), 20000, axis=0)
        ci, co = int(d["c_in"] * HI), int(d["c_out"] * HI)
        seg = cdp[ci:co + FADE]
        # Volumen igualado en cada punta (3 s junto al corte) y rampa entre las dos: sin saltos en los cortes.
        def ratio(g0, g1, c0, c1):
            rg = np.sqrt(np.mean(game[g0:g1].astype(np.float32) ** 2) + 1e-9)
            rc = np.sqrt(np.mean((cdp[c0:c1] * 32768) ** 2) + 1e-9)
            return rg / rc
        w3 = 3 * HI
        b = int(d["g_out"] * HI)
        k_in, k_out = ratio(a, a + w3, ci, ci + w3), ratio(b - w3, b, co - w3, co)
        ramp = np.linspace(k_in, k_out, len(seg), dtype=np.float32)[:, None] * d["pol"]
        d["gain"] = [round(float(k_in), 3), round(float(k_out), 3)]
        d["o_in"] = out_pos / HI
        emit(seg * 32768 * ramp)
        d["o_out"] = out_pos / HI
        gpos = int(d["g_out"] * HI)
    gmap.append((gpos / HI, out_pos / HI))
    emit(np.concatenate([game[gpos:], np.zeros((FADE, 2), np.int16)]))
    enc.stdin.close(); enc.wait()
    out_total = out_pos / HI

    def to_out(gt):
        """Tiempo del juego -> tiempo en la salida (fuera de los tramos reemplazados es un corrimiento)."""
        for d in kept:
            if d["g_in"] <= gt <= d["g_out"]:
                return d["o_in"] + (gt - d["g_in"]) * (d["o_out"] - d["o_in"]) / max(1e-6, d["g_out"] - d["g_in"])
        base = max((p for p in gmap if p[0] <= gt), default=(0.0, 0.0))
        return base[1] + (gt - base[0])

    segs = []
    for d in items:
        segs.append(dict(kind=d["kind"], artist=d["artist"], title=d["title"],
                         start=round(to_out(max(0.0, d["g_start"])), 2), end=round(to_out(min(total, d["g_end"])), 2),
                         replaced=d in kept))
    segs.sort(key=lambda x: x["start"])
    gaps, cur = [], 0.0
    for sgm in segs:
        if sgm["start"] - cur > 60:
            gaps.append([round(cur, 1), round(sgm["start"], 1)])
        cur = max(cur, sgm["end"])
    if out_total - cur > 60:
        gaps.append([round(cur, 1), round(out_total, 1)])
    clean = [{k: v for k, v in d.items() if k != "path"} for d in items]
    with open(out_base + ".json", "w") as f:
        json.dump(dict(host=host, duration=round(out_total, 2), game_duration=round(total, 2), items=clean,
                       segments=segs, gaps=gaps), f, ensure_ascii=False, indent=1)
    print(f"{os.path.basename(out_base)}: {len(kept)} tramos del CD, {out_total/60:.1f} min (juego {total/60:.1f}); huecos >60 s: {gaps}")


if __name__ == "__main__":
    main()
