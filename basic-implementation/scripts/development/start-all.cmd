	@REM call start-3rd-party-stack.cmd

	call set-sql-env.cmd
	REM copy delete-from-agg_x_activity.sql %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
	REM docker exec -i %PG_CONTAINER_ID% psql --dbname=nyc --file=/var/lib/postgresql/data/urbangeopulse/delete-from-agg_x_activity.sql --username=user --output=/var/lib/postgresql/data/urbangeopulse/delete-from-agg_x_activity.out

	call set-log-env.cmd
	
	call start-a-service receiver 					openlog
	call start-a-service mobilization-classifier
	call start-a-service locations-finder
	@REM call start-a-service activity-aggregator
	@REM call start-a-service info 	
	@REM call start-a-service delay-manager

	REM pause
	timeout /t 5 >nul


	@REM services																					througput 
	@REM ----------------------------------------------------------------------------------         -------------------------------------------------------------
	@REM receiver (1 partition)  + mobilization-classifier (1 threads) 								< 1,250 (huge mobilization-classifier lag ~ up to 70,000)
	@REM receiver (5 partition)  + mobilization-classifier (5 threads) 								< 1,250 (mobilization-classifier lag ~ 12,000)
	@REM receiver (10 partition) + mobilization-classifier (10 threads)			  					  1,250 (~ no lag)
	@REM receiver (10 partition) + mobilization-classifier (10 threads)								< 1,500 (mobilization-classifier lag ~ 18,000, 'development-kafka-broker-1' ~100% CPU, Memory ~1GB)
	@REM receiver (20 partition) + mobilization-classifier (20 threads)			  					  1,500 (~ no lag)
	@REM receiver (20 partition) + mobilization-classifier (20 threads)	+ locations-finder			< 1,000 (huge mobilization-classifier lag ~ up to 50,000)