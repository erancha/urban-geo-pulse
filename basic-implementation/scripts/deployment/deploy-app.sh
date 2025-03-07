#!/bin/bash
set -e

# Load environment variables
set -a  # automatically export all variables
source .env
source activity-aggregator-vars.env
set +a  # stop automatically exporting

# Ensure Docker is running and configured
./ensure-docker.sh

# Deploy application services stack
echo "Deploying application services..."

COMPOSE_FILES_DIR="./compose-files"

# Deploy activity aggregator
docker compose -f ${COMPOSE_FILES_DIR}/docker-compose-app-activity-aggregator.yml up -d

# Deploy delay manager
docker compose -f ${COMPOSE_FILES_DIR}/docker-compose-app-delay-manager.yml up -d

# Deploy locations finder
docker compose -f ${COMPOSE_FILES_DIR}/docker-compose-app-locations-finder.yml up -d

# Deploy mobilization classifier
docker compose -f ${COMPOSE_FILES_DIR}/docker-compose-app-mobilization-classifier.yml up -d

# Deploy web API
docker compose -f ${COMPOSE_FILES_DIR}/docker-compose-app-web-api.yml up -d

echo "All services deployed successfully"

# Display running services
docker service ls

# Wait for services to be running
echo "Waiting for services to start..."
sleep 10

# Check stack status
echo -e "\nStack services status:"
docker stack services urban-geo-pulse-app

echo -e "\nStack status:"
docker stack ps urban-geo-pulse-app --format "table {{.Name}}\t{{.CurrentState}}\t{{.Error}}"

# Verify stack is running
if ! docker stack ls | grep -q "urban-geo-pulse-app"; then
    echo "ERROR: Stack deployment failed!"
    exit 1
fi

echo -e "\nApplication services deployed successfully!"

# Show overall deployment status
echo -e "\nOverall deployment status:"
docker stack ls
