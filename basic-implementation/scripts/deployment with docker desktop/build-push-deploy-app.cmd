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
call build-and-push receiver 1.2
call build-and-push mobilization-classifier
call build-and-push locations-finder 1.3
call build-and-push delay-manager
call build-and-push activity-aggregator
call build-and-push info

echo.
echo Deploying application services...
call deploy-app.cmd