	docker-compose up -d
	timeout /t 10 >nul
	docker-compose ps

	REM pause