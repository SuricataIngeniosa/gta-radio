"""Copia los logos de images/<JUEGO>/logos a la app (assets/logos/<GAME>/<ARCHIVO>.png), convirtiendo los SVG y
achicando a 512 px. Corre en el contenedor gtaradio-svg (cairosvg + Pillow): python /p/tools/apply_logos.py /p"""
import io, sys
from pathlib import Path
import cairosvg
from PIL import Image

root = Path(sys.argv[1])
out = root / "android/app/src/main/assets/logos"
MAP = {
    "GTA_IV": {"MassiveBSoundsystem": "MASSIVEB", "LibertyRockRadio": "LRR", "TheVibe98.8": "VIBE", "InternationalFunk99": "IF99",
               "TheBeat": "BEAT", "TheClassics": "CLASSICS", "JazzNationRadio": "JAZZ", "K109TheStudio": "K109", "VladivostokFM": "VLAD",
               "WKTTRadio": "WKTT", "ElectroChoc": "ELECTRO", "LibertyCityHardcore": "LCHC", "FusionFM": "FUSION", "TheJourney": "JOURNEY",
               "RadioBroker": "BROKER", "SanJuanSounds": "SANJUAN", "TuffGong": "TUFFGONG", "Integrity": "INTEGRITY",
               "PublicLibertyRadio": "PLR", "IndependenceFM": "INDEP", "RamJam_FM": "RAMJAM", "ViceCityFM": "VCFM", "Self-Actualization_FM": "SELFACT"},
    "GTA_V": {"LosSantosRockRadio": "LSRR", "NonStopPopFM": "NONSTOP", "RadioLosSantos": "RLS", "ChannelX": "CHANNELX", "WCTR": "WCTR",
              "RebelRadio": "REBEL", "SoulwaxFM": "SOULWAX", "EastLosFM": "EASTLOS", "WestCoastClassics": "WCC", "TheBlueArk": "BLUEARK",
              "WorldwideFM": "WORLDWIDE", "FlyLoFM": "FLYLO", "TheLowdown91.1": "LOWDOWN", "RadioMirrorPark": "MIRROR", "Space103.2": "SPACE",
              "VinewoodBoulevardRadio": "VINEWOOD", "BlaineCountyTalkRadio": "BLAINE", "TheLab": "LAB", "BlondedLosSantos97.8FM": "BLONDED",
              "LSUR": "LSUR"},
    "GTA_VI": {"ViceCityRapRadio": "VCRR", "DirtySouthClassics": "DIRTYSOUTH", "HoneyFM": "HONEY", "CocoteoFM": "COCOTEO", "LaOla": "LAOLA",
               "AfroBankFM": "AFROBANK", "RadioOnU": "ONU", "FlashFM": "FLASH", "Emotion983": "EMOTION", "ViceCityFM": "VCFM", "VRock": "VROCK",
               "TheChamber1066": "CHAMBER", "BackCountryRadio": "BACKCOUNTRY", "GlorianaBluesRadio": "GLORIANA", "StockyardFM": "STOCKYARD",
               "CircoLocoRecordsRadio": "CIRCOLOCO", "WorldWideFM": "WORLDWIDE", "KaleidoscopeFM": "KALEIDO", "LeonidaJazzRadio": "JAZZ",
               "SymphonyFM": "SYMPHONY", "WKTTTalkRadio": "WKTT", "TheWipe": "WIPE", "TheVaultRadio": "VAULT"},
}
# Logos oscuros que se pierden sobre el fondo del botón: llevan un contorno blanco alrededor de toda la silueta.
WHITE_OUTLINE = {("GTA_IV", "LRR"), ("GTA_IV", "JAZZ"), ("GTA_IV", "LCHC"), ("GTA_IV", "PLR")}


def white_outline(im, frac=0.018):
    from PIL import ImageFilter
    im = im.convert("RGBA")
    w = max(2, round(max(im.size) * frac))
    big = Image.new("RGBA", (im.width + 2 * w, im.height + 2 * w))
    big.paste(im, (w, w))
    a = big.getchannel("A").point(lambda v: 255 if v > 40 else 0).filter(ImageFilter.MaxFilter(2 * w + 1)).filter(ImageFilter.GaussianBlur(0.8))
    out = Image.new("RGBA", big.size, (255, 255, 255, 0)); out.putalpha(a)
    out.alpha_composite(big)
    return out


FOLDER = {"GTA_IV": "IV", "GTA_V": "V", "GTA_VI": "VI"}
for game, m in MAP.items():
    (out / game).mkdir(parents=True, exist_ok=True)
    done = set()
    for f in sorted((root / "images" / FOLDER[game] / "logos").iterdir()):
        key = f.stem.split("-GTA")[0]
        stem = m.get(key)
        if not stem:
            print(f"  (sin emisora en la app: {f.name})"); continue
        if f.suffix == ".svg":
            im = Image.open(io.BytesIO(cairosvg.svg2png(url=str(f), output_width=512))).convert("RGBA")
        else:
            im = Image.open(f)
            im = im.convert("RGBA") if im.mode in ("RGBA", "LA", "P") else im.convert("RGB")
        if im.mode == "RGBA" and im.getbbox():
            im = im.crop(im.getchannel("A").getbbox() or im.getbbox())
        if game == "GTA_IV" and im.mode == "RGB" and max(im.getpixel((1, 1))) < 30:
            # Logo sobre un rectángulo negro (The Journey): el negro que toca el borde pasa a transparente.
            from PIL import ImageDraw
            im = im.convert("RGBA")
            ImageDraw.floodfill(im, (0, 0), (0, 0, 0, 0), thresh=40)
            im = im.crop(im.getchannel("A").getbbox())
        if game == "GTA_V" and stem == "EASTLOS":
            # Cuadrado amarillo: se recorta al círculo inscripto (el texto entra), como los demás logos de V.
            from PIL import ImageDraw
            im = im.convert("RGBA"); side = min(im.size)
            im = im.crop(((im.width - side) // 2, (im.height - side) // 2, (im.width + side) // 2, (im.height + side) // 2))
            mask = Image.new("L", (side * 4, side * 4)); ImageDraw.Draw(mask).ellipse((0, 0, side * 4 - 1, side * 4 - 1), fill=255)
            im.putalpha(mask.resize((side, side), Image.LANCZOS))
        if (game, stem) in WHITE_OUTLINE:
            im = white_outline(im)
        im.thumbnail((512, 512), Image.LANCZOS)
        im.save(out / game / f"{stem}.png", optimize=True)
        done.add(stem)
    print(game, len(done), "logos; sin logo:", sorted(set(m.values()) - done))
