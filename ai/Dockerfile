# RainSong AI: Muse Spark (Meta Model API) + MusicGen-small.
# CPU image. First start downloads weights into the HF_HOME volume.
FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/models \
    RAINSONG_PORT=8765 \
    MUSICGEN_MODEL=facebook/musicgen-small

RUN apt-get update && apt-get install -y --no-install-recommends \
        libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch \
    && pip install --no-cache-dir transformers scipy numpy zeroconf

COPY server.py /app/server.py

EXPOSE 8765
VOLUME ["/models"]
CMD ["python", "/app/server.py"]
