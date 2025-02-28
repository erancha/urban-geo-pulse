@echo off
call set-sql-env.cmd

@REM rmdir /s /q %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc
@REM mkdir %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc

@REM set SQL_FILE=init.sql
set SQL_FILE=delete-from-agg_activity.sql

@echo on
copy %SQL_FILE% %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
docker exec -i %PG_CONTAINER_ID% psql --dbname=nyc --file=/var/lib/postgresql/data/urbangeopulse/%SQL_FILE% --username=user --output=/var/lib/postgresql/data/urbangeopulse/init-sql.out
@echo off
copy %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse\init-sql.out %TEMP%
start /B notepad++ %TEMP%\init-sql.out

timeout /t 5 >nul