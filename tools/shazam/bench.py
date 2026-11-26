import asyncio, subprocess, sys, time
from shazamio import Shazam
src = sys.argv[1]; N = int(sys.argv[2]); C = int(sys.argv[3])
def clip(t, i):
    p = f"/tmp/c{i}.wav"
    subprocess.run(["ffmpeg","-v","error","-y","-ss",str(t),"-t","8","-i",src,"-ac","1","-ar","16000",p],check=True); return p
async def one(sh, sem, t, i):
    async with sem:
        t0 = time.time(); r = await sh.recognize(clip(t, i)); tr = r.get("track") or {}
        print(f"{t:6.0f} {time.time()-t0:5.1f}s {tr.get('subtitle')} - {tr.get('title')}", flush=True)
async def main():
    sh = Shazam(); sem = asyncio.Semaphore(C); t0 = time.time()
    await asyncio.gather(*[one(sh, sem, 60 + 97 * i, i) for i in range(N)])
    print(f"total {time.time()-t0:.1f}s para {N} con {C} a la vez")
asyncio.run(main())
