from typing import List

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.api.deps import require_api_key
from app.models.database import get_db, VoiceProfile
from app.models.schemas import VoiceProfileOut
from app.services.voice_service import validate_and_store_sample, delete_voice_files

router = APIRouter(prefix="/voices", tags=["voices"], dependencies=[Depends(require_api_key)])


@router.post("", response_model=VoiceProfileOut)
async def create_voice(
    name: str = Form(...),
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    data = await file.read()
    voice = validate_and_store_sample(name=name, filename=file.filename, data=data)
    db.add(voice)
    db.commit()
    db.refresh(voice)
    return voice


@router.get("", response_model=List[VoiceProfileOut])
def list_voices(db: Session = Depends(get_db)):
    return db.query(VoiceProfile).order_by(VoiceProfile.created_at.desc()).all()


@router.get("/{voice_id}", response_model=VoiceProfileOut)
def get_voice(voice_id: str, db: Session = Depends(get_db)):
    voice = db.get(VoiceProfile, voice_id)
    if not voice:
        raise HTTPException(404, "Voice profile not found")
    return voice


@router.get("/{voice_id}/sample")
def play_voice_sample(voice_id: str, db: Session = Depends(get_db)):
    voice = db.get(VoiceProfile, voice_id)
    if not voice:
        raise HTTPException(404, "Voice profile not found")
    return FileResponse(voice.sample_path)


@router.delete("/{voice_id}")
def delete_voice(voice_id: str, db: Session = Depends(get_db)):
    voice = db.get(VoiceProfile, voice_id)
    if not voice:
        raise HTTPException(404, "Voice profile not found")
    delete_voice_files(voice)
    db.delete(voice)
    db.commit()
    return {"deleted": True}
