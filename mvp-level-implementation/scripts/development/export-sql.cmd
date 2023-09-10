    call set-sql-env.cmd
	call set-log-env.cmd

	copy select-from-agg_x_activity.sql %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
	@echo on
	docker exec -i %PG_CONTAINER_ID% psql --dbname=nyc --file=/var/lib/postgresql/data/urbangeopulse/select-from-agg_x_activity.sql --username=user --output=/var/lib/postgresql/data/urbangeopulse/select-from-agg_x_activity.out
	@echo off
	move %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse\select-from-agg_x_activity.out "%LOG_FOLDER%"
	start /B notepad++ "%LOG_FOLDER%\select-from-agg_x_activity.out"

	REM pause
	timeout /t 5 >nul
	
	REM docker exec -it %PG_CONTAINER_ID% bash
	REM docker exec -it %PG_CONTAINER_ID% pg_resetxlog -D /var/lib/postgresql/data
	
	