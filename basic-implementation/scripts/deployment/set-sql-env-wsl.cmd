@echo off

set PG_CONTAINER_FOLDER=%~dp0..\..
if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
    echo Creating folder %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
    mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
)

REM Get container ID dynamically using docker ps
for /f "tokens=1" %%i in ('docker ps --filter "name=urban-geo-pulse-3rdparty_postgis-server-nyc" --format "{{.ID}}"') do set PG_CONTAINER_ID=%%i
