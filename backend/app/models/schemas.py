import datetime
from typing import Optional

from pydantic import BaseModel


class VoiceProfileOut(BaseModel):
    id: str
    name: str
    duration_seconds: float
    model_name: str
    created_at: datetime.datetime

    class Config:
        from_attributes = True


class GenerateRequest(BaseModel):
    voice_id: str
    text: str
    language: str = "en"
    speed: float = 1.0
    output_format: str = "mp3"


class JobStatusOut(BaseModel):
    job_id: str
    status: str
    progress: int
    current_chunk: int
    total_chunks: int
    error_message: Optional[str] = None

    class Config:
        from_attributes = True
