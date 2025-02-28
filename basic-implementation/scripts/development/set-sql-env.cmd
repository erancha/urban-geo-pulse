@echo off

set PG_CONTAINER_FOLDER="%LOCALAPPDATA%"
@REM set PG_CONTAINER_FOLDER=D:\

if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
	mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
)

set PG_CONTAINER_ID=03396bf6eba851f5fe6474c3ec6a6212fe296dab639a6a64d98a23e7a1a55ccf
