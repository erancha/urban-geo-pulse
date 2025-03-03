#!/bin/bash
set -e

# Deploy third-party services using docker-compose
docker-compose -f docker-compose-3rd-party.yml up -d

echo "Third-party services deployment complete!"
