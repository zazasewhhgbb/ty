from fastapi import APIRouter

from app.config.settings import get_settings

router = APIRouter(tags=["health"])
settings = get_settings()


@router.get("/health")
def health():
    device = "cuda" if _cuda_available() else "cpu"
    return {"status": "ok", "model": settings.model_name, "requested_device": settings.device, "cuda_available": _cuda_available()}


def _cuda_available() -> bool:
    try:
        import torch
        return torch.cuda.is_available()
    except Exception:
        return False
