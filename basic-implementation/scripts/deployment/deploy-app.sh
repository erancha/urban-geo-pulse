#!/bin/bash
set -e

# Load environment variables
source .env
source activity-aggregator-vars.env

# Deploy third-party services first
./deploy-3rdparty.sh

# Deploy each service with environment variable substitution
envsubst < docker-compose-app-activity-aggregator.yml | docker-compose -f - up -d
envsubst < docker-compose-app-delay-manager.yml | docker-compose -f - up -d
envsubst < docker-compose-app-locations-finder.yml | docker-compose -f - up -d
envsubst < docker-compose-app-mobilization-classifier.yml | docker-compose -f - up -d
envsubst < docker-compose-app-web-api.yml | docker-compose -f - up -d

echo "Application deployment complete!"
