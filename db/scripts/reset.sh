#!/usr/bin/env bash
# Drop the db volume, re-run migrations, reload data. Args pass to load.py, e.g. reset.sh 5y
set -euo pipefail
cd "$(dirname "$0")/../.."

docker-compose down -v
docker-compose up -d --wait db

# the healthcheck passes even when a migration fails
if docker-compose logs db | grep 'ERROR:'; then exit 1; fi

docker-compose --profile pipeline run --rm --build pipeline "$@"
