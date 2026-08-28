from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.api.deps import require_api_key
from app.jobs.job_manager import submit_job, cancel_job
from app.models.database import get_db, GenerationJob, VoiceProfile
from app.models.schemas import GenerateRequest, JobStatusOut

router = APIRouter(tags=["jobs"], dependencies=[Depends(require_api_key)])


@router.post("/generate", response_model=JobStatusOut)
def generate(req: GenerateRequest, db: Session = Depends(get_db)):
    voice = db.get(VoiceProfile, req.voice_id)
    if not voice:
        raise HTTPException(404, "Voice profile not found")
    if not req.text.strip():
        raise HTTPException(400, "Text is empty")

    job = GenerationJob(
        voice_id=req.voice_id,
        status="queued",
        language=req.language,
        speed=req.speed,
        output_format=req.output_format,
    )
    db.add(job)
    db.commit()
    db.refresh(job)

    submit_job(job.id, req.text)

    return JobStatusOut(
        job_id=job.id, status=job.status, progress=0, current_chunk=0, total_chunks=0
    )


@router.get("/jobs/{job_id}", response_model=JobStatusOut)
def get_job(job_id: str, db: Session = Depends(get_db)):
    job = db.get(GenerationJob, job_id)
    if not job:
        raise HTTPException(404, "Job not found")
    return JobStatusOut(
        job_id=job.id,
        status=job.status,
        progress=job.progress,
        current_chunk=job.current_chunk,
        total_chunks=job.total_chunks,
        error_message=job.error_message,
    )


@router.post("/jobs/{job_id}/cancel")
def cancel(job_id: str, db: Session = Depends(get_db)):
    job = db.get(GenerationJob, job_id)
    if not job:
        raise HTTPException(404, "Job not found")
    cancel_job(job_id)
    return {"cancelling": True}


@router.get("/jobs/{job_id}/audio")
def get_audio(job_id: str, db: Session = Depends(get_db)):
    job = db.get(GenerationJob, job_id)
    if not job:
        raise HTTPException(404, "Job not found")
    if job.status != "completed" or not job.output_path:
        raise HTTPException(409, f"Job is not completed yet (status: {job.status})")
    media_type = "audio/mpeg" if job.output_format == "mp3" else "audio/wav"
    return FileResponse(job.output_path, media_type=media_type)


@router.delete("/jobs/{job_id}")
def delete_job(job_id: str, db: Session = Depends(get_db)):
    job = db.get(GenerationJob, job_id)
    if not job:
        raise HTTPException(404, "Job not found")
    db.delete(job)
    db.commit()
    return {"deleted": True}
