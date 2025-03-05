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
call build-and-push-a-service receiver 					1.6
call build-and-push-a-service mobilization-classifier   1.1
call build-and-push-a-service locations-finder 			1.4
call build-and-push-a-service delay-manager
call build-and-push-a-service activity-aggregator       1.1
call build-and-push-a-service info                      1.1

echo.
echo Deploying application services...
call deploy-app.cmd