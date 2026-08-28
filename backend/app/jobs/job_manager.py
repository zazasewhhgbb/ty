"""
Runs generation jobs in background threads (simple, dependency-free choice
for a single-box deployment — swap for Celery/RQ later if you need multiple
worker machines; the interface below wouldn't need to change).

Resumability (spec section 18):
  - Each chunk's audio is written to disk as soon as it succeeds, and its
    row in the `chunks` table is marked "done".
  - If the process restarts mid-job, `resume_job` skips chunks already
    marked "done" and only regenerates the rest.
  - A failed chunk is retried a few times before the whole job is marked
    "failed" (earlier successful chunks are never discarded).
"""
import logging
import threading
from pathlib import Path

from sqlalchemy.orm import Session

from app.audio.chunking import plan_chunks
from app.audio.processing import combine_chunks
from app.config.settings import get_settings
from app.models.database import SessionLocal, GenerationJob, Chunk, VoiceProfile
from app.tts.engine import TTSEngine

logger = logging.getLogger("jobs")
settings = get_settings()
_engine = TTSEngine(model_name=settings.model_name, device=settings.device)

_job_semaphore = threading.Semaphore(settings.max_concurrent_jobs)
_cancel_flags: dict[str, bool] = {}

MAX_ATTEMPTS = 3


def submit_job(job_id: str, text: str):
    """Plans chunks for a freshly-created job, then kicks off a worker thread."""
    db = SessionLocal()
    try:
        job = db.get(GenerationJob, job_id)
        chunk_plan = plan_chunks(text, max_chars=settings.chunk_max_chars)
        for i, chunk_text in enumerate(chunk_plan.chunks):
            db.add(Chunk(job_id=job_id, index=i, text=chunk_text, status="pending"))
        job.total_chunks = len(chunk_plan.chunks)
        db.commit()
    finally:
        db.close()

    thread = threading.Thread(target=_run_job, args=(job_id,), daemon=True)
    thread.start()


def resume_job(job_id: str):
    """Call on server startup for any job left in 'processing' or 'queued'."""
    thread = threading.Thread(target=_run_job, args=(job_id,), daemon=True)
    thread.start()


def cancel_job(job_id: str):
    _cancel_flags[job_id] = True


def _run_job(job_id: str):
    with _job_semaphore:
        db: Session = SessionLocal()
        try:
            job = db.get(GenerationJob, job_id)
            voice = db.get(VoiceProfile, job.voice_id)
            job.status = "processing"
            db.commit()

            chunks = (
                db.query(Chunk)
                .filter(Chunk.job_id == job_id)
                .order_by(Chunk.index)
                .all()
            )
            chunk_dir = Path(settings.jobs_dir) / job_id / "chunks"
            chunk_dir.mkdir(parents=True, exist_ok=True)

            for chunk in chunks:
                if _cancel_flags.get(job_id):
                    job.status = "cancelled"
                    db.commit()
                    return

                if chunk.status == "done" and chunk.audio_path and Path(chunk.audio_path).exists():
                    job.current_chunk = chunk.index + 1
                    job.progress = int(100 * job.current_chunk / max(job.total_chunks, 1))
                    db.commit()
                    continue  # already generated in a previous run — skip

                out_path = str(chunk_dir / f"{chunk.index:04d}.wav")
                success = False
                last_error = None
                for attempt in range(1, MAX_ATTEMPTS + 1):
                    try:
                        chunk.attempts = attempt
                        _engine.synthesize_chunk(
                            text=chunk.text,
                            speaker_wav=voice.sample_path,
                            language=job.language,
                            out_path=out_path,
                            speed=job.speed,
                        )
                        success = True
                        break
                    except Exception as e:  # noqa: BLE001
                        last_error = str(e)
                        logger.warning("Chunk %s attempt %s failed: %s", chunk.index, attempt, e)

                if not success:
                    chunk.status = "failed"
                    job.status = "failed"
                    job.error_message = f"Chunk {chunk.index} failed after {MAX_ATTEMPTS} attempts: {last_error}"
                    db.commit()
                    return

                chunk.status = "done"
                chunk.audio_path = out_path
                job.current_chunk = chunk.index + 1
                job.progress = int(100 * job.current_chunk / max(job.total_chunks, 1))
                db.commit()

            # all chunks done -> combine
            ordered_paths = [c.audio_path for c in sorted(chunks, key=lambda c: c.index)]
            final_path = str(
                Path(settings.jobs_dir) / job_id / f"final.{job.output_format}"
            )
            combine_chunks(ordered_paths, final_path, output_format=job.output_format)

            job.output_path = final_path
            job.status = "completed"
            job.progress = 100
            db.commit()
        except Exception as e:  # noqa: BLE001
            logger.exception("Job %s crashed", job_id)
            job = db.get(GenerationJob, job_id)
            if job:
                job.status = "failed"
                job.error_message = str(e)
                db.commit()
        finally:
            _cancel_flags.pop(job_id, None)
            db.close()
