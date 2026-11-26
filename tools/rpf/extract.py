"""Extrae los archivos de un RPF3 de GTA IV (índice cifrado AES-256 ECB x16). En estos paquetes de audio las entradas de
archivo traen: hash del nombre, tamaño, offset directo en bytes y tamaño en el archivo (bit 0x40000000 = comprimido)."""
import os, struct, sys, zlib
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
KEY = bytes.fromhex("1AB56FED7EC3FF01227B691533975DCE47D769653FF775426A96CD6D5307565D")
rpf, out = sys.argv[1], sys.argv[2]; os.makedirs(out, exist_ok=True)
f = open(rpf, "rb"); magic, toc_size, count, _, enc = struct.unpack("<4sIIII", f.read(20))
f.seek(0x800); toc = f.read(toc_size)
if enc:
    toc = toc[: len(toc) - len(toc) % 16]
    for _ in range(16):
        d = Cipher(algorithms.AES(KEY), modes.ECB()).decryptor(); toc = d.update(toc) + d.finalize()
base = os.path.splitext(os.path.basename(rpf))[0]
for i in range(count):
    h, size, off, flags = struct.unpack("<IIII", toc[i * 16:(i + 1) * 16])
    if off & 0x80000000:          # directorio
        continue
    comp = bool(flags & 0x40000000); stored = flags & 0x3FFFFFFF
    f.seek(off); raw = f.read(stored)
    if comp:
        raw = zlib.decompress(raw)
    name = f"{base}_{i:02d}_{h:08x}.ivaud"
    open(os.path.join(out, name), "wb").write(raw)
    print(name, len(raw), raw[:16].hex())
