#!/bin/bash
set -e

# Ensure Docker is running and configured
./ensure-docker.sh

# Remove 3rd party services stack
echo "Removing 3rd party services..."
docker stack rm urban-geo-pulse-3rdparty

# Wait for stack to be removed
echo "Waiting for services to be removed..."
while docker stack ls | grep -q "urban-geo-pulse-3rdparty"; do
    sleep 2
    echo -n "."
done
echo

# Verify stack is removed
if docker stack ls | grep -q "urban-geo-pulse-3rdparty"; then
    echo "ERROR: Failed to remove stack!"
    exit 1
fi

echo -e "\n3rd party services removed successfully!"

# Show remaining stacks
echo -e "\nRemaining stacks:"
docker stack ls
