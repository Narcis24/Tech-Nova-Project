#!/usr/bin/env bash
# Builds every image the AWS stack runs and pushes it to ECR, tagged with the current git commit.
# Usage, from anywhere:  AWS_PROFILE=tech-nova-team infra/scripts/build-and-push.sh [image ...]
# With no arguments it does all of them. Prints the tag to put in terraform.tfvars as image_tag.
set -euo pipefail

PREFIX="${PREFIX:-tech-nova-team}"
REGION="${AWS_REGION:-eu-west-1}"
# Fargate tasks are declared X86_64; build for that whatever this machine is
PLATFORM="${PLATFORM:-linux/amd64}"
KAFKA_IMAGE="apache/kafka:4.0.0"

cd "$(dirname "$0")/../.."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

# Uncommitted changes would make the tag lie about what is inside the image
TAG="${TAG:-$(git rev-parse --short HEAD)}"
if [[ -n "$(git status --porcelain)" && -z "${ALLOW_DIRTY:-}" ]]; then
    echo "Uncommitted changes: commit first, or set ALLOW_DIRTY=1 to build them as ${TAG}-dirty" >&2
    exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
    TAG="${TAG}-dirty"
fi

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null

# name|dockerfile|context: the same builds as docker-compose.yml
declare -A BUILDS=(
    [app]="app/Dockerfile|."
    [execution-engine]="execution-engine/Dockerfile|."
    [auth-service]="auth/Dockerfile|auth"
    [pipeline]="data-pipeline/Dockerfile|data-pipeline"
    [migrations]="infra/docker/migrations/Dockerfile|."
)

wanted=("$@")
[[ ${#wanted[@]} -eq 0 ]] && wanted=(app execution-engine auth-service kafka pipeline migrations)

for name in "${wanted[@]}"; do
    target="${REGISTRY}/${PREFIX}/${name}:${TAG}"
    echo "== ${name} -> ${target}"
    if [[ "$name" == "kafka" ]]; then
        # Not ours: copy the public image so tasks without internet access can pull it
        docker pull --platform "$PLATFORM" "$KAFKA_IMAGE"
        docker tag "$KAFKA_IMAGE" "$target"
    elif [[ -n "${BUILDS[$name]:-}" ]]; then
        IFS='|' read -r dockerfile context <<<"${BUILDS[$name]}"
        docker build --platform "$PLATFORM" -f "$dockerfile" -t "$target" "$context"
    else
        echo "Unknown image: $name (known: ${!BUILDS[*]} kafka)" >&2
        exit 1
    fi
    docker push "$target"
done

echo
echo "Pushed tag ${TAG}. Set it in infra/terraform/main/terraform.tfvars:  image_tag = \"${TAG}\""
