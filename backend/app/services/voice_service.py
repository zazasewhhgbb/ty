import shutil
from pathlib import Path

from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.audio.processing import get_duration_seconds
from app.config.settings import get_settings
from app.models.database import VoiceProfile
from app.storage.file_storage import save_upload_bytes

settings = get_settings()

ALLOWED_EXTENSIONS = {".wav", ".mp3", ".m4a", ".ogg"}
MIN_DURATION_SECONDS = 4.0
MAX_DURATION_SECONDS = 180.0
RECOMMENDED_DURATION_SECONDS = "6-30 seconds (XTTS-v2 clones from as little as ~6s, "
"but 20-30s of clean, single-speaker audio gives noticeably more stable results)"


def validate_and_store_sample(name: str, filename: str, data: bytes) -> VoiceProfile:
    ext = Path(filename).suffix.lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(400, f"Unsupported audio format '{ext}'. Use WAV, MP3, M4A, or OGG.")

    max_bytes = settings.max_upload_mb * 1024 * 1024
    if len(data) > max_bytes:
        raise HTTPException(400, f"File too large. Max {settings.max_upload_mb} MB.")
    if len(data) == 0:
        raise HTTPException(400, "Uploaded file is empty.")

    dest_dir = settings.voices_dir / "samples"
    saved_path = save_upload_bytes(dest_dir, filename, data)

    try:
        duration = get_duration_seconds(saved_path)
    except Exception:
        Path(saved_path).unlink(missing_ok=True)
        raise HTTPException(400, "Could not read audio file — it may be corrupt or empty.")

    if duration < MIN_DURATION_SECONDS:
        Path(saved_path).unlink(missing_ok=True)
        raise HTTPException(
            400,
            f"Recording is too short ({duration:.1f}s). Please provide at least "
            f"{MIN_DURATION_SECONDS:.0f} seconds of clear speech.",
        )
    if duration > MAX_DURATION_SECONDS:
        # not an error — just trim expectations; XTTS only needs a short clip anyway
        pass

    return VoiceProfile(
        name=name,
        sample_path=saved_path,
        duration_seconds=duration,
        model_name=settings.model_name,
    )


def delete_voice_files(voice: VoiceProfile):
    Path(voice.sample_path).unlink(missing_ok=True)
    if voice.embedding_path:
        Path(voice.embedding_path).unlink(missing_ok=True)
