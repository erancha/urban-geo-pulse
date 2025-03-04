#!/bin/bash
set -e

# Ensure Docker is running and configured
./ensure-docker.sh

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$SCRIPT_DIR/sql"

# Get PostgreSQL container ID
CONTAINER_ID=$(docker ps --filter "name=urbangeopulse_postgis-server-nyc" --format "{{.ID}}")

if [ -z "$CONTAINER_ID" ]; then
    echo "Error: PostgreSQL container not found. Make sure it's running with deploy-3rdparty.sh"
    exit 1
fi

# Check if SQL files exist
if [ ! -d "$SQL_DIR" ]; then
    echo "Error: SQL directory not found at $SQL_DIR"
    echo "Please copy SQL files from scripts/development/ to scripts/deployment/sql/"
    exit 1
fi

if [ ! -f "$SQL_DIR/init.sql" ]; then
    echo "Error: init.sql not found in $SQL_DIR"
    echo "Please copy it from scripts/development/"
    exit 1
fi

# Copy SQL file into container
echo "Copying SQL files to container..."
docker cp "$SQL_DIR/init.sql" "$CONTAINER_ID":/init.sql

# Execute SQL file
echo "Executing init.sql..."
docker exec -i "$CONTAINER_ID" psql -U user -d nyc -f /init.sql

echo "Database initialization complete!"
