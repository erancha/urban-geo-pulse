	set SERVICE_NAME=%1%
	set LOG_FILE="%LOG_FOLDER%/%SERVICE_NAME%-service.out"

	cd ../../services/%SERVICE_NAME%
	start /B start-service.cmd >> %LOG_FILE%
	cd ../../scripts/development
	
	start /B PowerShell Get-Content -Tail 10 -Wait -Path ""%LOG_FILE%""
	if /I "%2%"=="openlog" (
		start /B notepad++ %LOG_FILE%
	)	
