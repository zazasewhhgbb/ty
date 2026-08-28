"""
SQLite by default. DATABASE_URL is the only thing that needs to change to
move to Postgres later (SQLAlchemy handles the rest via the ORM layer).
"""
import datetime
import uuid

from sqlalchemy import create_engine, Column, String, Integer, Float, DateTime, ForeignKey, Text
from sqlalchemy.orm import declarative_base, sessionmaker, relationship

from app.config.settings import get_settings

settings = get_settings()

connect_args = {"check_same_thread": False} if settings.database_url.startswith("sqlite") else {}
engine = create_engine(settings.database_url, connect_args=connect_args)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def new_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:12]}"


class VoiceProfile(Base):
    __tablename__ = "voice_profiles"

    id = Column(String, primary_key=True, default=lambda: new_id("vp"))
    name = Column(String, nullable=False)
    sample_path = Column(String, nullable=False)     # original reference audio on disk
    duration_seconds = Column(Float, nullable=False)
    model_name = Column(String, nullable=False)
    embedding_path = Column(String, nullable=True)   # cached speaker latents, if precomputed
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    jobs = relationship("GenerationJob", back_populates="voice", cascade="all, delete-orphan")


class GenerationJob(Base):
    __tablename__ = "generation_jobs"

    id = Column(String, primary_key=True, default=lambda: new_id("job"))
    voice_id = Column(String, ForeignKey("voice_profiles.id"))
    status = Column(String, default="queued")   # queued|processing|completed|failed|cancelled
    progress = Column(Integer, default=0)
    current_chunk = Column(Integer, default=0)
    total_chunks = Column(Integer, default=0)
    input_text_path = Column(String, nullable=True)
    output_path = Column(String, nullable=True)
    error_message = Column(Text, nullable=True)
    language = Column(String, default="en")
    speed = Column(Float, default=1.0)
    output_format = Column(String, default="mp3")
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    voice = relationship("VoiceProfile", back_populates="jobs")
    chunks = relationship("Chunk", back_populates="job", cascade="all, delete-orphan")


class Chunk(Base):
    __tablename__ = "chunks"

    id = Column(Integer, primary_key=True, autoincrement=True)
    job_id = Column(String, ForeignKey("generation_jobs.id"))
    index = Column(Integer, nullable=False)
    text = Column(Text, nullable=False)
    status = Column(String, default="pending")   # pending|done|failed
    audio_path = Column(String, nullable=True)
    attempts = Column(Integer, default=0)

    job = relationship("GenerationJob", back_populates="chunks")


def init_db():
    Base.metadata.create_all(bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
