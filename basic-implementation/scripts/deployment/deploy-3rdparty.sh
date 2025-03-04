#!/bin/bash
set -e

# Load environment variables
source .env

# Ensure Docker is running and configured
./ensure-docker.sh

echo "Setting up data directories..."
sudo mkdir -p /var/lib/postgresql/data
sudo mkdir -p /var/lib/postgresql/backup

# Deploy 3rd party services stack
echo "Deploying 3rd party services..."
docker stack deploy -c docker-compose-3rd-party.yml urbangeopulse

# Wait for services to be running
echo "Waiting for services to start..."
sleep 10

# Check stack status
echo -e "\nStack services status:"
docker stack services urbangeopulse

echo -e "\nStack status:"
docker stack ps urbangeopulse --format "table {{.Name}}\t{{.CurrentState}}\t{{.Error}}"

# Verify stack is running
if ! docker stack ls | grep -q "urbangeopulse"; then
    echo "ERROR: Stack deployment failed!"
    exit 1
fi

echo -e "\n3rd party services deployed successfully!"
