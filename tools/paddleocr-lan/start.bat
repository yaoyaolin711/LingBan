@echo off
title RikkaHub LAN OCR Starter
echo [1/3] Checking Docker...
docker --version >nul 2>nul
if errorlevel 1 (
  echo Docker is not installed or not running.
  echo Please install Docker Desktop first: https://www.docker.com/products/docker-desktop/
  pause
  exit /b 1
)

echo [2/3] Starting PaddleOCR service...
docker compose up -d
if errorlevel 1 (
  echo Failed to start service. Please check Docker Desktop.
  pause
  exit /b 1
)

echo [3/3] Done.
echo.
echo Next step:
echo - Run "ipconfig" to find your IPv4 address (example: 192.168.1.100)
echo - In app, fill: http://YOUR_IP:8866
echo.
pause
