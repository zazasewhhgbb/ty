"""
API-level tests. TTS synthesis itself is monkeypatched/skipped by default
since it needs real model weights + GPU/CPU time; set RUN_TTS_TESTS=1 to
also exercise the real model end-to-end.
"""
import io
import os
import wave

import pytest
from fastapi.testclient import TestClient

os.environ["DATABASE_URL"] = "sqlite:///./data/test.db"
os.environ["API_KEY"] = "test-key"

from app.main import app  # noqa: E402
from app.models.database import init_db  # noqa: E402

init_db()  # TestClient doesn't reliably fire startup events across versions

client = TestClient(app)
AUTH = {"Authorization": "Bearer test-key"}


def _make_wav_bytes(seconds: float = 5.0) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(b"\x00\x00" * int(16000 * seconds))
    return buf.getvalue()


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_requires_api_key():
    resp = client.get("/voices")
    assert resp.status_code == 401


def test_voice_upload_and_validation():
    # too short -> rejected
    short_wav = _make_wav_bytes(seconds=1.0)
    resp = client.post(
        "/voices",
        headers=AUTH,
        data={"name": "Test Voice"},
        files={"file": ("sample.wav", short_wav, "audio/wav")},
    )
    assert resp.status_code == 400

    good_wav = _make_wav_bytes(seconds=6.0)
    resp = client.post(
        "/voices",
        headers=AUTH,
        data={"name": "Test Voice"},
        files={"file": ("sample.wav", good_wav, "audio/wav")},
    )
    assert resp.status_code == 200
    voice_id = resp.json()["id"]

    resp = client.get("/voices", headers=AUTH)
    assert resp.status_code == 200
    assert any(v["id"] == voice_id for v in resp.json())

    resp = client.delete(f"/voices/{voice_id}", headers=AUTH)
    assert resp.status_code == 200


def test_reject_bad_format():
    resp = client.post(
        "/voices",
        headers=AUTH,
        data={"name": "Bad"},
        files={"file": ("sample.exe", b"not audio", "application/octet-stream")},
    )
    assert resp.status_code == 400


def test_text_chunking():
    from app.audio.chunking import plan_chunks

    text = "This is sentence one. This is sentence two! Is this sentence three? " * 20
    plan = plan_chunks(text, max_chars=100)
    assert len(plan.chunks) > 1
    for chunk in plan.chunks:
        assert chunk.strip()


def test_job_not_found():
    resp = client.get("/jobs/does_not_exist", headers=AUTH)
    assert resp.status_code == 404


def test_generate_requires_existing_voice():
    resp = client.post(
        "/generate",
        headers=AUTH,
        json={"voice_id": "vp_does_not_exist", "text": "hello world"},
    )
    assert resp.status_code == 404


@pytest.mark.skipif(os.environ.get("RUN_TTS_TESTS") != "1", reason="Requires real TTS model + weights")
def test_full_generation_pipeline():
    good_wav = _make_wav_bytes(seconds=6.0)
    resp = client.post(
        "/voices",
        headers=AUTH,
        data={"name": "Full Test"},
        files={"file": ("sample.wav", good_wav, "audio/wav")},
    )
    voice_id = resp.json()["id"]

    resp = client.post(
        "/generate",
        headers=AUTH,
        json={"voice_id": voice_id, "text": "This is a short test sentence."},
    )
    assert resp.status_code == 200
    job_id = resp.json()["job_id"]

    import time
    for _ in range(60):
        status = client.get(f"/jobs/{job_id}", headers=AUTH).json()
        if status["status"] in ("completed", "failed"):
            break
        time.sleep(2)

    assert status["status"] == "completed"
