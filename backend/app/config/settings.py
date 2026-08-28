"""
Centralized configuration. Everything is read from environment variables
(see .env.example) so no secrets are ever hard-coded in source.
"""
from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # TTS
    model_name: str = "tts_models/multilingual/multi-dataset/xtts_v2"
    device: str = "cuda"
    coqui_tos_agreed: str = "1"

    # Storage
    data_dir: str = "./data"
    database_url: str = "sqlite:///./data/voicegen.db"

    # Auth
    api_key: str = "change-me"

    # Jobs
    max_concurrent_jobs: int = 1
    chunk_max_chars: int = 400

    # Output
    output_format: str = "mp3"

    # Uploads
    max_upload_mb: int = 25

    @property
    def voices_dir(self) -> Path:
        p = Path(self.data_dir) / "voices"
        p.mkdir(parents=True, exist_ok=True)
        return p

    @property
    def jobs_dir(self) -> Path:
        p = Path(self.data_dir) / "jobs"
        p.mkdir(parents=True, exist_ok=True)
        return p


@lru_cache
def get_settings() -> Settings:
    return Settings()
