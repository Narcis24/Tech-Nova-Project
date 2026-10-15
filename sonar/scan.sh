#!/usr/bin/env bash
# Scans every project and checks it against the "Tech Nova" quality gate; exits non-zero if any fails.
# Run from anywhere: SONAR_TOKEN=<token> ./sonar/scan.sh
set -uo pipefail

: "${SONAR_TOKEN:?set SONAR_TOKEN to a SonarQube user token}"
export SONAR_TOKEN
HOST="${SONAR_HOST_URL:-http://localhost:8083}"
cd "$(dirname "$0")/.."

failed=()

# app / execution-engine: Maven; install first so messaging is in ~/.m2 for the per-module runs
mvn -B -q install -DskipTests || exit 1
for m in app execution-engine; do
    mvn -B -f "$m/pom.xml" verify sonar:sonar -Dsonar.host.url="$HOST" -Dsonar.token="$SONAR_TOKEN" \
        -Dsonar.qualitygate.wait=true || failed+=("$m")
done

# auth needs its coverage report first; the local Node may be too old, so use a Node 24 container
docker run --rm -u "$(id -u):$(id -g)" -e npm_config_cache=/tmp/.npm -v "$PWD/auth":/app -w /app \
    node:24-alpine sh -c "npm ci && npm run test:cov" || failed+=("auth tests")

# UI coverage is produced by npm run test:ci before scanning (Jenkins: UI Checks).
if [[ ! -s ui/coverage/lcov.info ]]; then
    echo "Missing UI coverage: run npm run test:ci in ui/ first" >&2
    exit 1
fi

# auth / data-pipeline / ui: settings in their sonar-project.properties; the image's own
# /tmp/.scannerwork is writable only by its uid 1000, so use a fresh work dir
for m in auth data-pipeline ui; do
    docker run --rm --network host -u "$(id -u):$(id -g)" -e SONAR_HOST_URL="$HOST" -e SONAR_TOKEN \
        -e SONAR_USER_HOME=/tmp/.sonar -e SCANNER_WORKDIR_PATH=/tmp/scannerwork -v "$PWD/$m":/usr/src \
        sonarsource/sonar-scanner-cli -Dsonar.qualitygate.wait=true || failed+=("$m")
done

if (( ${#failed[@]} )); then
    echo "FAILED: ${failed[*]}" >&2
    exit 1
fi
echo "All projects passed the quality gate"
