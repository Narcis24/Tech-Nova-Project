#!/usr/bin/env bash
# Runs a one-off job on Fargate, waits for it to finish and prints its logs; exits non-zero if it failed.
# Usage:  AWS_PROFILE=tech-nova-team infra/scripts/run-task.sh migrations|prices|fundamentals
set -euo pipefail

job="${1:?usage: run-task.sh migrations|prices|fundamentals}"
cd "$(dirname "$0")/../terraform/main"
out=$(tofu output -json run_task)
# get key [key ...]: one value from the run_task output
get() {
    python3 -c 'import json, sys
value = json.load(sys.stdin)
for key in sys.argv[1:]:
    value = value[key]
print(value)' "$@" <<<"$out"
}

cluster=$(get cluster)
family=$(get task_definitions "$job")
log_group=$(get log_groups "$job")
security_group=$(get security_group)
# The loaders call Yahoo Finance, so they run in a public subnet with a public IP (still no inbound);
# migrations only need RDS and the shared VPC endpoints, so they stay private
if [[ "$job" == "migrations" ]]; then
    network="subnets=[$(get private_subnet)],securityGroups=[${security_group}],assignPublicIp=DISABLED"
else
    network="subnets=[$(get public_subnet)],securityGroups=[${security_group}],assignPublicIp=ENABLED"
fi

echo "Starting ${family} on ${cluster}"
task=$(aws ecs run-task --cluster "$cluster" --task-definition "$family" --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={${network}}" --query 'tasks[0].taskArn' --output text)
echo "Task ${task##*/}: waiting for it to finish (a few minutes for the loaders)"
aws ecs wait tasks-stopped --cluster "$cluster" --tasks "$task"

read -r exit_code reason < <(aws ecs describe-tasks --cluster "$cluster" --tasks "$task" \
    --query 'tasks[0].[containers[0].exitCode, stoppedReason]' --output text)
echo "---- logs ----"
aws logs tail "$log_group" --log-stream-name-prefix "$job/" --since 1h --format short | tail -60
echo "--------------"
if [[ "$exit_code" != "0" ]]; then
    echo "FAILED: exit code ${exit_code}, ${reason}" >&2
    exit 1
fi
echo "Done: ${job} succeeded"
