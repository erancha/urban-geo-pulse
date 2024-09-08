	set PG_CONTAINER_FOLDER=%LOCALAPPDATA%
	REM set PG_CONTAINER_FOLDER=D:\
	
	if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
		mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
	)
	set PG_CONTAINER_ID=8c0f2f6b926e142ab7674b256caa01ef0314d101490bb3bdc940bf291955785c
