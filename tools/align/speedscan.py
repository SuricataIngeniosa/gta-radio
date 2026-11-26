"""Busca un track del CD en el audio del juego probando velocidades (por si el juego usa otra transferencia)."""
import sys, numpy as np
sys.path.insert(0, "/tools")
from locate import decode, features, Index, SR, HOP
from scipy import signal
game_path, *tracks = sys.argv[1:]
idx = Index(features(decode(game_path))); fps = SR / HOP
for t in tracks:
    cd = decode(t); best = None
    for sp in np.arange(0.94, 1.0601, 0.005):
        y = signal.resample_poly(cd, 1000, int(round(1000 * sp))) if abs(sp - 1) > 1e-6 else cd
        F = features(y); L = int(8 * fps)
        starts = np.linspace(2 * fps, F.shape[1] - L - 2 * fps, 8).astype(int)
        hits = [(idx.find(F[:, s:s + L])[0] - s) / fps for s in starts]
        offs = np.array(hits); o = max(offs, key=lambda v: np.sum(np.abs(offs - v) < 1)); v = int(np.sum(np.abs(offs - o) < 1))
        if not best or v > best[0]: best = (v, sp, o)
    print(f"{t.split('/')[-1]}: mejor {best[0]}/8 a velocidad {best[1]:.3f} (cd más {'lento' if best[1]>1 else 'rápido'}), off {best[2]:.1f}", flush=True)
