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

# Check if SQL file argument is provided
if [ -z "$1" ]; then
    echo "Error: SQL file argument is required"
    echo "Usage: $0 <sql-file>"
    echo "Example: $0 query-agg_activity.sql"
    exit 1
fi

SQL_FILE="$1"
if [ ! -f "$SQL_DIR/$SQL_FILE" ]; then
    echo "Error: SQL file '$SQL_DIR/$SQL_FILE' not found"
    echo "Please copy it from scripts/development/sql/"
    exit 1
fi

# Copy SQL file into container
echo "Copying SQL file to container..."
docker cp "$SQL_DIR/$SQL_FILE" "$CONTAINER_ID:/$SQL_FILE"

# Execute SQL file
echo "Executing $SQL_FILE..."
docker exec -i "$CONTAINER_ID" psql -U user -d nyc -f "/$SQL_FILE"

# Display output
OUTPUT_FILE="/tmp/${SQL_FILE%.*}.out"
docker exec -i "$CONTAINER_ID" psql -U user -d nyc -f "/$SQL_FILE" > "$OUTPUT_FILE"
cat "$OUTPUT_FILE"
