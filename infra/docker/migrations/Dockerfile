# Applies db/migrations to RDS as a one-off ECS task. Build from the repository root:
#   docker build -f infra/docker/migrations/Dockerfile .
FROM postgres:16-alpine

COPY db/migrations/ /migrations/
COPY infra/docker/migrations/migrate.sh /usr/local/bin/migrate.sh
RUN chmod +x /usr/local/bin/migrate.sh

# Not a database server: only the psql client from this image is used
USER postgres
ENTRYPOINT ["migrate.sh"]
