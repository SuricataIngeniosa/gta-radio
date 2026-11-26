# GTA Radio

Android and Android Auto app that plays the radio stations of GTA III, Vice City, San Andreas, IV, V and VI, showing the song, the artist and each station's logo. The station grid is styled after each game, and in the car previous/next switch stations.

**The app ships no audio.** You extract the stations from your own copy of each game with the commands below.

## Install

1. Download `GTA-Radio.apk` from [Releases](../../releases) and install it (allow "install unknown apps").
2. Copy the stations to the phone (see [Folder layout](#folder-layout)).
3. Open the app and pick each game's folder from the gear icon. If you pick none, the app looks for `Music/GTA III Radios`, `Music/GTA VC Radios`, etc. on internal storage, the SD card or a USB drive.

On car head units without a file picker (e.g. BYD DiLink), tap "Allow access to all files" in the app, or from a PC:

```sh
adb shell appops set ar.nallib.gtaradio MANAGE_EXTERNAL_STORAGE allow
```

## Folder layout

One folder per game:

| Game | Folder |
|---|---|
| GTA III | `GTA III Radios` |
| Vice City | `GTA VC Radios` (or `GTA Vice City Radios`) |
| San Andreas | `GTA SA Radios` |
| GTA IV | `GTA IV Radios` |
| GTA V | `GTA V Radios` |
| GTA VI | `GTA VI Radios` |

Inside it, one subfolder per station, named after the station (`Flash FM` or its short name `FLASH`; the full list is in `android/app/src/main/java/ar/nallib/gtaradio/data/Stations.kt`). Inside each station folder, one file per song, DJ break or ad block:

```
[order]_[Station]_[Song]_[Artist].ext
001_Flash FM_Comentario_Toni.flac
002_Flash FM_Billie Jean_Michael Jackson.flac
003_Flash FM_Tanda - 1_Comercial.flac
```

Files whose title starts with `Comentario`, `Intro`, `Outro`, `Tanda`, `Anuncio`, `Ad` or `Jingle` are shown as station name + DJ. Supported formats: flac, mp3, ogg, opus, m4a and wav.

The app reads no `.cue` files: they are only used on the PC to cut a full station into these files (see [Cut a station into songs](#cut-a-station-into-songs)).

Logos: the app already includes them. To override one, put `<SHORTNAME>.png` in the game folder (`FLASH.png`) or `logo.png` in the station folder.

## Requirements

Linux (or WSL) with Docker. Every tool runs in a container, so you don't need Python or ffmpeg installed:

```sh
git clone https://github.com/SuricataIngeniosa/gta-radio && cd gta-radio
docker build -t gtaradio-align  tools/align      # ffmpeg + numpy: cutting, .cue
docker build -t gtaradio-rpf    tools/rpf        # GTA IV .rpf archives
docker build -t gtaradio-vgm    tools/vgmstream  # vgmstream: Rockstar audio (.ivaud, .awc)
docker build -t gtaradio-shazam tools/shazam     # song recognition (only when there is no .cue)
```

In the examples, `GAME` is the game's install folder and `OUT` is where you want the stations.

## Extracting the stations

| Game | Status of these commands |
|---|---|
| GTA III | untested on the game files (plain WAV) |
| Vice City | tested |
| San Andreas | untested: needs a third-party tool (below) |
| GTA IV | tested (RPF → .ivaud → WAV) |
| GTA V | untested: needs OpenIV on Windows |
| GTA VI | no PC release, so there are no game files to extract |

### GTA III

Each station is a whole file in `GAME/audio/`: `HEAD.wav`, `CLASS.wav`, `KJAH.wav`, `RISE.wav`, `LIPS.wav`, `GAME.wav`, `MSX.wav`, `FLASH.wav` and `CHAT.wav`. Convert them to mp3, then cut them with the `.cue` files in `cues/III/`:

```sh
for s in HEAD CLASS KJAH RISE LIPS GAME MSX FLASH CHAT; do
  docker run --rm -u $(id -u):$(id -g) -v "GAME/audio":/in:ro -v "OUT":/out gtaradio-align \
    ffmpeg -n -i /in/$s.wav -c:a libmp3lame -b:a 320k /out/$s.mp3
done
```

### Vice City

Each station is an `.adf` in `GAME/Audio/` (`FLASH.adf`, `WAVE.adf`, `EMOTION.adf`, `ESPANT.adf`, `FEVER.adf`, `VROCK.adf`, `WILD.adf`, `KCHAT.adf`, `VCPR.adf`). An `.adf` is an mp3 with every byte XORed with 0x22; `tools/adf2mp3.py` undoes it without re-encoding:

```sh
docker run --rm -u $(id -u):$(id -g) -v "GAME/Audio":/in:ro -v "OUT":/out -v "$PWD/tools":/t:ro \
  gtaradio-align python /t/adf2mp3.py /in /out
```

You get `flash.mp3`, `wave.mp3`, etc. Rename them to upper case (`FLASH.mp3`) and cut them with `cues/VC/`. Wildstyle, K-Chat and VCPR have no `.cue` yet.

### San Andreas

The stations live in `GAME/audio/streams/` (extensionless files such as `CH`, `CO`, `CR`, `DS`, `HC`, `MH`, `MR`, `NJ`, `RE`, `RG`), each holding many separate tracks as encrypted Ogg Vorbis. This repo has **no** extractor for that format. The usual tool is SAAT (San Andreas Audio Toolkit, Windows), which exports each track as `.ogg`. Then rename them into station folders as described in [Folder layout](#folder-layout).

### GTA IV

The stations are in `GAME/pc/audio/sfx/RADIO_*.rpf`. `tools/rpf/extract.py` opens the archive (its index is AES-encrypted) and pulls out the `.ivaud` files, then vgmstream decodes them to WAV:

```sh
mkdir -p OUT/ivaud
docker run --rm -u $(id -u):$(id -g) -v "GAME/pc/audio/sfx":/in:ro -v "OUT/ivaud":/out -v "$PWD/tools":/t:ro \
  gtaradio-rpf sh -c 'for r in /in/RADIO_*.rpf; do python /t/rpf/extract.py "$r" /out; done'

docker run --rm -u $(id -u):$(id -g) -v "OUT/ivaud":/w gtaradio-vgm \
  sh -c 'for f in /w/*.ivaud; do vgmstream-cli -o "${f%.ivaud}.wav" "$f"; done'
```

Each `.ivaud` is one song or one DJ block, named by a number instead of its title. To find out which is which, see [Recognizing songs with Shazam](#recognizing-songs-with-shazam).

### GTA V

The stations are in RPF7 archives (`x64*.rpf` → `audio/sfx/RADIO_*.rpf`). Their encryption needs keys taken from the game executable, so the simplest route is [OpenIV](https://openiv.com/) on Windows: open `RADIO_*.rpf` and export the `.awc` files. vgmstream then converts them like in GTA IV:

```sh
docker run --rm -u $(id -u):$(id -g) -v "OUT/awc":/w gtaradio-vgm \
  sh -c 'for f in /w/*.awc; do vgmstream-cli -S 0 -o "${f%.awc}_?s_?n.wav" "$f"; done'
```

(`-S 0` extracts every sub-stream in each `.awc`; `?s` and `?n` add its number and name.)

### GTA VI

It's console-only, so there are no game files to extract. If you have a full station recording, `tools/align/chapters_to_cue.py` builds its `.cue` from the chapters (song, artist and timestamp) of a "Full Radio Station" video downloaded with `yt-dlp --write-info-json`.

## Cut a station into songs

Given a full station and its `.cue`, `split.py` writes the per-song files the app expects. FLAC stays lossless FLAC, mp3 becomes 320 kbps mp3, and there are no gaps between tracks:

```sh
docker run --rm -u $(id -u):$(id -g) -v "OUT":/w -v "$PWD/cues":/c:ro -v "$PWD/tools":/t:ro gtaradio-align \
  python /t/align/split.py /w/FLASH.mp3 /c/VC/FLASH.cue "/w/Flash FM" "Flash FM" "Toni"
```

Arguments: audio, `.cue`, output folder, station name and DJ.

## Recognizing songs with Shazam

When there is no `.cue` (San Andreas, IV and V, or a game version different from the one used for `cues/`), `scan_songs.py` queries Shazam every 60 s of audio and `songs_to_cue.py` builds the `.cue`:

```sh
docker run --rm -u $(id -u):$(id -g) -v "OUT":/w -v "$PWD/tools":/t:ro gtaradio-shazam \
  python /t/shazam/scan_songs.py /w/K109.mp3 /w/K109.json
docker run --rm -u $(id -u):$(id -g) -v "OUT":/w -v "$PWD/tools":/t:ro gtaradio-align \
  python /t/align/songs_to_cue.py /w/K109.json /w/K109.cue
```

Shazam allows about 2 queries per minute per IP, so a one-hour station takes a while. The scan can be stopped and resumed: every answer is saved to `K109.q.jsonl`. Anything not recognized as a song becomes a DJ break.

If you have the songs as separate files (from a CD, for example), `tools/align/align.py` locates each one inside the full station by correlation, to about 0.1 s, without Shazam.

## `cues/`

Ready-made `.cue` files for GTA III and Vice City, aligned with the audio of one PC copy. If your version has different songs (the Steam release of Vice City removed some), the timings won't match: regenerate them with `align.py` or Shazam.

## Building

```sh
cd android && ./build.sh          # debug APK in app/build/outputs/apk/debug/
./build.sh install                # also installs it over adb
```

Needs JDK 17 and the Android SDK with `platforms;android-37.2` (see `build.sh`).

## Disclaimer

Personal project, not affiliated with Rockstar Games or Take-Two. Names, logos and music belong to their owners. The app ships no game audio: everyone extracts their own from their own copy. The fonts bundled in the app and their licenses are listed in [android/licenses/FONTS.md](android/licenses/FONTS.md).
