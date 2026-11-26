"""Ubica canciones del CD dentro del audio del juego comparando espectros (robusto a otra mezcla/fase/códec)
y luego afina con la forma de onda. Mide además la deriva (si el juego corre a otra velocidad que el CD).

Uso: python locate.py JUEGO.mp3 SALIDA.json track1.flac track2.flac ...
Salida: por canción, puntos de anclaje [(seg_en_cd, seg_en_juego, correlación)] a lo largo del tema.
"""
import json
import os
import subprocess
import sys

import numpy as np
from scipy import fft, signal

SR = 8000
HOP = 80            # 10 ms
NFFT = 512
BANDS = 32


def decode(path, sr=SR):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(sr), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()


_edges = np.unique(np.geomspace(4, NFFT // 2, BANDS + 1).astype(int))


def features(x):
    """Log-energía en bandas logarítmicas cada 10 ms, derivada en el tiempo (onsets), normalizada por banda."""
    _, _, Z = signal.stft(x, nperseg=NFFT, noverlap=NFFT - HOP, boundary=None, padded=False)
    P = np.abs(Z) ** 2
    B = np.stack([P[a:b].sum(0) for a, b in zip(_edges[:-1], _edges[1:])])
    L = np.log(B + 1e-8)
    D = np.diff(L, axis=1, prepend=L[:, :1])
    D -= D.mean(1, keepdims=True)
    D /= D.std(1, keepdims=True) + 1e-6
    return D.astype(np.float32)          # (bandas, frames)


class Index:
    def __init__(self, F):
        self.F = F
        self.n = 1 << int(np.ceil(np.log2(F.shape[1] + 4000)))
        self.X = fft.rfft(F, self.n, axis=1, workers=-1)

    def find(self, P, lo=0, hi=None):
        """Mejor frame de inicio para el parche P (bandas, L) y su correlación media por banda."""
        L = P.shape[1]
        c = fft.irfft((self.X * np.conj(fft.rfft(P, self.n, axis=1, workers=-1))).sum(0), self.n, workers=-1)
        c = c[: self.F.shape[1] - L] / (P.shape[0] * L)
        hi = hi or len(c)
        k = lo + int(np.argmax(c[lo:hi]))
        return k, float(c[k])


def fine(game, cd, g_at, c_at, win=1.0, search=0.05):
    """Afina con la forma de onda (8 kHz): desplazamiento en s que maximiza la correlación cerca de g_at."""
    a = int((c_at) * SR); w = int(win * SR); s = int(search * SR)
    ref = cd[a:a + w]
    lo = int(g_at * SR) - s
    seg = game[max(0, lo): lo + w + 2 * s]
    if len(ref) < w or len(seg) < w + 2 * s:
        return g_at, 0.0
    c = signal.correlate(seg, ref, mode="valid")
    e = np.sqrt(np.convolve(seg ** 2, np.ones(w), "valid")) * np.linalg.norm(ref) + 1e-9
    n = c / e
    k = int(np.argmax(np.abs(n)))
    return (max(0, lo) + k) / SR, float(n[k])


def main():
    game_path, out, *tracks = sys.argv[1:]
    game = decode(game_path)
    idx = Index(features(game))
    fps = SR / HOP
    res = []
    for t in tracks:
        cd = decode(t)
        Fc = features(cd)
        dur = len(cd) / SR
        L = int(8 * fps)
        if Fc.shape[1] < 3 * L:
            res.append({"file": os.path.basename(t), "dur": dur, "anchors": []}); continue
        # Parches de 8 s cada ~20 s a lo largo del tema.
        starts = np.linspace(2 * fps, Fc.shape[1] - L - 2 * fps, max(3, int(dur // 20))).astype(int)
        raw = []
        for s0 in starts:
            k, c = idx.find(Fc[:, s0:s0 + L])
            raw.append((s0 / fps, k / fps, c))
        # Consenso: el desplazamiento (juego - cd) que más se repite.
        offs = np.array([g - c for c, g, _ in raw])
        best = max(offs, key=lambda o: np.sum(np.abs(offs - o) < 1.0))
        anchors = []
        for c_at, g_at, cc in raw:
            if abs((g_at - c_at) - best) < 1.0 and cc > 0.15:
                gf, nc = fine(game, cd, g_at + 2.0, c_at + 2.0)
                anchors.append((round(c_at + 2.0, 3), round(gf, 4), round(cc, 3), round(nc, 3)))
        res.append({"file": os.path.basename(t), "dur": round(dur, 2), "offset": round(float(best), 3), "anchors": anchors,
                    "votes": int(np.sum(np.abs(offs - best) < 1.0)), "probes": len(raw)})
        a = np.array(anchors) if anchors else None
        drift = ""
        if a is not None and len(a) >= 2:
            slope = np.polyfit(a[:, 0], a[:, 1], 1)[0]
            drift = f"velocidad {slope:.5f}"
        print(f"  {res[-1]['votes']:2d}/{len(raw):2d}  off {best:8.2f}  {drift:18s} {os.path.basename(t)}", flush=True)
    with open(out, "w") as f:
        json.dump(res, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
