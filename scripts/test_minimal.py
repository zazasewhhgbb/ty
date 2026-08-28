#!/usr/bin/env python3
"""
Phase 2 of the build order: the smallest possible proof that the chosen
TTS model actually works, before any backend/API/app code is trusted.

Usage:
    python scripts/test_minimal.py voice_sample.wav text.txt output.wav

Input:
    voice_sample.wav  - a short (6-30s) clean reference recording of a
                         voice you own or are authorized to clone
    text.txt           - plain text to speak

Output:
    output.wav          - generated speech in the reference voice
"""
import os
import sys

os.environ.setdefault("COQUI_TOS_AGREED", "1")


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)

    speaker_wav, text_path, out_path = sys.argv[1:4]

    with open(text_path, "r", encoding="utf-8") as f:
        text = f.read().strip()

    if not text:
        print("text.txt is empty")
        sys.exit(1)

    import torch
    from TTS.api import TTS

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Loading XTTS-v2 on {device} (first run downloads ~2GB of weights)...")
    tts = TTS("tts_models/multilingual/multi-dataset/xtts_v2").to(device)

    print(f"Generating speech for {len(text)} characters...")
    tts.tts_to_file(
        text=text,
        speaker_wav=speaker_wav,
        language="en",
        file_path=out_path,
    )
    print(f"Done. Wrote {out_path}")


if __name__ == "__main__":
    main()
