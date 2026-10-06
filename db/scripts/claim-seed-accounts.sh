#!/usr/bin/env bash
# Local dev only: gives the unowned seed accounts (ACC001-ACC005) to a registered user so they
# can be used over the API. Usage: db/scripts/claim-seed-accounts.sh <username>
set -euo pipefail
cd "$(dirname "$0")/../.."

user="${1:?usage: $0 <username>}"
docker-compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v user="$0"' "$user" <<'SQL'
UPDATE accounts SET owner_username = :'user' WHERE owner_username IS NULL;
SQL
