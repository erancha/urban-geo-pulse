	set PG_CONTAINER_FOLDER="%LOCALAPPDATA%"
	REM set PG_CONTAINER_FOLDER=D:\
	
	if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
		mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
	)
	set PG_CONTAINER_ID=d95fff61bb78954bd4d745ce47ca856847aa3231387d8eb555a94ff5229fd106
