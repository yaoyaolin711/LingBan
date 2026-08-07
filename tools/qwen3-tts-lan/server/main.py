"""
RikkaHub LAN Qwen3-TTS server.

Protocol (compatible with Android LanTtsClient / Qwen3LocalTTSProvider):
  GET  /health
  POST /v1/tts/speech
"""

from __future__ import annotations

import io
import os
import traceback
from typing import Any, Optional

import numpy as np
import soundfile as sf
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from pydantic import BaseModel, Field

PORT = int(os.environ.get("PORT", "8877"))
MODEL_SIZE = os.environ.get("MODEL_SIZE", "1.7b").lower()  # 1.7b | 0.6b
MODEL_MODE = os.environ.get("MODEL_MODE", "custom_voice").lower()  # custom_voice | voice_design
MODEL_ID = os.environ.get("MODEL_ID", "").strip()

# Official CustomVoice speakers (Qwen3-TTS-12Hz)
DEFAULT_SPEAKERS = [
    "Vivian",
    "Serena",
    "Uncle_Fu",
    "Dylan",
    "Eric",
    "Ryan",
    "Aiden",
    "Ono_Anna",
    "Sohee",
]

SAMPLE_RATE = 24000

app = FastAPI(title="RikkaHub LAN Qwen3-TTS", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_model: Any = None
_speakers: list[str] = list(DEFAULT_SPEAKERS)
_loaded_model_id: Optional[str] = None
_load_error: Optional[str] = None


def resolve_model_id() -> str:
    if MODEL_ID:
        return MODEL_ID
    if MODEL_MODE == "voice_design":
        return "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign"
    if MODEL_SIZE.startswith("0.6"):
        return "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice"
    return "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice"


def ensure_model():
    global _model, _speakers, _loaded_model_id, _load_error
    if _model is not None:
        return _model
    if _load_error:
        raise HTTPException(status_code=503, detail=f"Model load failed: {_load_error}")

    model_id = resolve_model_id()
    try:
        from qwen_tts import Qwen3TTSModel  # type: ignore

        print(f"[qwen3-tts-lan] Loading model: {model_id}")
        _model = Qwen3TTSModel.from_pretrained(
            model_id,
            device_map="auto",
            dtype="auto",
        )
        _loaded_model_id = model_id
        try:
            speakers = list(_model.get_supported_speakers())
            if speakers:
                _speakers = speakers
        except Exception:
            pass
        print(f"[qwen3-tts-lan] Model ready. speakers={_speakers}")
        return _model
    except Exception as e:
        _load_error = f"{e}\n{traceback.format_exc()}"
        print(f"[qwen3-tts-lan] Model load failed:\n{_load_error}")
        raise HTTPException(status_code=503, detail=f"Model load failed: {e}") from e


class SpeechRequest(BaseModel):
    input: str = Field(..., min_length=1, description="Text to synthesize")
    mode: str = Field(default="custom_voice", description="custom_voice | voice_design")
    speaker: str = Field(default="Vivian")
    language: str = Field(default="Auto")
    instruct: str = Field(default="")
    voice_description: str = Field(default="")
    response_format: str = Field(default="wav", description="wav | pcm | mp3")
    speed: float = Field(default=1.0, ge=0.5, le=2.0)


class HealthInfo(BaseModel):
    status: str
    models: dict[str, Optional[str]]
    speakers: list[str]
    sample_rate: int
    mode: str
    model_size: str


@app.on_event("startup")
def on_startup():
    # Eager load so /health reflects readiness; failures stay in _load_error.
    try:
        ensure_model()
    except Exception:
        pass


@app.get("/health")
def health() -> HealthInfo:
    ready = _model is not None and _load_error is None
    model_id = _loaded_model_id or resolve_model_id()
    return HealthInfo(
        status="ok" if ready else ("loading" if _load_error is None else "error"),
        models={
            "custom_voice": model_id if MODEL_MODE == "custom_voice" else None,
            "voice_design": model_id if MODEL_MODE == "voice_design" else None,
            "active": model_id if ready else None,
            "error": _load_error,
        },
        speakers=_speakers,
        sample_rate=SAMPLE_RATE,
        mode=MODEL_MODE,
        model_size=MODEL_SIZE,
    )


def audio_to_bytes(wav: np.ndarray, sample_rate: int, fmt: str) -> tuple[bytes, str]:
    fmt = (fmt or "wav").lower()
    if wav.ndim > 1:
        wav = wav.squeeze()
    if fmt == "pcm":
        # 16-bit little-endian PCM
        clipped = np.clip(wav, -1.0, 1.0)
        pcm = (clipped * 32767.0).astype(np.int16)
        return pcm.tobytes(), "audio/L16"
    if fmt == "mp3":
        # Prefer WAV if mp3 encoder unavailable; keep contract simple.
        buf = io.BytesIO()
        sf.write(buf, wav, sample_rate, format="WAV")
        return buf.getvalue(), "audio/wav"
    buf = io.BytesIO()
    sf.write(buf, wav, sample_rate, format="WAV")
    return buf.getvalue(), "audio/wav"


@app.post("/v1/tts/speech")
def synthesize(req: SpeechRequest):
    text = req.input.strip()
    if not text:
        raise HTTPException(status_code=422, detail="input must not be empty")

    model = ensure_model()
    mode = (req.mode or MODEL_MODE).lower()

    try:
        if mode == "voice_design":
            instruct = (req.voice_description or req.instruct or "").strip()
            if not instruct:
                raise HTTPException(
                    status_code=400,
                    detail="voice_description is required for voice_design mode",
                )
            wavs, sr = model.generate_voice_design(
                text=text,
                language=req.language or "Auto",
                instruct=instruct,
            )
        else:
            speaker = req.speaker or "Vivian"
            kwargs: dict[str, Any] = {
                "text": text,
                "language": req.language or "Auto",
                "speaker": speaker,
            }
            if req.instruct.strip():
                kwargs["instruct"] = req.instruct.strip()
            wavs, sr = model.generate_custom_voice(**kwargs)

        wav = wavs[0] if isinstance(wavs, (list, tuple)) else wavs
        audio_bytes, media_type = audio_to_bytes(np.asarray(wav), int(sr or SAMPLE_RATE), req.response_format)
        return Response(
            content=audio_bytes,
            media_type=media_type,
            headers={
                "X-Sample-Rate": str(int(sr or SAMPLE_RATE)),
                "X-Response-Format": req.response_format.lower(),
            },
        )
    except HTTPException:
        raise
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"TTS failed: {e}") from e


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=PORT)
