import asyncio, json, subprocess, sys
from shazamio import Shazam
src, t = sys.argv[1], float(sys.argv[2])
subprocess.run(["ffmpeg","-v","error","-y","-ss",str(t),"-t","8","-i",src,"-ac","1","-ar","16000","/tmp/c.wav"],check=True)
r = asyncio.run(Shazam().recognize("/tmp/c.wav"))
print(json.dumps({"matches": r.get("matches"), "title": (r.get("track") or {}).get("title")}, indent=1)[:800])
