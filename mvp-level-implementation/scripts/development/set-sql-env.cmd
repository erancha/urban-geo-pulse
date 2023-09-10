	set PG_CONTAINER_FOLDER=%LOCALAPPDATA%
	REM set PG_CONTAINER_FOLDER=D:\
	
	if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
		mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
	)
	set PG_CONTAINER_ID=5f8d7bbaf4bc2ca79040c5f052f5e3326657357295e65a8324195d966044a43e
