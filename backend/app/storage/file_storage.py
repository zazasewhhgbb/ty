"""
Local filesystem storage for development/small deployments.

Kept behind a small functional interface so it can be swapped for an
S3-compatible backend later (spec section 25) without touching callers.
"""
import re
import uuid
from pathlib import Path

_SAFE_CHARS_RE = re.compile(r"[^A-Za-z0-9._-]")


def safe_filename(original_name: str) -> str:
    """Strips path components and any character that isn't alphanumeric,
    dot, underscore or hyphen — blocks path traversal and weird filenames."""
    name = Path(original_name).name  # drop any directory components
    name = _SAFE_CHARS_RE.sub("_", name)
    return f"{uuid.uuid4().hex[:8]}_{name}" if name else uuid.uuid4().hex


def save_upload_bytes(dest_dir: Path, filename: str, data: bytes) -> str:
    dest_dir.mkdir(parents=True, exist_ok=True)
    safe_name = safe_filename(filename)
    dest_path = dest_dir / safe_name
    with open(dest_path, "wb") as f:
        f.write(data)
    return str(dest_path)
