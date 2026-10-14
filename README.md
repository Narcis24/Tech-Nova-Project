# TechNova - Enterprise Trading Platform

**An enterprise-grade trading application with multi-asset support, order management, and real-time position tracking.**

## Branching Strategy

**Gitflow** - Feature work on `feature/` branches off `develop`, with PR review before merging. Release branches for testing/bug fixes before merging to `main` and `develop`.

## Team

- Narcis Petrica Balint
- Jes Mariya Wilson
- Evan Lin
- Jay Popat

## Layout

```
pom.xml            Maven parent for the Java services (messaging, app, execution-engine)
messaging/         Kafka event contract shared by app and execution-engine
app/               Java application
auth/              login and JWT issuing
execution-engine/  prices and fills orders from market data
e2e/               end-to-end tests across all services
contracts/         OpenAPI specs of the auth and trading APIs, used to generate the UI's types
ui/                Angular front end
data-pipeline/     loads market data from Yahoo Finance
db/migrations/     schema, applied on first db start
db/scripts/        reset.sh, checks.sql
docs/
```

## Getting Started

### Prerequisites

- PostgreSQL client installed
- Docker & Docker Compose

### Setup

```bash
cp .env.example .env            # set POSTGRES_PASSWORD and JWT_SECRET
./db/scripts/reset.sh           # fresh db + prices (pass e.g. 2020-01-01 for more history)
docker-compose up -d --build
# accounts belong to the user who opened them (POST /api/v1/accounts); to use the seed
# accounts ACC001-ACC005, register a user and claim them:
./db/scripts/claim-seed-accounts.sh <username>

# Connect to database (optional for direct access)
psql -h localhost -p 5434 -U technova -d technova
```

See [docs/database.md](docs/database.md) for the schema.

## Building and unit tests

The Java services build from the repo root, so `messaging` is built first and used straight
from the reactor. Building a service on its own (`cd app && mvn ...`) only works once
`messaging` is in `~/.m2` (`mvn install`).

```bash
mvn test                            # messaging, app and execution-engine
mvn test -pl app -am                # one service plus what it depends on
```

The Docker images build from the root too: `docker build -f app/Dockerfile .`. auth (NestJS)
and e2e are built separately.

## API types for the UI

The UI's request and response types are generated from the OpenAPI specs in `contracts/`, so the
build fails when the UI and a backend disagree on a field. The generated files
(`ui/src/app/api/auth-api.ts`, `ui/src/app/api/trading-api.ts`) and the specs are committed, so
building and testing the UI doesn't need a running backend. Don't edit the generated files.

Regenerate after changing a backend API (a controller, a request or a response class):

```bash
docker-compose up -d --build app   # the trading API serves its spec at :8081/api/v3/api-docs
cd ui
npm run fetch:trading-spec         # saves it to contracts/trading-api.json
npm run generate:api               # regenerates both type files
```

`contracts/auth-api.yml` isn't fetched by a script; after updating it, only run `npm run generate:api`.
Commit the spec and the generated files together, then fix whatever `ng build` reports.

## End-to-end tests

`e2e/` runs the real services, Postgres and Kafka in containers (Testcontainers) and drives them
over HTTP: register and log in, place orders, wait for the execution engine to fill them through
Kafka, and check cash and positions. Only Alpaca is stubbed (WireMock, fixed quotes). Needs Docker,
nothing else; random host ports, so it runs alongside a local docker-compose stack.

```bash
mvn -f e2e/pom.xml verify      # first run builds the service images (~3 min), then ~1 min
```

Container logs land in `e2e/target/e2e-logs/`. To reuse prebuilt images instead of building, pass
`-De2e.image.app=... -De2e.image.auth=... -De2e.image.engine=...`.

## Code quality (SonarQube)

Every project must pass the "Tech Nova" quality gate (defined in `sonar/quality-gate.sh`).

```bash
# 1. Start SonarQube: http://localhost:8083 (admin/admin, change on first login)
docker-compose --profile quality up -d sonarqube

# 2. As admin: My Account > Security > generate a *User* token (squ_...)
export SONAR_TOKEN=<token>

# 3. Create the gate (once per fresh SonarQube, and after editing the script)
./sonar/quality-gate.sh

# 4. Scan all projects; fails if any of them fails the gate
./sonar/scan.sh
```
