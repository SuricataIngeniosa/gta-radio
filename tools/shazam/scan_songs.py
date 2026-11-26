"""Arma la lista de canciones de una emisora compilada (todo en un mp3) con Shazam.

1. Pregunta cada STEP s. Shazam devuelve en qué segundo de la canción cae la muestra (matches[].offset), así que el
   inicio de cada tema sale de una sola consulta: inicio = t - offset (se toma la mediana entre consultas).
2. El final se busca entre la última consulta que reconoce el tema y la primera que no, partiendo el intervalo (±5 s).
Todo lo que no es canción queda como charla (la app muestra radio + locutor).

Reanudable: cada consulta se guarda en <salida>.q.jsonl. Uso: python scan_songs.py audio.mp3 salida.json [step=60]
"""
import asyncio
import json
import statistics
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path

from aiohttp_retry import ExponentialRetry
from shazamio import Shazam
from shazamio.client import HTTPClient

src, out = Path(sys.argv[1]), Path(sys.argv[2])
STEP = float(sys.argv[3]) if len(sys.argv) > 3 else 60
WIN = 8.0
qpath = out.with_suffix(".q.jsonl")
cache = {}
if qpath.exists():
    for l in qpath.open():
        l = l.strip("\x00 \t\r\n")  # un corte de luz puede dejar la última línea con bytes nulos
        if not l:
            continue
        try:
            r = json.loads(l)
        except json.JSONDecodeError:
            continue
        cache[r["t"]] = r
# Sin los reintentos internos de shazamio (hasta 20, en silencio y sin coordinar entre escaneos): los maneja el turnero.
sh = Shazam(http_client=HTTPClient(retry_options=ExponentialRetry(attempts=1)))


def duration():
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(src)],
                       capture_output=True, text=True, check=True)
    return float(r.stdout)


# Turnero compartido entre todos los escaneos que corren a la vez (mismo /work montado): Shazam limita por IP, así que
# se reparten los turnos con un intervalo mínimo que sube cuando Shazam rechaza y baja de a poco cuando acepta.
GATE = Path(__file__).resolve().parent / ".gate.json"


def _gate(update):
    import fcntl
    import time
    with open(GATE.with_suffix(".lock"), "a+") as lk:
        fcntl.flock(lk, fcntl.LOCK_EX)
        try:
            g = json.loads(GATE.read_text())
        except Exception:  # noqa: BLE001
            g = {"next": 0.0, "gap": 3.0}
        r = update(g, time.time())
        GATE.write_text(json.dumps(g))
        return r


def gate_wait():
    def upd(g, now):
        slot = max(now, g["next"]); g["next"] = slot + g["gap"]; return slot - now
    return _gate(upd)


def gate_result(limited):
    def upd(g, now):
        if limited:
            g["gap"] = min(20.0, g["gap"] + 1.0); g["next"] = max(g["next"], now + 30)
        else:
            g["gap"] = max(2.0, g["gap"] - 0.1)
    _gate(upd)


async def q(t):
    t = round(t, 1)
    if t in cache:
        return cache[t]
    tmp = f"/tmp/{src.stem}.wav"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(t), "-t", str(WIN), "-i", str(src), "-ac", "1", "-ar", "16000", tmp], check=True)
    res = None
    net_errors = 0
    while net_errors < 40:
        await asyncio.sleep(gate_wait())
        try:
            res = await sh.recognize(tmp); gate_result(False); break
        except Exception as e:  # noqa: BLE001
            print(f"{src.stem} t={t} error {e!r}", flush=True)
            if "RateLimited" in repr(e):
                gate_result(True)  # el límite de Shazam no cuenta como fallo: se reintenta hasta que pase
            else:
                net_errors += 1; await asyncio.sleep(15)
    if res is None:
        # Sin respuesta (red caída, límite de Shazam): no se guarda, así la próxima corrida la vuelve a preguntar.
        return {"t": t, "key": None, "title": None, "artist": None, "offsets": [], "am": None}
    tr = res.get("track") or {}
    am = next((a.get("id") for a in ((tr.get("hub") or {}).get("actions") or []) if a.get("type") == "applemusicplay"), None)
    rec = {"t": t, "key": tr.get("key"), "title": tr.get("title"), "artist": tr.get("subtitle"),
           "offsets": [m.get("offset") for m in res.get("matches") or []], "am": am}
    cache[t] = rec
    with qpath.open("a") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    print(f"{src.stem} {int(t // 60)}:{int(t % 60):02} {rec['artist']} - {rec['title']}", flush=True)
    await asyncio.sleep(1.0)
    return rec


dpath = out.with_suffix(".dur.json")
durs = json.loads(dpath.read_text()) if dpath.exists() else {}


def itunes_duration(h):
    """Duración del tema (s) en el catálogo de Apple: por el id de Apple Music que da Shazam, o buscando artista + título.
    No gasta consultas de Shazam. None si no aparece."""
    k = h["key"]
    if k in durs:
        return durs[k]
    d = None
    try:
        if h.get("am"):
            url = "https://itunes.apple.com/lookup?id=" + str(h["am"])
        else:
            url = "https://itunes.apple.com/search?" + urllib.parse.urlencode({"term": f"{h['artist']} {h['title']}", "entity": "song", "limit": 5})
        with urllib.request.urlopen(url, timeout=15) as r:
            res = json.load(r).get("results", [])
        art = (h["artist"] or "").lower().split(" feat")[0].split(" &")[0]
        pick = next((x for x in res if art and art in (x.get("artistName") or "").lower()), res[0] if res else None)
        if pick and pick.get("trackTimeMillis"):
            d = pick["trackTimeMillis"] / 1000
    except Exception as e:  # noqa: BLE001
        print(f"itunes {h['title']}: {e!r}", flush=True)
    durs[k] = d
    dpath.write_text(json.dumps(durs))
    return d


async def main():
    total = duration()
    grid = [round(i * STEP, 1) for i in range(int(total // STEP) + 1)]
    hits = [await q(t) for t in grid]
    # Grupos de consultas seguidas con el mismo tema.
    groups = []
    for h in hits:
        if not h["key"]:
            continue
        if groups and groups[-1]["key"] == h["key"] and h["t"] - groups[-1]["hits"][-1]["t"] <= STEP * 2 + 1:
            groups[-1]["hits"].append(h)
        else:
            groups.append({"key": h["key"], "title": h["title"], "artist": h["artist"], "hits": [h]})
    # 1) Inicios: salen exactos de Shazam (t - offset); se calculan todos antes que los finales.
    starts_ = []
    for g in groups:
        starts = [h["t"] - o for h in g["hits"] for o in h["offsets"][:3] if o is not None]
        # La mediana de los candidatos que caen cerca entre sí (Shazam a veces da otra versión del tema).
        best = max(starts, key=lambda s: sum(abs(s - x) < 3 for x in starts)) if starts else g["hits"][0]["t"]
        starts_.append(max(0.0, statistics.median([x for x in starts if abs(x - best) < 3]) if starts else best))
    # 2) Finales: con la duración del catálogo de Apple (sin gastar Shazam), sin pasar el inicio del tema siguiente;
    #    si Apple no lo tiene (o su versión es más corta que lo que se escuchó), partiendo el intervalo con Shazam.
    songs = []
    for i, g in enumerate(groups):
        start = starts_[i]
        nxt = starts_[i + 1] if i + 1 < len(groups) else total
        last = g["hits"][-1]["t"] + WIN
        d = itunes_duration(g["hits"][0])
        # El tema terminó entre la última consulta que lo reconoce y la siguiente de la grilla (que ya no lo reconoce):
        # la duración de Apple sólo vale si cae ahí (si no, es otra versión, p. ej. la del álbum, más larga que la de radio).
        latest = min(total, g["hits"][-1]["t"] + STEP + WIN / 2)
        if d and last - 2 <= start + d <= latest:
            end = start + d
        else:
            lo, hi = last, min(total, g["hits"][-1]["t"] + STEP + WIN)
            while hi - lo > 6:
                mid = (lo + hi) / 2
                r = await q(mid - WIN)
                if r["key"] == g["key"]:
                    lo = mid
                else:
                    hi = mid
            end = lo
        songs.append({"start": round(start, 1), "end": round(min(end, nxt, total), 1), "title": g["title"], "artist": g["artist"],
                      "key": g["key"], "hits": len(g["hits"])})
    # Huecos largos sin canción entre dos temas: puede haber uno corto que la grilla salteó; se pregunta en el medio.
    extra = []
    for a, b in zip(songs, songs[1:]):
        if b["start"] - a["end"] > 50:
            r = await q((a["end"] + b["start"]) / 2 - WIN / 2)
            if r["key"] and r["key"] not in (a["key"], b["key"]):
                extra.append({"start": round(max(a["end"], r["t"] - (r["offsets"][0] or 0)), 1), "end": round(r["t"] + WIN, 1),
                              "title": r["title"], "artist": r["artist"], "key": r["key"], "hits": 1})
    songs = sorted(songs + extra, key=lambda s: s["start"])
    json.dump({"file": src.name, "duration": round(total, 1), "step": STEP, "songs": songs}, out.open("w"), ensure_ascii=False, indent=1)
    print(f"LISTO {src.stem}: {len(songs)} canciones")


asyncio.run(main())
