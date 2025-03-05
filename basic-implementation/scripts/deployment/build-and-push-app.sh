#!/bin/bash
set -e

# Load environment variables 
source .env

# Ensure Docker is running and configured
./ensure-docker.sh

# Get the services directory path relative to the Windows path
SERVICES_DIR="/mnt/c/Projects/IntelliJ/urban-geo-pulse/basic-implementation/services"

# Build and push each service from its directory
cd "$SERVICES_DIR/activity-aggregator" && \
docker build -t $DOCKER_REGISTRY/activity-aggregator:latest -f /mnt/c/Projects/IntelliJ/urban-geo-pulse/basic-implementation/scripts/deployment/Dockerfile_activity-aggregator .

cd "$SERVICES_DIR/delay-manager" && \
docker build -t $DOCKER_REGISTRY/delay-manager:latest -f /mnt/c/Projects/IntelliJ/urban-geo-pulse/basic-implementation/scripts/deployment/Dockerfile_delay-manager .

cd "$SERVICES_DIR/locations-finder" && \
docker build -t $DOCKER_REGISTRY/locations-finder:latest -f /mnt/c/Projects/IntelliJ/urban-geo-pulse/basic-implementation/scripts/deployment/Dockerfile_locations-finder .

cd "$SERVICES_DIR/mobilization-classifier" && \
docker build -t $DOCKER_REGISTRY/mobilization-classifier:latest -f /mnt/c/Projects/IntelliJ/urban-geo-pulse/basic-implementation/scripts/deployment/Dockerfile_mobilization-classifier .

cd "$SERVICES_DIR/receiver" && \
docker build -t $DOCKER_REGISTRY/receiver:latest -f /mnt/c/Projects/IntelliJ/urban-geo-pulse/basic-implementation/scripts/deployment/Dockerfile_receiver .

# Push all images
docker push $DOCKER_REGISTRY/activity-aggregator:latest
docker push $DOCKER_REGISTRY/delay-manager:latest
docker push $DOCKER_REGISTRY/locations-finder:latest
docker push $DOCKER_REGISTRY/mobilization-classifier:latest
docker push $DOCKER_REGISTRY/receiver:latest

echo "Build and push complete!"

# Show pushed images
echo -e "\nPushed images:"
docker images | grep "$DOCKER_REGISTRY"
