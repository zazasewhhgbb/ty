# CUDA-enabled base for GPU deployment. For CPU-only deployment, swap the
# base image for `python:3.11-slim` and drop the nvidia bits below — see
# README "Local Development (CPU mode)".
FROM nvidia/cuda:12.1.1-runtime-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONUNBUFFERED=1 \
    COQUI_TOS_AGREED=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.11 python3-pip ffmpeg git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY backend/requirements.txt .
RUN python3.11 -m pip install --no-cache-dir --upgrade pip \
    && python3.11 -m pip install --no-cache-dir -r requirements.txt

COPY backend/ .
COPY scripts/download_model.sh /app/scripts/download_model.sh

# Pre-bake the model weights into the image so the license prompt and the
# ~2GB download never block container startup (see README Troubleshooting).
RUN bash /app/scripts/download_model.sh || true

EXPOSE 8000

CMD ["python3.11", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
