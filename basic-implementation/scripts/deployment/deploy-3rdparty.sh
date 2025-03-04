#!/bin/bash
set -e

# Load environment variables
source .env

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

# Create and set permissions for data directories
echo "Setting up data directories..."
sudo mkdir -p /tmp/postgreSQL_nyc /tmp/pgadmin
sudo chown -R 999:999 /tmp/postgreSQL_nyc  # postgres user
sudo chown -R 5050:5050 /tmp/pgadmin  # pgadmin user

# Deploy 3rd party services stack
echo "Deploying 3rd party services..."
docker stack deploy -c docker-compose-3rd-party.yml urban-geo-pulse-3rdparty

# Wait for services to be running
echo "Waiting for services to start..."
sleep 10

# Check stack status
echo -e "\nStack services status:"
docker stack services urban-geo-pulse-3rdparty

echo -e "\nStack status:"
docker stack ps urban-geo-pulse-3rdparty --format "table {{.Name}}\t{{.CurrentState}}\t{{.Error}}"

# Verify stack is running
if ! docker stack ls | grep -q "urban-geo-pulse-3rdparty"; then
    echo "ERROR: Stack deployment failed!"
    exit 1
fi

echo -e "\n3rd party services deployed successfully!"
