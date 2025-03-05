@echo off

cd /d "%~dp0"
for /f %%i in ('docker-compose ps -q') do set HAS_CONTAINERS=true
if defined HAS_CONTAINERS (
    echo Third-party services are already running
) else (
    echo Starting third-party services...
    docker-compose up -d
    docker-compose ps
    timeout /t 5 >nul
)