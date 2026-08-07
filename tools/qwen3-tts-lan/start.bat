@echo off
title RikkaHub LAN Qwen3-TTS Starter
cd /d "%~dp0"

echo [1/4] Checking Docker...
docker --version >nul 2>nul
if errorlevel 1 (
  echo Docker is not installed or not running.
  echo Please install Docker Desktop first: https://www.docker.com/products/docker-desktop/
  echo.
  echo Alternative: install Python 3.12+, then run:
  echo   pip install -r requirements.txt
  echo   python -m uvicorn server.main:app --host 0.0.0.0 --port 8877
  pause
  exit /b 1
)

echo [2/4] Building image (first run may take a while)...
docker compose build
if errorlevel 1 (
  echo Build failed. If you have no NVIDIA GPU, edit docker-compose.yml
  echo and remove the "deploy.resources" GPU section, then retry.
  pause
  exit /b 1
)

echo [3/4] Starting Qwen3-TTS service...
docker compose up -d
if errorlevel 1 (
  echo Failed to start service. Please check Docker Desktop / NVIDIA drivers.
  pause
  exit /b 1
)

echo [4/4] Done.
echo.
echo Next steps:
echo - Run "ipconfig" to find your IPv4 address (example: 192.168.1.100)
echo - In app: Settings -^> Speech -^> LAN Qwen3 TTS
echo - Fill: http://YOUR_IP:8877
echo - Wait for model download on first start (check: docker logs -f qwen3-tts-lan)
echo.
pause
