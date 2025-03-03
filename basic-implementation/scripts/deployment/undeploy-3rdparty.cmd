@echo off
echo Removing 3rd party services...

docker stack rm 3rdparty
timeout /t 20 >nul

echo Removing network...
docker network rm urbangeopulse-net

echo Leaving swarm...
docker swarm leave --force

echo.
echo 3rd party services and network removed.
timeout /t 5 >nul
