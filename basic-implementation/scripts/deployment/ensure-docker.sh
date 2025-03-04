#!/bin/bash
set -e

# Check if Docker daemon is running
if ! systemctl is-active --quiet docker; then
    echo "Starting Docker daemon..."
    sudo systemctl start docker
    sleep 5
fi

# Initialize swarm if not already done
if ! docker info | grep -q "Swarm: active"; then
    echo "Initializing Docker Swarm..."
    docker swarm init
fi

# Start local registry if not running
if ! docker ps | grep -q "registry:2"; then
    echo "Starting local registry..."
    docker run -d -p 5000:5000 --name registry --restart=always registry:2
fi

# Create default environment file if it doesn't exist
if [ ! -f .env ]; then
    echo "Creating .env file..."
    echo "DOCKER_REGISTRY=localhost:5000" > .env
fi
