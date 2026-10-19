#!/usr/bin/env bash
# Local dev only: gives a registered user the ADMIN role (on top of any they have), so there is a first
# admin to create the others. Takes effect at their next login or token refresh.
# Usage: db/scripts/make-admin.sh <username>
set -euo pipefail
cd "$(dirname "$0")/../.."

user="${1:?usage: $0 <username>}"
docker-compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v user="$0"' "$user" <<'SQL'
INSERT INTO user_roles (username, role) VALUES (:'user', 'ADMIN') ON CONFLICT DO NOTHING;
SELECT username, string_agg(role, ', ' ORDER BY role) AS roles FROM user_roles WHERE username = :'user' GROUP BY username;
SQL
