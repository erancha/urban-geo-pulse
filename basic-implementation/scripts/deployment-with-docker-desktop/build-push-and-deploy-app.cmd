@echo off
if "%1"=="undeploy" (
    call undeploy-app.cmd
    exit /b
)

echo Checking 3rd party services...
docker service ls | findstr "3rdparty" >nul
if errorlevel 1 (
    echo Setting up 3rd party services...
    call deploy-3rdparty.cmd
) else (
    echo 3rd party services already running.
)

echo Building and pushing all services...
call build-and-push-a-service receiver                  1.0  latest
call build-and-push-a-service mobilization-classifier   1.0  latest
call build-and-push-a-service locations-finder          1.0  latest
call build-and-push-a-service delay-manager             1.0  latest
call build-and-push-a-service activity-aggregator       1.0  mongodb
call build-and-push-a-service info                      1.0  mongodb

echo.
echo Deploying application services...
call deploy-app.cmd