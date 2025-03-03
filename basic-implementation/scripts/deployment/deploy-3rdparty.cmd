@echo off
echo Checking and cleaning up previous deployment...

docker stack rm 3rdparty 2>nul
timeout /t 20 >nul

echo Initializing swarm...
docker swarm leave --force 2>nul
docker swarm init

echo Creating network...
docker network rm urbangeopulse-net 2>nul
docker network create -d overlay urbangeopulse-net

echo Deploying 3rd party services...
docker stack deploy --compose-file=docker-compose-3rd-party.yml --with-registry-auth 3rdparty
timeout /t 30 >nul

echo.
echo 3rd party services deployment complete.
timeout /t 5 >nul
