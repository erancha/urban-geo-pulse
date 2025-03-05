@echo off

@REM pushd ..\..\scripts\development\
@REM call init-sql.cmd
@REM popd

@REM set EXECUTION_COMMAND=mvnw spring-boot:run --quiet
set EXECUTION_COMMAND=java -Xms128M -jar target\receiver-1.0.jar

@REM !!TIME_TO_WAIT_BEFORE_SQL_QUERY_IN_SEC!!: Must be aligned with the intervals in query-agg_activity.sql and delete-from-agg_activity.sql

@echo on

@REM set URL_TO_EXECUTE_AFTER_STARTUP="http://localhost:8080/urbangeopulse/api/simulator/streets/points?streetName=Duffield St&iterationsCount=10&saveToBackup=true"

set PEOPLE_GEO_LOCATIONS_CSV=NYC_people-geo-locations--Duffield_St.csv
@REM set ITERATIONS_TO_SIMULATE_FROM_BACKUP=10
set TIME_TO_WAIT_BEFORE_SQL_QUERY_IN_SEC=180

@REM set URL_TO_EXECUTE_AFTER_STARTUP="http://localhost:8080/urbangeopulse/api/simulator/streets/points?saveToBackup=true"

@REM set PEOPLE_GEO_LOCATIONS_CSV=NYC_people-geo-locations--all.csv
@REM set ITERATIONS_TO_SIMULATE_FROM_BACKUP=1
@REM set TIME_TO_WAIT_BEFORE_SQL_QUERY_IN_SEC=420

set RECEIVER_THROTTLE_PRODUCING_THROUGHPUT=500

call ../mobilization-classifier/set-env.cmd
time /T
start /B %EXECUTION_COMMAND%
timeout /t %TIME_TO_WAIT_BEFORE_SQL_QUERY_IN_SEC% >nul

@echo off

pushd ..\..\scripts\development
call query-sql.cmd
popd


@REM Performance analysis:
@REM ---------------------
@REM  (NYC_people-geo-locations--all.csv  ~120,000 records.)
@REM  ------------------------------------------------------
@REM  #1: RECEIVER_THROTTLE_PRODUCING_THROUGHPUT=1250 
@REM 		, receiver + mobilization-classifier services, 
@REM 		+ PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,10 : Almost no lag in mobilization-classifier-cg.
@REM    (a) Same as #1, with PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,15 ==> Very high lag   (~70,000 records).
@REM    (b) Same as #1, with PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,10 ==> Average lag     (~10,000 records).
@REM    (c) Same as #1, with PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,7  ==> Higher lag      (~30,000 records).

@REM  #2: RECEIVER_THROTTLE_PRODUCING_THROUGHPUT=500 + all services: (elapsed === aggregator)
@REM    (a) With PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,8          ==> Average lag     (~15,000 records), elapsed: ~4:50 minutes (several tests).
@REM    (b) With PEOPLE_GEO_LOCATIONS_TOPIC=people_geo_locations,7          ==> Higher lag      (~25,000 records), elapsed: ~4:55 minutes.

@REM    (c) Same as #2(a) with RECEIVER_THROTTLE_PRODUCING_THROUGHPUT=750   ==> Very high lag   (~60,000 records), elapsed: ~4.55 minutes (several tests).

@REM    (d) Same as #2(a) with RECEIVER_THROTTLE_PRODUCING_THROUGHPUT=1000  ==> Huge lag        (~90,000 records), elapsed: 6:25,6:05 minutes.

@REM  #3: Same as #2(a) *  2 iterations  ==>  low lag (~5-20,000 records),  elapsed ~08:15 minutes (several tests).
@REM  #4: Same as #2(a) *  5 iterations  ==>  low lag (~5-20,000 records),  elapsed ~19:55 minutes (several tests).
@REM  #5: Same as #2(a) * 10 iterations  ==>  ~no lag,                      elapsed ~40 minutes (several tests).
@REM  #6: Same as #2(a) * 20 iterations  ==>  ~no lag,                      elapsed ~80 minutes.
