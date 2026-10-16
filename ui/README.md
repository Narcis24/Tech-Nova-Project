# Ui

## Development server

There are two ways to run it. Both reload automatically when you change a source file.

**On localhost only** (the default; only your own machine can open it):

```bash
npm start
```

Open `http://localhost:4200/`.

**On your machine's IP address** (anyone who can reach your machine can open it, e.g. a teammate on the same network):

```bash
npm run start:network
```

Open `http://<your-ip>:8090/`. The terminal prints the address under "Network". On Linux, `hostname -I` shows your IP.
`http://localhost:8090/` still works on your own machine. Port 8090 must be open in your firewall, or in the security group on a cloud VM.

8090 is used because only ports 8000-8100 are reachable on our machines, and 8080-8083 are taken by Jenkins,
the trading API, auth and SonarQube. If 8090 is busy on yours, pick another free port in that range:

```bash
npm run start:network -- --port 8095
```

Either way, `/api` and `/auth` requests are forwarded to the backend on the same machine as `ng serve`
(ports 8081 and 8082, see `proxy.conf.json`), so start the backend there with `docker-compose up -d`.

## Production container

From the repository root, with the backend environment configured in `.env`:

```bash
docker compose up -d --build ui
```

Open `http://localhost:8091` (`UI_PORT` overrides the host port). This serves the
production build through Nginx, not `ng serve`. `/api` and `/auth` are proxied to
`app:8081` and `auth-service:8082`, so browser requests remain on the same origin.
For other deployments, set `API_UPSTREAM` and `AUTH_UPSTREAM` to the backend
`host:port` values. Do not put API credentials or secrets in the Angular build.
Deep links fall back to `index.html`; missing static assets return 404. The
`/healthz` endpoint checks the static server. TLS belongs at the deployment ingress.

## Checks

From `ui/`, with Node 22 and Chrome/Chromium installed:

```bash
npm ci
npm run check
```

This runs lint, browser-test type checks, formatting checks, unit tests with LCOV
coverage, and the production build. Set `CHROME_BIN` if Chromium isn't on the usual
path. CI uses the `ci` target in `ui/Dockerfile`; the Docker commands are in the
root `Jenkinsfile`. Reports go to `ui/coverage`. Generated API clients are excluded.
Use `npm run format` before review. Ordinary `npm test` keeps Chrome's sandbox;
`test:ci` uses the container launcher.

## Browser smoke tests

These are manual checks against an existing disposable environment. They cover
desktop and mobile login, dashboard, validation and order review without confirming
an order. Jenkins does not start a UI smoke stack or run these browser tests.

From `ui/`, with the UI and backends already running:

```bash
npx playwright install chromium
UI_BASE_URL=http://localhost:8091 npm run test:smoke
npx playwright show-report          # HTML report of the last run on http://localhost:9323
```

This creates temporary users and funded test accounts in that environment.
Set `CHROME_BIN` to reuse an installed Chromium instead of downloading one.
Node types are confined to `e2e/tsconfig.json`.

## API clients

`npm run fetch:specs` downloads live OpenAPI documents using `curl`, then
`npm run generate:clients` regenerates the clients. Start both backends first.

## Jenkins and Sonar

Jenkins runs the UI checks, archives coverage, scans UI coverage in
`tech-nova-ui`, and scans the dependency lockfile and production image with Trivy.
Run `npm run test:ci` in `ui/` before a local `sonar/scan.sh` run; the scanner
requires the LCOV report instead of rebuilding or rerunning UI checks.
Sonar uses the existing server, token credential and quality gate.

Existing Angular 19 advisories require a major upgrade. The dependency security
gate currently fails on those findings; no exceptions were added to hide them.
