	@echo off

	REM set EXECUTION_COMMAND=mvnw spring-boot:run --quiet
	set EXECUTION_COMMAND=java -Xms128M -jar target\activity-aggregator-1.0.jar 

	REM 4 activity-aggregator services should be started, for 4 topics: pedestrians_streets, pedestrians_neighborhoods, mobilized_streets and mobilized_neighborhoods.
	REM The number of partitions and consumer threads per topic is configurable using the environment variable ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT.
    
	set POSTGIS_SERVER_HOST_NAME=localhost
	set POSTGIS_SERVER_PORT=5433
    REM set ACTIVITY_AGGREGATOR_AUTO_OFFSET_RESET_CONFIG=earliest
	REM set ACTIVITY_AGGREGATOR_DEBUG_TRIGGER_REBALANCING_ON_STARTUP_AFTER_MINUTES=1
	REM set ACTIVITY_AGGREGATOR_PERSISTENCE_MAX_RECORDS=100
	
	REM Note: ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT also configures the partitions count of the relevant topics (below): pedestrians_streets, pedestrians_neighborhoods, mobilized_streets, mobilized_neighborhoods.
	
	@echo on

	set ACTIVITY_AGGREGATOR_MAX_POLL_INTERVAL_MINUTES_CONFIG=10
	set ACTIVITY_AGGREGATOR_SESSION_TIMEOUT_SECONDS_CONFIG=120
	
	set ACTIVITY_AGGREGATOR_MOBILITY_TYPE=pedestrians
	set ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC=20
    set ACTIVITY_AGGREGATOR_LOCATION_TYPE=street
	set ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT=30
	for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%
	set ACTIVITY_AGGREGATOR_PERSISTENCE_INTERVAL_SEC=

    set ACTIVITY_AGGREGATOR_LOCATION_TYPE=neighborhood
	set ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT=10
	for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%

	
    set ACTIVITY_AGGREGATOR_MOBILITY_TYPE=mobilized
    set ACTIVITY_AGGREGATOR_LOCATION_TYPE=street
	set ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT=8
	for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%

    set ACTIVITY_AGGREGATOR_LOCATION_TYPE=neighborhood
	set ACTIVITY_AGGREGATOR_CONSUMER_THREADS_COUNT=2
	for /l %%i in (1,1,1) do start /B  %EXECUTION_COMMAND%
