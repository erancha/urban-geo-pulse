#!/bin/bash

# Create directory if it doesn't exist
PG_CONTAINER_FOLDER="$(dirname "$(dirname "$(readlink -f "$0")")")"
mkdir -p "$PG_CONTAINER_FOLDER/Temp/postgreSQL_nyc/urbangeopulse"

# Get container ID dynamically
export PG_CONTAINER_ID=$(docker ps --filter "name=urban-geo-pulse-3rdparty_postgis-server-nyc" --format "{{.ID}}")
