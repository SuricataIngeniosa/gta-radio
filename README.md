# GTA Radio

App de Android y Android Auto que reproduce las radios de GTA III, Vice City, San Andreas, IV, V y VI con el tema, el artista y el logo de cada emisora. Muestra la grilla de radios con el estilo de cada juego, y en el auto anterior/siguiente cambian de emisora.

**La app no trae audio.** Las emisoras se sacan de tu propia copia de cada juego con los comandos de abajo.

## Instalar

1. Bajá `GTA-Radio.apk` de [Releases](../../releases) e instalalo (hay que permitir "instalar apps desconocidas").
2. Copiá las emisoras al teléfono (ver [Dónde van los archivos](#dónde-van-los-archivos)).
3. Abrí la app y elegí la carpeta de cada juego desde el engranaje. Si no elegís ninguna, la app busca sola `Music/GTA III Radios`, `Music/GTA VC Radios`, etc., en la memoria interna, la SD o un pendrive.

En autos sin selector de archivos (por ejemplo el BYD DiLink) tocá "Permitir acceso a todos los archivos" en la app, o desde una PC:

```sh
adb shell appops set ar.nallib.gtaradio MANAGE_EXTERNAL_STORAGE allow
```

## Dónde van los archivos

Hay una carpeta por juego:

| Juego | Carpeta |
|---|---|
| GTA III | `GTA III Radios` |
| Vice City | `GTA VC Radios` (o `GTA Vice City Radios`) |
| San Andreas | `GTA SA Radios` |
| GTA IV | `GTA IV Radios` |
| GTA V | `GTA V Radios` |
| GTA VI | `GTA VI Radios` |

Dentro de cada una, la app acepta dos formas:

**A. Un archivo por emisora** (lo más rápido): `HEAD.mp3`, `FLASH.flac`, etc., con el nombre corto de la emisora (la lista completa está en `android/app/src/main/java/ar/nallib/gtaradio/data/Stations.kt`). Si al lado hay un `HEAD.cue`, la app muestra el tema que suena. Sin `.cue` se ve sólo el nombre de la radio.

**B. Una subcarpeta por emisora, con un archivo por tema** (recomendado: se puede saltar de tema). La subcarpeta se llama como la emisora (`Head Radio` o `HEAD`) y cada archivo así:

```
[orden]_[Emisora]_[Tema]_[Artista].ext
001_Flash FM_Comentario_Toni.flac
002_Flash FM_Billie Jean_Michael Jackson.flac
003_Flash FM_Tanda - 1_Comercial.flac
```

Lo que empieza con `Comentario`, `Intro`, `Outro`, `Tanda`, `Anuncio` o `Jingle` se muestra como radio + locutor. Formatos: flac, mp3, ogg, opus, m4a y wav.

Logos: `<NOMBRE>.png` en la carpeta del juego (`FLASH.png`) o `logo.png` dentro de la carpeta de la emisora. La app ya trae los logos de todas.

## Requisitos para los comandos

Linux (o WSL) con Docker. Todas las herramientas corren en contenedores, así que no hace falta instalar Python ni ffmpeg:

```sh
git clone https://github.com/SuricataIngeniosa/gta-radio && cd gta-radio
docker build -t gtaradio-align tools/align      # ffmpeg + numpy: cortes, .cue
docker build -t gtaradio-rpf   tools/rpf        # paquetes .rpf de GTA IV
docker build -t gtaradio-vgm   tools/vgmstream  # vgmstream: audio de Rockstar (.ivaud, .awc)
docker build -t gtaradio-shazam tools/shazam    # reconocer temas (sólo si no hay .cue)
```

En los ejemplos, `JUEGO` es la carpeta donde está instalado el juego y `SALIDA` la carpeta donde querés las emisoras.

## Sacar las emisoras de cada juego

| Juego | Estado de estos comandos |
|---|---|
| GTA III | sin probar con los archivos del juego (el formato es WAV común) |
| Vice City | probado |
| San Andreas | sin probar: necesita otra herramienta (abajo) |
| GTA IV | probado (RPF → .ivaud → WAV) |
| GTA V | sin probar: necesita OpenIV en Windows |
| GTA VI | no hay versión de PC: no hay archivos para extraer |

### GTA III

Las emisoras están enteras en `JUEGO/audio/`: `HEAD.wav`, `CLASS.wav`, `KJAH.wav`, `RISE.wav`, `LIPS.wav`, `GAME.wav`, `MSX.wav`, `FLASH.wav` y `CHAT.wav`. Se pasan a mp3 y se cortan con los `.cue` de `cues/III/`:

```sh
for s in HEAD CLASS KJAH RISE LIPS GAME MSX FLASH CHAT; do
  docker run --rm -u $(id -u):$(id -g) -v "JUEGO/audio":/in:ro -v "SALIDA":/out gtaradio-align \
    ffmpeg -n -i /in/$s.wav -c:a libmp3lame -b:a 320k /out/$s.mp3
done
```

Con eso ya funciona la forma A (copiá también `cues/III/*.cue` al lado). Para la forma B, ver [Cortar en un archivo por tema](#cortar-en-un-archivo-por-tema).

### Vice City

Cada emisora es un `.adf` en `JUEGO/Audio/` (`FLASH.adf`, `WAVE.adf`, `EMOTION.adf`, `ESPANT.adf`, `FEVER.adf`, `VROCK.adf`, `WILD.adf`, `KCHAT.adf`, `VCPR.adf`). Un `.adf` es un mp3 con cada byte pasado por XOR 0x22, y `tools/adf2mp3.py` lo deshace sin recodificar:

```sh
docker run --rm -u $(id -u):$(id -g) -v "JUEGO/Audio":/in:ro -v "SALIDA":/out -v "$PWD/tools":/t:ro \
  gtaradio-align python /t/adf2mp3.py /in /out
```

Salen `flash.mp3`, `wave.mp3`, etc. Renombralos a mayúsculas (`FLASH.mp3`) y copiá al lado `cues/VC/*.cue`. Wildstyle, K-Chat y VCPR no tienen `.cue`.

### San Andreas

Las radios están en `JUEGO/audio/streams/` (archivos sin extensión como `CH`, `CO`, `CR`, `DS`, `HC`, `MH`, `MR`, `NJ`, `RE`, `RG`) y cada uno trae muchos temas sueltos en Ogg Vorbis cifrado. Este repo **no** tiene un extractor para ese formato. Lo más usado es SAAT (San Andreas Audio Toolkit, de Windows), que exporta cada tema como `.ogg`. Después se ordenan en subcarpetas con la forma B.

### GTA IV

Las radios están en `JUEGO/pc/audio/sfx/RADIO_*.rpf`. `tools/rpf/extract.py` abre el paquete (el índice está cifrado con AES) y saca los `.ivaud`. Después vgmstream los decodifica a WAV:

```sh
mkdir -p SALIDA/ivaud
docker run --rm -u $(id -u):$(id -g) -v "JUEGO/pc/audio/sfx":/in:ro -v "SALIDA/ivaud":/out -v "$PWD/tools":/t:ro \
  gtaradio-rpf sh -c 'for r in /in/RADIO_*.rpf; do python /t/rpf/extract.py "$r" /out; done'

docker run --rm -u $(id -u):$(id -g) -v "SALIDA/ivaud":/w gtaradio-vgm \
  sh -c 'for f in /w/*.ivaud; do vgmstream-cli -o "${f%.ivaud}.wav" "$f"; done'
```

Cada `.ivaud` es un tema o un bloque de comentarios del DJ, con un número en el nombre en vez del título. Para saber cuál es cuál, ver [Reconocer los temas con Shazam](#reconocer-los-temas-con-shazam).

### GTA V

Las radios están en paquetes RPF7 (`x64*.rpf` → `audio/sfx/RADIO_*.rpf`). Su cifrado necesita claves que salen del ejecutable del juego, así que el camino más simple es [OpenIV](https://openiv.com/) en Windows: abrir `RADIO_*.rpf` y exportar los `.awc`. Después vgmstream los convierte igual que en GTA IV:

```sh
docker run --rm -u $(id -u):$(id -g) -v "SALIDA/awc":/w gtaradio-vgm \
  sh -c 'for f in /w/*.awc; do vgmstream-cli -S 0 -o "${f%.awc}_?s_?n.wav" "$f"; done'
```

(`-S 0` saca todos los sub-audios de cada `.awc`; `?s` y `?n` ponen su número y nombre.)

### GTA VI

Sólo existe en consolas, así que no hay archivos de juego para extraer. Si tenés la emisora completa en un archivo, `tools/align/chapters_to_cue.py` arma el `.cue` con los capítulos (tema y artista con su minuto) de un video "Full Radio Station" bajado con `yt-dlp --write-info-json`.

## Cortar en un archivo por tema

Con el audio de la emisora y su `.cue`, `split.py` genera la forma B: FLAC→FLAC sin pérdida, mp3→mp3 320 kbps, todo sin huecos entre temas:

```sh
docker run --rm -u $(id -u):$(id -g) -v "SALIDA":/w -v "$PWD/cues":/c:ro -v "$PWD/tools":/t:ro gtaradio-align \
  python /t/align/split.py /w/FLASH.mp3 /c/VC/FLASH.cue "/w/Flash FM" "Flash FM" "Toni"
```

Argumentos: audio, `.cue`, carpeta de salida, nombre de la emisora y locutor.

## Reconocer los temas con Shazam

Cuando no hay `.cue` (emisoras de SA, IV y V, o versiones del juego distintas a la usada para `cues/`), `scan_songs.py` consulta Shazam cada 60 s del archivo y `songs_to_cue.py` arma el `.cue`:

```sh
docker run --rm -u $(id -u):$(id -g) -v "SALIDA":/w -v "$PWD/tools":/t:ro gtaradio-shazam \
  python /t/shazam/scan_songs.py /w/K109.mp3 /w/K109.json
docker run --rm -u $(id -u):$(id -g) -v "SALIDA":/w -v "$PWD/tools":/t:ro gtaradio-align \
  python /t/align/songs_to_cue.py /w/K109.json /w/K109.cue
```

Shazam acepta unas 2 consultas por minuto por IP, así que una emisora de 1 h tarda un rato. El escaneo se puede cortar y retomar: cada respuesta queda guardada en `K109.q.jsonl`. Lo que no reconoce como canción queda como comentario.

Si tenés los temas sueltos (de un CD, por ejemplo), `tools/align/align.py` los ubica en el archivo completo por correlación, con unos 0,1 s de precisión y sin Shazam.

## `cues/`

`.cue` ya hechos para GTA III y Vice City, alineados con el audio de una copia de PC. Si tu versión trae otros temas (la de Steam de Vice City sacó algunos), los tiempos no coinciden: generalos de nuevo con `align.py` o Shazam.

## Compilar

```sh
cd android && ./build.sh          # APK debug en app/build/outputs/apk/debug/
./build.sh install                # además lo instala por adb
```

Necesita JDK 17 y el Android SDK con `platforms;android-37.2` (ver `build.sh`).

## Aviso

Proyecto personal sin relación con Rockstar Games ni Take-Two. Los nombres, logos y la música son de sus dueños. La app no trae audio de los juegos: cada uno saca el suyo de su propia copia. Las fuentes que trae la app y sus licencias están en [android/licenses/FONTS.md](android/licenses/FONTS.md).
