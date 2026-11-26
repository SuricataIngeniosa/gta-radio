# vgmstream (decodificador libre de audio de videojuegos; lee los .ivaud de GTA IV), versión oficial ya compilada.
FROM debian:trixie-slim
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates unzip ffmpeg && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt/vgm && cd /opt/vgm \
 && curl -fsSL -o v.zip https://github.com/vgmstream/vgmstream/releases/download/r2168/vgmstream-linux.zip \
 && unzip v.zip && rm v.zip && chmod +x vgmstream-cli && ls -la
ENV PATH=/opt/vgm:$PATH
WORKDIR /work
