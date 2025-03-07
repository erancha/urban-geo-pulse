#!/bin/bash

# Create necessary directories
mkdir -p ../compose-files

# Copy Docker Compose files from deployment-with-docker-desktop
cp ../../deployment-with-docker-desktop/docker-compose-*.yml ../compose-files/
cp ../../deployment-with-docker-desktop/.env ../compose-files/
cp ../../deployment-with-docker-desktop/activity-aggregator-vars.env ../compose-files/

# Copy shared environment files
cp -r ../../deployment-with-docker-desktop/shared-env ../compose-files/

echo "Deployment files prepared successfully"
