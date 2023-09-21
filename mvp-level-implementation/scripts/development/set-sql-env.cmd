	set PG_CONTAINER_FOLDER=%LOCALAPPDATA%
	REM set PG_CONTAINER_FOLDER=D:\
	
	if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
		mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
	)
	set PG_CONTAINER_ID=d789ff49e1b7a1cf53274ebb9ac5330e662ca773b451bd1c40ef7018856009dc
