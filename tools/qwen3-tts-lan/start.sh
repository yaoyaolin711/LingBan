#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "[1/4] Checking Docker..."
if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed."
  echo "Alternative: pip install -r requirements.txt && python -m uvicorn server.main:app --host 0.0.0.0 --port 8877"
  exit 1
fi

echo "[2/4] Building image..."
docker compose build

echo "[3/4] Starting Qwen3-TTS service..."
docker compose up -d

echo "[4/4] Done."
echo
echo "Next steps:"
echo "- Find your LAN IP (ifconfig / ip addr)"
echo "- In app: Settings -> Speech -> LAN Qwen3 TTS"
echo "- Fill: http://YOUR_IP:8877"
echo "- First start downloads the model: docker logs -f qwen3-tts-lan"
