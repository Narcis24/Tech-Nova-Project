#!/usr/bin/env bash
# Creates (or resets) the "Tech Nova" quality gate, makes it the default and assigns it to every service.
# Safe to re-run: existing conditions are replaced with the ones below.
# Needs an admin user token (squ_...): SONAR_TOKEN=<token> ./sonar/quality-gate.sh
set -euo pipefail

HOST="${SONAR_HOST_URL:-http://localhost:8083}"
GATE="Tech Nova"
PROJECTS=(tech-nova-app tech-nova-execution-engine tech-nova-auth tech-nova-data-pipeline tech-nova-ui)

# metric operator threshold; ratings are 1=A .. 5=E, so "GT 1" means worse than A
CONDITIONS=(
    # new code: same bar as "Sonar way"
    "new_violations GT 0"
    "new_coverage LT 80"
    "new_duplicated_lines_density GT 3"
    "new_security_hotspots_reviewed LT 100"
    # overall code: existing code has to stay clean too
    "software_quality_reliability_rating GT 1"
    "software_quality_security_rating GT 1"
    "software_quality_maintainability_rating GT 1"
    "coverage LT 80"
    "duplicated_lines_density GT 3"
)

: "${SONAR_TOKEN:?set SONAR_TOKEN to an admin user token}"

api() {
    local method=$1 path=$2; shift 2
    curl -sf -u "$SONAR_TOKEN:" -X "$method" "$HOST/api/$path" "$@"
}

if ! api GET qualitygates/show --get --data-urlencode "name=$GATE" > /dev/null; then
    api POST qualitygates/create --data-urlencode "name=$GATE" > /dev/null
    echo "created gate $GATE"
fi

for id in $(api GET qualitygates/show --get --data-urlencode "name=$GATE" \
        | python3 -c 'import sys, json; print(*(c["id"] for c in json.load(sys.stdin)["conditions"]))'); do
    api POST qualitygates/delete_condition -d "id=$id"
done

for c in "${CONDITIONS[@]}"; do
    read -r metric op error <<< "$c"
    api POST qualitygates/create_condition --data-urlencode "gateName=$GATE" \
        -d "metric=$metric" -d "op=$op" -d "error=$error" > /dev/null
    echo "condition $metric $op $error"
done

api POST qualitygates/set_as_default --data-urlencode "name=$GATE"

# projects that have not been scanned yet pick the gate up as the default
for p in "${PROJECTS[@]}"; do
    if api POST qualitygates/select --data-urlencode "gateName=$GATE" -d "projectKey=$p"; then
        echo "assigned $p"
    else
        echo "skipped $p (not scanned yet)"
    fi
done
