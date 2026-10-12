# Auth service

NestJS identity provider for TechNova: registers users, checks credentials and issues the JWTs
that the Trade REST API (`app`) accepts. The API is specified in `contracts/auth-api.yml`.

- Listens on port 8082 under `/auth`: `POST /auth/v1/auth/{register,login,refresh,logout}`
- Swagger UI at `/auth/docs`, OpenAPI document at `/auth/docs-json`, health at `/auth/health`
- Tables `users` and `refresh_tokens` (migrations V006 and V010 in `db/migrations`)

## Configuration

| Variable      | Default     | Notes                                           |
|---------------|-------------|-------------------------------------------------|
| `JWT_SECRET`  | (required)  | At least 32 bytes; the same secret `app` uses   |
| `DB_PASSWORD` | (required)  |                                                 |
| `DB_HOST`     | `localhost` |                                                 |
| `DB_PORT`     | `5434`      |                                                 |
| `DB_NAME`     | `technova`  |                                                 |
| `DB_USER`     | `technova`  |                                                 |

The service refuses to start if the secret is missing or short, or if the database or a table is missing.

## Commands

```bash
npm ci
npm run start:dev   # watch mode; needs the env vars above and a migrated database
npm test            # unit and HTTP tests; the database is replaced by an in-memory fake
npm run test:cov    # same, with coverage (lcov for SonarQube)
npm run lint
```

In the full stack it runs from the `Dockerfile` via `docker-compose`; the system-wide tests are in `e2e/`.
