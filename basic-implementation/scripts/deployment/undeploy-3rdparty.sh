#!/bin/bash
set -e

# Stop and remove all third-party containers
docker-compose -f docker-compose-3rd-party.yml down

echo "Third-party services undeployment complete!"
