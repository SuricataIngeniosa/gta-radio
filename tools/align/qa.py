"""Control de cortes: alrededor de cada empalme la salida tiene que sonar como el juego (espectro) y sin saltos de nivel."""
import json, sys, numpy as np
sys.path.insert(0, "/tools")
from locate import decode, features, SR, HOP
game_path, out_base = sys.argv[1:]
g = decode(game_path); o = decode(out_base + ".flac"); d = json.load(open(out_base + ".json"))
FPS = SR / HOP
def win(x, t, a=-3, b=3): return x[max(0, int((t + a) * SR)): int((t + b) * SR)]
def sim(x, y):
    n = min(len(x), len(y)); fx, fy = features(x[:n]), features(y[:n])
    best = max(float(np.mean(fx[:, max(0, k):fx.shape[1] + min(0, k)] * fy[:, max(0, -k):fy.shape[1] - max(0, k)])) for k in range(-5, 6))
    return best
def rms(x): return 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-9)
for it in d["items"]:
    if "o_in" not in it: continue
    line = f"{it['title'][:34]:34s}"
    for side in ("in", "out"):
        ot, gt = it["o_" + side], it["g_" + side]
        s = sim(win(o, ot), win(g, gt))
        # salto de nivel justo en el corte (0,3 s antes vs 0,3 s después) comparado con el mismo punto del juego
        jo = rms(win(o, ot, 0, .3)) - rms(win(o, ot, -.3, 0)); jg = rms(win(g, gt, 0, .3)) - rms(win(g, gt, -.3, 0))
        line += f"  {side}: parecido {s:.2f} salto {jo - jg:+5.1f} dB"
    print(line, f" gan {it.get('gain')}")
