"""Saca los polígonos de las letras I y V del logo de GTA IV (relleno negro, dentro del bisel blanco)."""
import json, sys
import cv2, numpy as np
im = cv2.imread(sys.argv[1], cv2.IMREAD_UNCHANGED)
a = im[:, :, 3] > 128
lum = im[:, :, :3].max(axis=2)
dark = (lum < 80) & a
n, lab = cv2.connectedComponents(dark.astype(np.uint8), connectivity=4)
# componentes que tocan lo transparente = anillo exterior; las demás = relleno de las letras
transp = ~a
border = cv2.dilate(transp.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0
out = []
for k in range(1, n):
    m = lab == k
    if m.sum() < 2000 or (m & border).any():
        continue
    cs, hier = cv2.findContours(m.astype(np.uint8), cv2.RETR_CCOMP, cv2.CHAIN_APPROX_NONE)
    for c, hh in zip(cs, hier[0]):
        poly = cv2.approxPolyDP(c, 2.0, True)[:, 0, :].tolist()
        out.append({"area": int(m.sum()), "hole": bool(hh[3] != -1), "pts": poly, "bbox": list(map(int, cv2.boundingRect(c)))})
json.dump(out, open(sys.argv[2], "w"))
for o in out:
    print(o["hole"], o["bbox"], len(o["pts"]), o["pts"])
