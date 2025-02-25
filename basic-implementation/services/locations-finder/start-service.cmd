	@echo off

	REM set EXECUTION_COMMAND=mvnw spring-boot:run --quiet
	set EXECUTION_COMMAND=java -Xms128M -jar target\locations-finder-1.0.jar 

	REM   4 locations-finder services are started for 2 topics (pedestrians_geo_locations and mobilized_geo_locations).
	REM   The total number of partitions (2 topics * LOCATIONS_FINDER_MOBILITY_TYPE_PARTITIONS_COUNT partitions) should be >= total number of threads (4 services * LOCATIONS_FINDER_CONSUMER_THREADS_COUNT), 
	REM   otherwise a few consumers will not be assigned to any partition, i.e. will be useless.

	REM set LOCATIONS_FINDER_INPUT_SRID=26918
	set LOCATIONS_FINDER_AUTO_OFFSET_RESET_CONFIG=earliest
	REM set LOCATIONS_FINDER_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES=3

	call ../delay-manager/set-env.cmd
	set LOCATIONS_FINDER_DELAY_FOR_MISSING_CITY_IN_SEC=120
	set POSTGIS_SERVER_HOST_NAME=localhost
	set POSTGIS_SERVER_PORT=5433

	REM Note: LOCATIONS_FINDER_MOBILITY_TYPE_PARTITIONS_COUNT partitions = 
	REM 			LOCATIONS_FINDER_CONSUMER_THREADS_COUNT consumers + 
	REM 			LOCATIONS_FINDER_CONSUMER_THREADS_COUNT consumers 
	REM -------------------------------------------------------------------

	@echo on

	set LOCATIONS_FINDER_SESSION_TIMEOUT_SECONDS_CONFIG=120
	
	
    set LOCATIONS_FINDER_MOBILITY_TYPE=pedestrians
	set LOCATIONS_FINDER_MOBILITY_TYPE_PARTITIONS_COUNT=40

    set LOCATIONS_FINDER_LOCATION_TYPE=street
	set LOCATIONS_FINDER_CONSUMER_THREADS_COUNT=20
	for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%
	
    set LOCATIONS_FINDER_LOCATION_TYPE=neighborhood
	set LOCATIONS_FINDER_CONSUMER_THREADS_COUNT=20
    for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%

	
    set LOCATIONS_FINDER_MOBILITY_TYPE=mobilized
	set LOCATIONS_FINDER_MOBILITY_TYPE_PARTITIONS_COUNT=10

    set LOCATIONS_FINDER_LOCATION_TYPE=street
	set LOCATIONS_FINDER_CONSUMER_THREADS_COUNT=5
 	for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%

    set LOCATIONS_FINDER_LOCATION_TYPE=neighborhood
	set LOCATIONS_FINDER_CONSUMER_THREADS_COUNT=5
	for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%
