#!/usr/bin/env bash
# Pauses the stack between demos and brings it back, without tofu destroy.
#   pause   services to 0 tasks, database stopped: from about $3-4 a day to about $0.70 (load balancer, storage)
#   resume  database started, Kafka first, then the other services; about 10 minutes
#   status  what is running
# Usage:  AWS_PROFILE=tech-nova-team infra/scripts/stack.sh pause|resume|status
#
# AWS starts a stopped database again by itself after 7 days: pause again if the break is longer.
# A tofu apply while paused sets every service back to 1 task (that is its desired count).
set -euo pipefail

PREFIX="${PREFIX:-tech-nova-team}"
CLUSTER="${PREFIX}-cluster"
DB="${PREFIX}-db"
# Kafka first: the others connect to it at start-up
SERVICES=(kafka execution-engine auth-service app)

scale() {
    aws ecs update-service --cluster "$CLUSTER" --service "${PREFIX}-$1" --desired-count "$2" \
        --query 'service.serviceName' --output text >/dev/null
}

db_status() {
    aws rds describe-db-instances --db-instance-identifier "$DB" --query 'DBInstances[0].DBInstanceStatus' --output text
}

case "${1:-}" in
pause)
    for s in "${SERVICES[@]}"; do scale "$s" 0; echo "${s}: scaled to 0"; done
    if [[ "$(db_status)" == "available" ]]; then
        aws rds stop-db-instance --db-instance-identifier "$DB" --query 'DBInstance.DBInstanceStatus' --output text
        echo "Database stopping (a few minutes). Paused."
    else
        echo "Database is $(db_status), not stopped"
    fi
    ;;
resume)
    if [[ "$(db_status)" == "stopped" ]]; then
        aws rds start-db-instance --db-instance-identifier "$DB" --query 'DBInstance.DBInstanceStatus' --output text
    fi
    echo "Waiting for the database (5-10 minutes after a stop)"
    aws rds wait db-instance-available --db-instance-identifier "$DB"
    scale kafka 1
    echo "Waiting for Kafka"
    aws ecs wait services-stable --cluster "$CLUSTER" --services "${PREFIX}-kafka"
    for s in execution-engine auth-service app; do scale "$s" 1; done
    echo "Waiting for the engine, auth-service and app (2-3 minutes)"
    aws ecs wait services-stable --cluster "$CLUSTER" \
        --services "${PREFIX}-execution-engine" "${PREFIX}-auth-service" "${PREFIX}-app"
    echo "Resumed: $(cd "$(dirname "$0")/../terraform/main" && tofu output -raw site_url)"
    ;;
status)
    echo "Database: $(db_status)"
    aws ecs describe-services --cluster "$CLUSTER" --services "${SERVICES[@]/#/${PREFIX}-}" \
        --query 'services[].[serviceName, runningCount, desiredCount]' --output table
    ;;
*)
    echo "usage: $0 pause|resume|status" >&2
    exit 1
    ;;
esac
