@echo off
setlocal enabledelayedexpansion

@REM Optional comma-separated list of services to skip: receiver,mobilization-classifier,locations-finder,activity-aggregator,info
@REM set SKIP_SERVICES=info
@REM set SKIP_SERVICES=receiver,mobilization-classifier,locations-finder,activity-aggregator

@REM Reads each line from the following *.env files (which contain cross-service environment variables) and sets them as environment variables in the current CMD shell
for /f "tokens=*" %%a in (./shared-env/locations-finder-vars-cross-service.env) do (
    set "%%a"
)
for /f "tokens=*" %%a in (./shared-env/activity-aggregator-vars-cross-service.env) do (
    set "%%a"
)

@REM Deploy Mobilization Classifier service
echo ------------------------------------------------------------
echo Deploying application services...
echo -----------------------

echo ,%SKIP_SERVICES%, | findstr /I ",mobilization-classifier," >nul
if %errorlevel% equ 0 (
    echo Skipping 'Mobilization Classifier' service...
) else (
    echo Deploying 'Mobilization Classifier' service...
    docker stack deploy --compose-file=docker-compose-app-mobilization-classifier.yml --with-registry-auth app
)

@REM Deploy Locations Finder service
echo ,%SKIP_SERVICES%, | findstr /I ",locations-finder," >nul
if %errorlevel% equ 0 (
    echo Skipping 'Locations Finder' service...
) else (
    echo Deploying 'Locations Finder' service...
    powershell -Command "(Get-Content docker-compose-app-locations-finder.yml) | ForEach-Object { [Environment]::ExpandEnvironmentVariables($_) } | Set-Content docker-compose-app-locations-finder.tmp.yml"
    docker stack deploy --compose-file=docker-compose-app-locations-finder.tmp.yml --with-registry-auth app
    del docker-compose-app-locations-finder.tmp.yml
)

@REM @REM TODO: Missing manifest in the jar file ..?!
@REM @REM docker stack deploy --compose-file=docker-compose-app-delay-manager.yml --with-registry-auth app

@REM Deploy Activity Aggregator service
echo ,%SKIP_SERVICES%, | findstr /I ",activity-aggregator," >nul
if %errorlevel% equ 0 (
    echo Skipping 'Activity Aggregator' service...
) else (
    echo Deploying 'Activity Aggregator' service...
    set COMPOSE_FILE=docker-compose-app-activity-aggregator-mongodb.yml
    @REM set COMPOSE_FILE=docker-compose-app-activity-aggregator-postgres.yml
    echo Using compose file: !COMPOSE_FILE!
    powershell -Command "(Get-Content !COMPOSE_FILE!) | ForEach-Object { [Environment]::ExpandEnvironmentVariables($_) } | Set-Content docker-compose-app-activity-aggregator.tmp.yml"
    docker stack deploy --compose-file=docker-compose-app-activity-aggregator.tmp.yml --with-registry-auth app
    del docker-compose-app-activity-aggregator.tmp.yml
)

@REM Deploy Info service
echo ,%SKIP_SERVICES%, | findstr /I ",info," >nul
if %errorlevel% equ 0 (
    echo Skipping 'Info' service...
) else (
    echo Deploying 'Info' service...
    set COMPOSE_FILE=docker-compose-app-info-mongodb.yml
    @REM set COMPOSE_FILE=docker-compose-app-info-postgres.yml
    echo Using compose file: !COMPOSE_FILE!
    docker stack deploy --compose-file=!COMPOSE_FILE! --with-registry-auth app
)

@REM Deploy Receiver service
echo ,%SKIP_SERVICES%, | findstr /I ",receiver," >nul
if %errorlevel% equ 0 (
    echo Skipping 'Receiver' service...
) else (
    echo Deploying 'Receiver' service...
    docker stack deploy --compose-file=docker-compose-app-receiver.yml --with-registry-auth app
)

echo.
echo Application services deployment complete.
timeout /t 5 >nul
@REM pause
endlocal