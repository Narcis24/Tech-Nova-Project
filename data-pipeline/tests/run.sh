#!/usr/bin/env bash
# Runs the pipeline tests in the pipeline image against a throwaway Postgres loaded with
# db/migrations. Needs only Docker. Usage: data-pipeline/tests/run.sh
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
root="$(cd "$here/.." && pwd)"
name="pipeline-test-$$"

cleanup() {
    docker rm -f "$name-db" >/dev/null 2>&1 || true
    docker network rm "$name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker build -q --target test -t tech-nova-pipeline:test "$here" >/dev/null
docker network create "$name" >/dev/null
docker run -d --name "$name-db" --network "$name" \
    -e POSTGRES_DB=technova -e POSTGRES_USER=technova -e POSTGRES_PASSWORD=test \
    -v "$root/db/migrations:/docker-entrypoint-initdb.d:ro" \
    postgres:16-alpine >/dev/null

# the init scripts run on a temporary server first; wait for the final one on the network port
for _ in $(seq 60); do
    if docker exec "$name-db" pg_isready -h localhost -U technova -d technova -q 2>/dev/null \
        && docker logs "$name-db" 2>&1 | grep -q "PostgreSQL init process complete"; then
        break
    fi
    sleep 1
done

docker run --rm --network "$name" \
    -e DB_URL="postgresql://technova:test@$name-db:5432/technova" \
    tech-nova-pipeline:test
