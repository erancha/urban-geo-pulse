@echo off

set PG_CONTAINER_FOLDER="%LOCALAPPDATA%"
@REM set PG_CONTAINER_FOLDER=D:\

if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
	mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
)

set PG_CONTAINER_ID=3ba509306dc18ae8fc915ab28fc5dc646adf907b076df938b5e4b8ad0c0106ee
