#!/bin/sh
# Applies db/migrations to the database once each, in name order (V001, V002, ...), and records them in
# schema_migrations so a rerun only applies new files. The same files the local Postgres container runs on
# its first start; RDS never runs them by itself.
# Connection from the standard libpq variables: PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD.
set -eu

MIGRATIONS_DIR=/migrations

psql -v ON_ERROR_STOP=1 -q -c "CREATE TABLE IF NOT EXISTS schema_migrations (
    version    VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP    NOT NULL DEFAULT now()
)"

applied=0
for file in $(ls "$MIGRATIONS_DIR" | sort); do
    version="${file%.*}"
    done_already=$(psql -tA -c "SELECT 1 FROM schema_migrations WHERE version = '$version'")
    if [ "$done_already" = "1" ]; then
        continue
    fi

    echo "Applying $file"
    case "$file" in
        *.sql)
            # One transaction per file: a failing migration leaves nothing half applied
            psql -v ON_ERROR_STOP=1 --single-transaction -f "$MIGRATIONS_DIR/$file"
            ;;
        *.sh)
            # Written for the Postgres image's entrypoint, which provides these two
            POSTGRES_USER="$PGUSER" POSTGRES_DB="$PGDATABASE" sh "$MIGRATIONS_DIR/$file"
            ;;
        *)
            echo "Skipping $file: not .sql or .sh"
            continue
            ;;
    esac
    psql -v ON_ERROR_STOP=1 -q -c "INSERT INTO schema_migrations (version) VALUES ('$version')"
    applied=$((applied + 1))
done

echo "Migrations done: $applied applied"
