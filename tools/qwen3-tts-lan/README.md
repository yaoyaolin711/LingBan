# RikkaHub LAN Qwen3-TTS

Run Qwen3-TTS on your PC so the Android app can synthesize speech over LAN.

## Quick start

**Windows:** double-click `start.bat`  
**macOS / Linux:** `./start.sh`

Then open:

`Settings → Speech → LAN Qwen3 TTS`

Fill: `http://YOUR_LAN_IP:8877`

Full beginner guide: [docs/tts/lan-qwen3-tts.md](../../docs/tts/lan-qwen3-tts.md)

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Service + model readiness |
| POST | `/v1/tts/speech` | Synthesize speech (WAV/PCM) |

Example:

```bash
curl -X POST http://127.0.0.1:8877/v1/tts/speech \
  -H "Content-Type: application/json" \
  -d '{"input":"你好","mode":"custom_voice","speaker":"Vivian","language":"Chinese","response_format":"wav"}' \
  --output out.wav
```

## Env vars

| Var | Default | Notes |
|-----|---------|-------|
| `PORT` | `8877` | Listen port |
| `MODEL_SIZE` | `1.7b` | `1.7b` or `0.6b` |
| `MODEL_MODE` | `custom_voice` | `custom_voice` or `voice_design` |
| `MODEL_ID` | (auto) | Override HuggingFace / ModelScope id |

## Without Docker

```bash
pip install -r requirements.txt
python -m uvicorn server.main:app --host 0.0.0.0 --port 8877
```
