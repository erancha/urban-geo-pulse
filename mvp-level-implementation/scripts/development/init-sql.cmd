    call set-sql-env.cmd

    copy init.sql %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse
    @echo on
    docker exec -i %PG_CONTAINER_ID% psql --dbname=nyc --file=/var/lib/postgresql/data/urbangeopulse/init.sql --username=user --output=/var/lib/postgresql/data/urbangeopulse/init-sql.out
    @echo off
    start /B notepad++ %PG_CONTAINER_FOLDER%\Temp\postgreSQL_nyc\urbangeopulse\init-sql.out

	timeout /t 5 >nul
	REM pause