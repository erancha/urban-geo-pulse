@echo off

@REM set EXECUTION_COMMAND=mvnw spring-boot:run --quiet
set EXECUTION_COMMAND=java -Xms128M -jar target\receiver-1.0.jar

@REM !!TIME_TO_WAIT_BEFORE_SQL_QUERY_IN_SEC!!: Must be aligned with the intervals in query-agg_activity.sql and delete-from-agg_activity.sql

@REM set URL_TO_EXECUTE_AFTER_STARTUP="http://localhost:8080/urbangeopulse/api/simulator/streets/points?streetName=Duffield St&iterationsCount=10&saveToBackup=true"

@REM set PEOPLE_GEO_LOCATIONS_CSV=NYC_people-geo-locations--Duffield_St.csv
@REM set ITERATIONS_TO_SIMULATE_FROM_BACKUP=10
@REM set TIME_TO_WAIT_BEFORE_SQL_QUERY_IN_SEC=90

@REM set URL_TO_EXECUTE_AFTER_STARTUP="http://localhost:8080/urbangeopulse/api/simulator/streets/points?saveToBackup=true"

set PEOPLE_GEO_LOCATIONS_CSV=NYC_people-geo-locations--all.csv
@REM set ITERATIONS_TO_SIMULATE_FROM_BACKUP=1
set TIME_TO_WAIT_BEFORE_SQL_QUERY_IN_SEC=420

set RECEIVER_THROTTLE_PRODUCING_THROUGHPUT=500

pushd ..\..\scripts\development\
call init-sql.cmd
popd

@echo on

call ../mobilization-classifier/set-env.cmd
time /T
copy "%PEOPLE_GEO_LOCATIONS_CSV%" people-geo-locations.csv
start /B %EXECUTION_COMMAND%
timeout /t %TIME_TO_WAIT_BEFORE_SQL_QUERY_IN_SEC% >nul

@echo off

del people-geo-locations.csv

pushd ..\..\scripts\development
call query-sql.cmd
popd