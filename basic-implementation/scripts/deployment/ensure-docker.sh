#!/bin/bash
set -e

# Check if Docker daemon is running
if ! systemctl is-active --quiet docker; then
    echo "Starting Docker daemon..."
    sudo systemctl start docker
    sleep 5
fi

echo "Ensuring Docker Swarm is initialized..."
if ! docker info | grep -q "Swarm: active"; then
    echo "Initializing Docker Swarm..."
    docker swarm init
fi

echo "Ensuring swarm network exists..."
if ! docker network ls | grep -q "urbangeopulse-net"; then
    echo "Creating urbangeopulse-net network..."
    docker network create --driver overlay --attachable urbangeopulse-net
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
