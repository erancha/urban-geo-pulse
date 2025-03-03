@echo off

set PG_CONTAINER_FOLDER="%LOCALAPPDATA%"
@REM set PG_CONTAINER_FOLDER=D:\

if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
	mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
)

set PG_CONTAINER_ID=647c78a9b913a0fdcb4e1e85df09506c57e3ec7a7b294299c6b1a34bb96fcc00
