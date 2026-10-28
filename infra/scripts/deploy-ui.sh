#!/usr/bin/env bash
# Builds the Angular app and publishes it to the UI bucket behind CloudFront.
# Usage, from anywhere:  AWS_PROFILE=tech-nova-team infra/scripts/deploy-ui.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
out=$(cd "$root/infra/terraform/main" && tofu output -json ui_deploy)
bucket=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["bucket"])' <<<"$out")
distribution=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["distribution_id"])' <<<"$out")

echo "== Building the UI"
cd "$root/ui"
npm ci --no-audit --no-fund
npx ng build --configuration production
dist="$root/ui/dist/ui/browser"

# The same caching as the UI container's nginx: file names with a content hash never change, so
# browsers keep them a year; index.html points at the current ones, so it is always checked again
echo "== Uploading to s3://${bucket}"
aws s3 sync "$dist" "s3://${bucket}" --delete --exclude index.html \
    --cache-control "public, max-age=31536000, immutable"
aws s3 cp "$dist/index.html" "s3://${bucket}/index.html" --cache-control "no-cache"

# CloudFront may hold the previous index.html; only that one needs forgetting
echo "== Refreshing CloudFront"
aws cloudfront create-invalidation --distribution-id "$distribution" --paths "/index.html" "/" \
    --query 'Invalidation.Id' --output text

echo "UI published: $(cd "$root/infra/terraform/main" && tofu output -raw site_url)"
