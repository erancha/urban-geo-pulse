@echo off
echo Deploying application services...

docker stack deploy --compose-file=docker-compose-app-receiver.yml --with-registry-auth app

docker stack deploy --compose-file=docker-compose-app-mobilization-classifier.yml --with-registry-auth app

@REM Reads each line from the following *.env files (which contain cross-service environment variables) and sets them as environment variables in the current CMD shell
for /f "tokens=*" %%a in (./shared-env/locations-finder-vars-cross-service.env) do (
    set "%%a"
)
for /f "tokens=*" %%a in (./shared-env/activity-aggregator-vars-cross-service.env) do (
    set "%%a"
)

@REM Substitute environment variables in the compose file

powershell -Command "(Get-Content docker-compose-app-locations-finder.yml) | ForEach-Object { [Environment]::ExpandEnvironmentVariables($_) } | Set-Content docker-compose-app-locations-finder.tmp.yml"
docker stack deploy --compose-file=docker-compose-app-locations-finder.tmp.yml --with-registry-auth app
del docker-compose-app-locations-finder.tmp.yml

@REM @REM TODO: Missing manifest in the jar file ..?!
@REM @REM docker stack deploy --compose-file=docker-compose-app-delay-manager.yml --with-registry-auth app

@REM Substitute environment variables in the compose file:

set COMPOSE_FILE=docker-compose-app-activity-aggregator-mongodb.yml
@REM set COMPOSE_FILE=docker-compose-app-activity-aggregator-postgres.yml
powershell -Command "(Get-Content %COMPOSE_FILE%) | ForEach-Object { [Environment]::ExpandEnvironmentVariables($_) } | Set-Content docker-compose-app-activity-aggregator.tmp.yml"
docker stack deploy --compose-file=docker-compose-app-activity-aggregator.tmp.yml --with-registry-auth app
del docker-compose-app-activity-aggregator.tmp.yml

set COMPOSE_FILE=docker-compose-app-info-mongodb.yml
@REM set COMPOSE_FILE=docker-compose-app-info-postgres.yml
docker stack deploy --compose-file=%COMPOSE_FILE% --with-registry-auth app

echo.
echo Application services deployment complete.
timeout /t 5 >nul
@REM pause