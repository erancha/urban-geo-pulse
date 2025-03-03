#!/bin/bash
set -e

# Load environment variables
source .env

# Build and push each service
docker build -t $DOCKER_REGISTRY/activity-aggregator:latest -f Dockerfile_activity-aggregator .
docker build -t $DOCKER_REGISTRY/delay-manager:latest -f Dockerfile_delay-manager .
docker build -t $DOCKER_REGISTRY/locations-finder:latest -f Dockerfile_locations-finder .
docker build -t $DOCKER_REGISTRY/mobilization-classifier:latest -f Dockerfile_mobilization-classifier .
docker build -t $DOCKER_REGISTRY/receiver:latest -f Dockerfile_receiver .

docker push $DOCKER_REGISTRY/activity-aggregator:latest
docker push $DOCKER_REGISTRY/delay-manager:latest
docker push $DOCKER_REGISTRY/locations-finder:latest
docker push $DOCKER_REGISTRY/mobilization-classifier:latest
docker push $DOCKER_REGISTRY/receiver:latest

echo "Build and push complete!"
