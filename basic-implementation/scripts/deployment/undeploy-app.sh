#!/bin/bash
set -e

# Stop and remove all application containers
docker-compose -f docker-compose-app-activity-aggregator.yml down
docker-compose -f docker-compose-app-delay-manager.yml down
docker-compose -f docker-compose-app-locations-finder.yml down
docker-compose -f docker-compose-app-mobilization-classifier.yml down
docker-compose -f docker-compose-app-web-api.yml down

echo "Application undeployment complete!"
