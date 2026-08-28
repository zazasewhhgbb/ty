import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import health, jobs, voices
from app.models.database import init_db, SessionLocal, GenerationJob
from app.jobs.job_manager import resume_job

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="Self-Hosted Voice Generator", version="0.1.0")

# CORS is permissive here because the Android app talks to this server
# directly with a bearer token, not from a browser origin. Tighten this
# if you ever add a web client.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(voices.router)
app.include_router(jobs.router)


@app.on_event("startup")
def on_startup():
    init_db()
    # Resume any jobs that were mid-flight when the server last stopped
    # (spec section 18 — resumable generation across server restarts).
    db = SessionLocal()
    try:
        unfinished = (
            db.query(GenerationJob)
            .filter(GenerationJob.status.in_(["queued", "processing"]))
            .all()
        )
        for job in unfinished:
            resume_job(job.id)
    finally:
        db.close()
