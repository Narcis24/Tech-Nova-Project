#!/bin/sh
# Local dev only: seeds a test admin (username test_admin) when TEST_ADMIN_PASSWORD is set in .env, so a fresh
# database can sign in to the admin pages straight away. Unset, as it must be on any shared server, means no admin.
# The password never goes in git: Postgres hashes it here with bcrypt, cost 12, which auth-service accepts.
# No exit or set -e: the Postgres entrypoint sources this file if it is not executable.
if [ -n "${TEST_ADMIN_PASSWORD:-}" ]; then
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v pw="$TEST_ADMIN_PASSWORD" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO users (username, password_hash, email)
VALUES ('test_admin', crypt(:'pw', gen_salt('bf', 12)), 'test_admin@technova.local');
INSERT INTO user_roles (username, role) VALUES ('test_admin', 'ADMIN');
SQL
else
  echo "TEST_ADMIN_PASSWORD not set: no test admin seeded"
fi
