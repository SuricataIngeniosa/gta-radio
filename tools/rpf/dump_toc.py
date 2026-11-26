"""Descifra y muestra el índice (TOC) de un RPF3 de GTA IV, para entender las entradas."""
import struct, sys
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
KEY = bytes.fromhex("1AB56FED7EC3FF01227B691533975DCE47D769653FF775426A96CD6D5307565D")
f = open(sys.argv[1], "rb"); hdr = f.read(20)
magic, toc_size, count, unk, enc = struct.unpack("<4sIIII", hdr)
print(magic, "toc", toc_size, "entradas", count, "unk", hex(unk), "cifrado", hex(enc))
f.seek(0x800); toc = f.read(toc_size)
if enc:
    toc = toc[: len(toc) - len(toc) % 16]
    for _ in range(16):
        d = Cipher(algorithms.AES(KEY), modes.ECB()).decryptor(); toc = d.update(toc) + d.finalize()
for i in range(count):
    a, b, c, d = struct.unpack("<IIII", toc[i * 16:(i + 1) * 16])
    print(f"{i:2d}  {a:08x} {b:10d} {c:08x} {d:08x}")
