#!/usr/bin/env bash
# Pre-downloads the XTTS-v2 weights (~2GB) and accepts the Coqui Public
# Model License non-interactively, so the first real API request doesn't
# have to pay that cost (and so Docker builds don't hang on a license
# prompt — see README "Troubleshooting").
set -euo pipefail

export COQUI_TOS_AGREED=1

python3 - <<'PY'
from TTS.api import TTS
print("Downloading tts_models/multilingual/multi-dataset/xtts_v2 ...")
TTS("tts_models/multilingual/multi-dataset/xtts_v2")
print("Model cached.")
PY
