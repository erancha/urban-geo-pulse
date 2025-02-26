@echo off

	set PG_CONTAINER_FOLDER="%LOCALAPPDATA%"
	@REM set PG_CONTAINER_FOLDER=D:\
	
	if not exist %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse (
		mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
	)
	set PG_CONTAINER_ID=fb956c0875bade296bf4390932a4962d56bf1c4da293a7ad3baaedb3f348473a
