# TechNova - Enterprise Trading Platform

**An enterprise-grade trading application with multi-asset support, order management, and real-time position tracking.**

## Branching Strategy

`develop` holds the latest finished work; start from it. Each sprint has a `sprint-N` branch, merged
into `develop` at the end of the sprint. Work happens on `feature/` and `fix/` branches off the
current sprint branch and is merged back through a reviewed pull request. The GitHub remote is named
`Tech-Nova-Project` in our clones.

## Team

- Narcis Petrica Balint
- Jes Mariya Wilson
- Evan Lin
- Jay Popat

## Layout

```
pom.xml            Maven parent for the Java services (messaging, app, execution-engine)
messaging/         Kafka event contract shared by app and execution-engine
app/               Java application: accounts, orders, portfolio, risk checks (Spring Boot)
auth/              login, registration, password reset and JWT issuing (NestJS)
execution-engine/  prices and fills orders from market data (Spring Boot)
ui/                web front end (Angular 19), served by nginx in its container
e2e/               end-to-end tests across all services
data-pipeline/     loads market data from Yahoo Finance
db/migrations/     schema, applied on first db start
db/scripts/        reset.sh, claim-seed-accounts.sh, checks.sql
sonar/             SonarQube quality gate and scan scripts
contracts/         OpenAPI contract for auth
docs/
```

## Getting Started

### Prerequisites

To run the app you only need **Docker with Docker Compose** (v2.20+). Everything else runs in
containers.

For building and testing outside containers:

| Tool | Version | Used for |
|---|---|---|
| Java (JDK) | 21 | app, execution-engine, e2e |
| Maven | 3.8+ | Java builds and tests |
| Node.js | 22 or 24 | ui (22+) and auth (24) |
| Google Chrome or Chromium | any | ui unit tests (headless) |
| PostgreSQL client (`psql`) | any | optional, direct database access |

### Setup

```bash
git clone -b develop git@github.com:Narcis24/Tech-Nova-Project.git   # needs an SSH key on GitHub,
cd Tech-Nova-Project                     # or use https://github.com/Narcis24/Tech-Nova-Project.git
cp .env.example .env            # set POSTGRES_PASSWORD and JWT_SECRET (see the comments in the file)
./db/scripts/reset.sh           # fresh db + prices (pass e.g. 2020-01-01 for more history)
docker-compose up -d --build    # first build takes a few minutes
```

`reset.sh` runs `docker-compose down -v`: it **deletes all data** (users, accounts, orders). Use it
for the first setup or when you want a clean database, not to restart the stack. To stop and
start without losing data, use `docker-compose stop` and `docker-compose up -d`.

Then open the app at **http://localhost:8091** and create an account with **Create an account**.

Accounts belong to the user who opened them. To use the seed accounts ACC001-ACC005 (with cash,
positions and orders), register a user and claim them:

```bash
./db/scripts/claim-seed-accounts.sh <username>
```

### Optional settings in `.env`

- **Market data:** without `ALPACA_KEY_ID`/`ALPACA_SECRET_KEY` (free at https://alpaca.markets)
  there are no live quotes, so market orders are rejected. Orders placed outside US market hours
  (Mon-Fri 09:30-16:00 New York time) are accepted and wait as Open until the market opens.
- **Password reset emails:** set `COMPOSE_PROFILES=mail` to also start Mailpit, which catches
  every email in a web inbox. The link in the email comes from `RESET_URL`; on a cloud VM set it
  to the VM's IP, e.g. `http://<vm-ip>:8091/reset-password` (`hostname -I` shows the IP).
- **Ports:** every host port can be changed; defaults below.

### Addresses

| What | URL |
|---|---|
| Web app | http://localhost:8091 |
| Trading API (Swagger) | http://localhost:8081/api/swagger-ui.html |
| Auth API (Swagger) | http://localhost:8082/auth/docs |
| Kafka UI | http://localhost:8085 |
| Mailpit inbox (`mail` profile) | http://localhost:8025 |
| SonarQube (`quality` profile) | http://localhost:8083 |
| PostgreSQL | `localhost:5434`, user and db `technova` |

On a cloud VM, replace `localhost` with the VM's IP, or forward the port in VS Code (same port
number on both sides). Only ports 8000-8100 are reachable on our VMs, so Kafka UI, Mailpit and
PostgreSQL need forwarding.

```bash
psql -h localhost -p 5434 -U technova -d technova   # optional direct database access
```

See [docs/database.md](docs/database.md) for the schema and [docs/Authentication.md](docs/Authentication.md)
for the login flow. Optional services are Docker Compose *profiles*: `--profile mail`,
`--profile quality` or `COMPOSE_PROFILES` in `.env`.

### Troubleshooting

- **`network ... not found` when starting Mailpit or SonarQube:** the stack was taken down
  without that profile, so its old container points at a deleted network. Recreate it:
  `docker-compose --profile mail up -d --force-recreate mailpit` (or `--profile quality ... sonarqube`).
- **`./sonar/quality-gate.sh` exits with 22 and no message:** SonarQube refused the token (403).
  It needs a *User* token (`squ_...`) of an admin, not a project or global analysis token.
- **`ECONNRESET` / 502 right after starting:** the Java services take up to a minute to start;
  wait and reload.
- **A change to the code does not show up:** containers run the image they were built from;
  rebuild with `docker-compose up -d --build <service>`.

## Building and unit tests

### Java (messaging, app, execution-engine)

The Java services build from the repo root, so `messaging` is built first and used straight
from the reactor. Building a service on its own (`cd app && mvn ...`) only works once
`messaging` is in `~/.m2` (`mvn install`).

```bash
mvn test                            # messaging, app and execution-engine
mvn test -pl app -am                # one service plus what it depends on
```

The Docker images build from the root too: `docker build -f app/Dockerfile .`.

### Auth (NestJS)

```bash
cd auth
npm ci
npm test                            # unit + HTTP tests (Vitest), no database needed
npm run test:cov                    # the same with coverage, used by Jenkins and SonarQube
```

### UI (Angular)

```bash
cd ui
npm ci
npm start                           # dev server on http://localhost:4200, proxies to the stack
npm run check                       # lint, format, type check, unit tests with coverage, build
```

See [ui/README.md](ui/README.md) for running the dev server on your IP, regenerating the API
clients and the Playwright smoke tests against the UI container.

## Replaying a trading day

The execution engine writes every order, quote and decision it handles to a daily journal,
`/data/journal/<yyyy-MM-dd>.jsonl` (UTC day) on the `engine_journal` volume. A replay feeds a
journal's orders and quotes into a fresh engine and checks that it makes the same decisions;
it exits 0 when all match and 1 otherwise, listing each difference.

```bash
# today's journal, inside the running engine container
docker exec tech-nova-project-execution-engine-1 sh -c 'java -Xmx256m -cp app.jar \
  -Dloader.main=com.neueda.trading.engine.Replay \
  org.springframework.boot.loader.launch.PropertiesLauncher /data/journal/$(date -u +%F).jsonl'

# a journal file on your machine (after mvn install)
docker cp tech-nova-project-execution-engine-1:/data/journal/2026-10-19.jsonl .
mvn -q -pl execution-engine exec:java -Dexec.args=2026-10-19.jsonl
```

Journals can also be gzipped (`.jsonl.gz`); the replay reads both. It also takes several files or
folders (every journal in a folder) and `--report <file>`, which saves every difference with the order
and the last quote behind it as JSON:

```bash
mvn -q -pl execution-engine exec:java \
  -Dexec.args="--report shadow-report.json execution-engine/src/test/resources/journals"
```

### Golden journals

`execution-engine/src/test/resources/journals/` holds recorded days that `mvn test` replays
(`GoldenJournalTest`), so a change that makes any recorded decision come out differently fails the
build:

- `2026-10-14-edge-cases.jsonl`: small, written by hand; every tricky rule on purpose.
- `2026-10-15-full-day.jsonl.gz`: a realistic full day (28,000 lines), made by `JournalGenerator`.

To add a real day from a running stack (replace the account ids first; they are real data):

```bash
docker cp tech-nova-project-execution-engine-1:/data/journal/2026-10-19.jsonl .
gzip 2026-10-19.jsonl && mv 2026-10-19.jsonl.gz execution-engine/src/test/resources/journals/
```

Replace a golden journal only when a change to the engine's decisions is **intended**: re-record it
(the `JournalGenerator` javadoc has the command), say in the pull request which decisions changed
and why, and never re-record just to make a failing test pass.

## End-to-end tests

`e2e/` runs the real services, Postgres and Kafka in containers (Testcontainers) and drives them
over HTTP: register and log in, place orders, wait for the execution engine to fill them through
Kafka, and check cash and positions. Only Alpaca is stubbed (WireMock, fixed quotes). Needs Docker,
Java and Maven; random host ports, so it runs alongside a local docker-compose stack.

```bash
mvn -f e2e/pom.xml verify      # first run builds the service images (~3 min), then ~1 min
```

Container logs land in `e2e/target/e2e-logs/`. To reuse prebuilt images instead of building, pass
`-De2e.image.app=... -De2e.image.auth=... -De2e.image.engine=...`.

## Code quality (SonarQube)

Every project must pass the "Tech Nova" quality gate (defined in `sonar/quality-gate.sh`).

```bash
# 1. Start SonarQube: http://localhost:8083 (admin/admin, change on first login).
#    --force-recreate avoids "network not found" after reset.sh; the data lives in volumes
docker-compose --profile quality up -d --force-recreate sonarqube

# 2. As admin: My Account > Security > generate a *User* token (squ_...)
export SONAR_TOKEN=<token>

# 3. Create the gate (once per fresh SonarQube, and after editing the script)
./sonar/quality-gate.sh

# 4. UI coverage, which the scan reads
(cd ui && npm ci && npm run test:ci)

# 5. Scan all projects (app, execution-engine, auth, data-pipeline, ui); fails if any fails the gate
./sonar/scan.sh
```

SonarQube needs about 2 GB of memory; stop it when you are done:
`docker-compose --profile quality stop sonarqube`.

## CI (Jenkins)

The `Jenkinsfile` runs, in order: GitLeaks secret scan, image builds, UI checks, Java and auth
unit tests, the shadow gate, a data-pipeline smoke test, the e2e tests, SonarQube and a Trivy
vulnerability scan of every image. Jenkins needs a "Secret text" credential `sonar-token`. A full run
is heavy; on a small VM run the stages above by hand instead.

**Shadow gate** (only on `develop` and `sprint-*`, the branches that get released): the candidate
engine replays the golden journals and any real days kept on the Jenkins machine in
`/var/lib/jenkins/shadow-journals` (not in git). One changed decision fails the build. The report,
`execution-engine/target/shadow-gate/report.json`, is archived with every build; open it from the
build page to see each changed decision with its order and quote. It needs no running services and
takes a few seconds.
