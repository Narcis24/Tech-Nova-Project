#!/usr/bin/env bash
# Drop the db volume, re-run migrations, reload prices and company data. Args pass to load.py, e.g. reset.sh 2020-01-01
set -euo pipefail
cd "$(dirname "$0")/../.."

docker-compose down -v
docker-compose up -d --wait db

# the healthcheck passes even when a migration fails
if docker-compose logs db | grep 'ERROR:'; then exit 1; fi

docker-compose --profile pipeline run --rm --build pipeline "$@"
# Company and fund facts for the trade page; a symbol Yahoo fails on is retried by the next run
docker-compose --profile pipeline run --rm --entrypoint python pipeline fundamentals.py \
    || echo "Some company data is missing; rerun: docker-compose --profile pipeline run --rm --entrypoint python pipeline fundamentals.py"
