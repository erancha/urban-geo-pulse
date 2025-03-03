@echo off

set PG_CONTAINER_FOLDER="%LOCALAPPDATA%"
@REM set PG_CONTAINER_FOLDER=D:\

if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
	mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
)

set PG_CONTAINER_ID=71d9910b1d9b1c635fafd94058ecc42edd9ddbad1647a145b31a3b286e4241d4
