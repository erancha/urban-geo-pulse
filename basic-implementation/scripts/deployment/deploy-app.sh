#!/bin/bash
set -e

# Load environment variables
set -a  # automatically export all variables
source .env
source activity-aggregator-vars.env
set +a  # stop automatically exporting

# Ensure Docker is running and configured
./ensure-docker.sh

# Ensure we're in swarm mode
if ! docker info | grep -q "Swarm: active"; then
    echo "Initializing Docker Swarm..."
    docker swarm init
fi

# Create the swarm-scoped network if it doesn't exist
echo "Ensuring swarm network exists..."
if ! docker network ls | grep -q "urban-geo-pulse-net"; then
    docker network create --driver overlay --attachable urban-geo-pulse-net
fi

# Deploy application services stack
echo "Deploying application services..."

# Create a temporary compose file with environment variables substituted
export DOCKER_REGISTRY
envsubst '${DOCKER_REGISTRY}' < docker-compose.yml > docker-compose.tmp.yml

# Deploy the stack
docker stack deploy -c docker-compose.tmp.yml urban-geo-pulse-app

# Clean up temporary file
rm docker-compose.tmp.yml

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
