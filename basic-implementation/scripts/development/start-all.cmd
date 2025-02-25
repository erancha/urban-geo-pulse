	@REM call start-3rd-party-stack.cmd

	call set-sql-env.cmd
	REM copy delete-from-agg_x_activity.sql %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
	REM docker exec -i %PG_CONTAINER_ID% psql --dbname=nyc --file=/var/lib/postgresql/data/urbangeopulse/delete-from-agg_x_activity.sql --username=user --output=/var/lib/postgresql/data/urbangeopulse/delete-from-agg_x_activity.out

	call set-log-env.cmd
	
	call start-a-service receiver 				openlog
	call start-a-service mobilization-sorter
	call start-a-service locations-finder
	call start-a-service activity-aggregator
	call start-a-service info 	
	call start-a-service delay-manager

	REM pause
	timeout /t 5 >nul
