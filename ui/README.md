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
