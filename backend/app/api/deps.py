"""
Simple bearer-token auth so strangers can't hit the TTS server (spec
section 26). Not full user-account auth — that's listed as a future
feature — just a shared secret between the Android app and this backend.
"""
from fastapi import Header, HTTPException

from app.config.settings import get_settings

settings = get_settings()


def require_api_key(authorization: str = Header(default="")):
    expected = f"Bearer {settings.api_key}"
    if not authorization or authorization != expected:
        raise HTTPException(401, "Missing or invalid API key")
