"""
Thin wrapper around the Coqui XTTS-v2 model.

Why XTTS-v2: see README "TTS Model Choice" for the full comparison against
F5-TTS and others. Short version — best practical balance of voice-cloning
quality from a short reference clip, multilingual support, GPU *and* CPU
inference, and a mature, actively-forked codebase. Its weights are licensed
under the Coqui Public Model License (non-commercial); that is acceptable
for this personal, self-hosted project. Do not use this module in a paid
product without a separate commercial agreement or a different model.

This module is intentionally the ONLY place that talks to the model, so
swapping engines later (e.g. adding F5-TTS as an alternative) means adding
a sibling class with the same interface, not touching the rest of the app.
"""
import os
import threading
from pathlib import Path

os.environ.setdefault("COQUI_TOS_AGREED", "1")

_model = None
_model_lock = threading.Lock()
_device = None


def _load_model(model_name: str, device: str):
    """Lazy singleton load — the model is large, so load it once per process."""
    global _model, _device
    with _model_lock:
        if _model is not None:
            return _model
        import torch
        from TTS.api import TTS

        resolved_device = device
        if resolved_device == "cuda" and not torch.cuda.is_available():
            resolved_device = "cpu"

        _model = TTS(model_name).to(resolved_device)
        _device = resolved_device
        return _model


class TTSEngine:
    def __init__(self, model_name: str, device: str = "cuda"):
        self.model_name = model_name
        self.device = device

    def get_device(self) -> str:
        _load_model(self.model_name, self.device)
        return _device

    def synthesize_chunk(
        self,
        text: str,
        speaker_wav: str,
        language: str,
        out_path: str,
        speed: float = 1.0,
    ) -> None:
        """
        Generate one chunk of speech using the given reference voice sample.
        Reusing the same speaker_wav for every chunk of a job is what keeps
        the voice consistent across the whole generation (see README /
        chunking.py docstring).
        """
        model = _load_model(self.model_name, self.device)
        Path(out_path).parent.mkdir(parents=True, exist_ok=True)
        model.tts_to_file(
            text=text,
            speaker_wav=speaker_wav,
            language=language,
            file_path=out_path,
            speed=speed,
        )
