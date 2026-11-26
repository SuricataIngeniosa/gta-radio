#!/usr/bin/env bash
# Corre los escaneos de Shazam (SA, IV, V) sin dejar que la máquina se suspenda; cuando terminan y el pipeline cortó todo
# lo escaneado, apaga la computadora. Uso: systemd-inhibit ... tools/run_until_done.sh  (ver abajo)
set -uo pipefail
cd "$(dirname "$0")/.."
log() { echo "$(date '+%F %T') $*"; }

log "arranco escaneos"
tools/shazam/run_sa.sh >> work/sa-full/scan.log 2>&1 & a=$!
tools/shazam/run_iv.sh >> work/iv/scan.log 2>&1 & b=$!
tools/shazam/run_v.sh >> work/v/scan.log 2>&1 & c=$!
wait $a $b $c
log "escaneos terminados"

# Esperar a que el pipeline (pasa cada 5 min) corte cada emisora escaneada: carpeta de salida más nueva que su .json.
pending() {
  python3 - <<'EOF'
import sys
sys.path.insert(0, "tools")
from pathlib import Path
import pipeline as p
n = 0
for d, (_, _, m) in p.GAMES.items():
    for src, (st, _) in m.items():
        j = p.ROOT / f"work/{d}/{src}.json"; o = p.ROOT / f"work/out-{d}/{st}"
        if not j.exists():
            continue
        if (o / ".done").exists():
            continue
        files = list(o.glob("*")) if o.exists() else []
        if not files or max(f.stat().st_mtime for f in files) < j.stat().st_mtime:
            n += 1
print(n)
EOF
}
for i in $(seq 1 12); do
  n=$(pending); log "emisoras escaneadas sin cortar: $n"
  [ "$n" = 0 ] && break
  timeout 300 tail -f /dev/null
done
log "apagando"
sync
systemctl poweroff || systemctl poweroff --check-inhibitors=no
