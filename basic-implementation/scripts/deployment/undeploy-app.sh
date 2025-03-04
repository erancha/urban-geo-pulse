#!/bin/bash
set -e

# Ensure Docker is running and configured
./ensure-docker.sh

# Remove application services stack
echo "Removing application services..."
docker stack rm urban-geo-pulse-app

# Wait for stack to be removed
echo "Waiting for services to be removed..."
while docker stack ls | grep -q "urban-geo-pulse-app"; do
    sleep 2
    echo -n "."
done
echo

# Verify stack is removed
if docker stack ls | grep -q "urban-geo-pulse-app"; then
    echo "ERROR: Failed to remove stack!"
    exit 1
fi

# Clean up any temporary files
rm -f docker-compose.tmp.yml

echo -e "\nApplication services removed successfully!"

# Show remaining stacks
echo -e "\nRemaining stacks:"
docker stack ls

# Show any remaining containers
echo -e "\nRemaining containers:"
docker ps
