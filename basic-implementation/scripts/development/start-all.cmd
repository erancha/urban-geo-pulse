@echo off

@REM Initialize environment
call set-sql-env.cmd
call set-log-env.cmd

@REM Start services in order
call start-a-service receiver                openlog
call start-a-service mobilization-classifier
call start-a-service locations-finder
@REM call start-a-service delay-manager
call start-a-service activity-aggregator
call start-a-service info

@REM Wait for services to initialize
timeout /t 5 >nul

@REM Performance analysis:
@REM ---------------------
@REM 	1. RECEIVER_THROTTLE_PRODUCING_THROUGHPUT=1250 
@REM 			+ receiver + mobilization-classifier services, 
@REM 			+ PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,10 : Almost no lag in mobilization-classifier-cg.
@REM	2. Same, with PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,15 ==> Huge lag    (~70,000 records).
@REM	3. Same, with PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,10 ==> Average lag (~10,000 records).
@REM	4. Same, with PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,7  ==> Higher lag  (~30,000 records).

@REM	5. RECEIVER_THROTTLE_PRODUCING_THROUGHPUT=500 + all services: 	==> Substantial lag (~25,000 records).
@REM 	6. Same, with PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,8	==> Average lag (~10,000 records), elapsed (aggregator): 4:51 minutes
@REM    7. Same, with PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,7 ==> Higher lag  (~25,000 records), elapsed (aggregator): 4:54 minutes