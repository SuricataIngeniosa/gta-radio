FROM python:3.13-slim
RUN apt-get update && apt-get install -y --no-install-recommends libcairo2 && rm -rf /var/lib/apt/lists/* && pip install --no-cache-dir cairosvg pillow
WORKDIR /work
