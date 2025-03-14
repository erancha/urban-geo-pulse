call set-sql-env.cmd
call set-log-env.cmd

@echo on
copy query-agg_activity.sql %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
docker exec -i %PG_CONTAINER_ID% psql --dbname=nyc --file=/var/lib/postgresql/data/urbangeopulse/query-agg_activity.sql --username=user --output=/var/lib/postgresql/data/urbangeopulse/query-agg_activity.out
move %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse\query-agg_activity.out %LOG_FOLDER%
start /B notepad++ %LOG_FOLDER%\query-agg_activity.out
@echo off

@REM pause
timeout /t 5 >nul
