	@echo off

@REM set EXECUTION_COMMAND=mvnw spring-boot:run --quiet
set EXECUTION_COMMAND=java -Xms128M -jar target\receiver-1.0.jar 

call set-env.cmd
	
@REM COPY_FROM_BACKUP is a *-separated list of numbers that define threads count * iterations count per thread.

@REM set URL_TO_EXECUTE_AFTER_STARTUP="http://localhost:8080/urbangeopulse/api/simulator/streets/points?saveToBackup=true"
@REM set PEOPLE_GEO_LOCATIONS_CSV=NYC_people-geo-locations--all.csv
@REM set COPY_FROM_BACKUP=1*1
@REM set TIME_TO_WAIT_IN_SEC=900
@REM set COPY_FROM_BACKUP=1*3
@REM set TIME_TO_WAIT_IN_SEC=2200
@REM set COPY_FROM_BACKUP=1*5
@REM set TIME_TO_WAIT_IN_SEC=3600
@REM set COPY_FROM_BACKUP=1*10
@REM set TIME_TO_WAIT_IN_SEC=7200
@REM set COPY_FROM_BACKUP=10*1
@REM set TIME_TO_WAIT_IN_SEC=6500
@REM set COPY_FROM_BACKUP=1*20
@REM set TIME_TO_WAIT_IN_SEC=13500
@REM set COPY_FROM_BACKUP=1*30
@REM set TIME_TO_WAIT_IN_SEC=20000

@REM set URL_TO_EXECUTE_AFTER_STARTUP="http://localhost:8080/urbangeopulse/api/simulator/streets/points?streetName=Duffield St&iterationsCount=10&saveToBackup=true"
set PEOPLE_GEO_LOCATIONS_CSV=NYC_people-geo-locations--Duffield_St.csv
set COPY_FROM_BACKUP=1*1
@REM Note: Adjust TIME_TO_WAIT_IN_SEC to the intervals in query-agg_activity.sql and delete-from-agg_activity.sql
set TIME_TO_WAIT_IN_SEC=120
@REM set COPY_FROM_BACKUP=1*1000
@REM set TIME_TO_WAIT_IN_SEC=1050
@REM set COPY_FROM_BACKUP=1*10000
@REM set TIME_TO_WAIT_IN_SEC=5500
@REM set COPY_FROM_BACKUP=5*2000
@REM set TIME_TO_WAIT_IN_SEC=4500
@REM set COPY_FROM_BACKUP=1*20000
@REM set TIME_TO_WAIT_IN_SEC=11000

copy "%PEOPLE_GEO_LOCATIONS_CSV%" people-geo-locations.csv

@echo on
pushd ..\..\scripts\development\
call init-sql.cmd
popd

start /B %EXECUTION_COMMAND%
	
timeout /t %TIME_TO_WAIT_IN_SEC% >nul
	
@echo off
	
del people-geo-locations.csv

pushd ..\..\scripts\development
call query-sql.cmd
popd