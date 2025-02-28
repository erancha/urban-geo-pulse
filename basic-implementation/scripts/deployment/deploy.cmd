@REM docker stack rm app
@REM docker stack rm 3rdparty
@REM timeout /t 20 >nul

@REM docker info --format '{{.Swarm.LocalNodeState}}'
@REM docker swarm leave --force
docker swarm init

@REM docker network rm urbangeopulse-net
docker network create -d overlay urbangeopulse-net
@REM docker network ls

docker stack deploy --compose-file=docker-compose-3rd-party.yml --with-registry-auth  3rdparty
timeout /t 30 >nul

docker stack deploy --compose-file=docker-compose-app-activity-aggregator.yml	--with-registry-auth  app
timeout /t 10 >nul

docker stack deploy --compose-file=docker-compose-app-locations-finder.yml 		--with-registry-auth  app
timeout /t 10 >nul

docker stack deploy --compose-file=docker-compose-app-mobilization-classifier.yml 	--with-registry-auth  app
timeout /t 10 >nul

docker stack deploy --compose-file=docker-compose-app-web-api.yml 				--with-registry-auth  app

@REM docker service ls
@REM docker service logs -t -f --since 5m  app_receiver 

@REM pause
timeout /t 5 >nul