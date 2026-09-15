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
app/             Java application
data-pipeline/   loads market data from Yahoo Finance
db/migrations/   schema, applied on first db start
db/scripts/      reset.sh, checks.sql
docs/
```

## Getting Started

### Prerequisites

- PostgreSQL client installed
- Docker & Docker Compose

### Setup

```bash
cp .env.example .env            # set POSTGRES_PASSWORD
./db/scripts/reset.sh           # fresh db + market data (pass e.g. 5y for more history)
docker-compose up -d --build

# Connect to database (optional for direct access)
psql -h localhost -p 5434 -U technova -d technova
```

See [docs/database.md](docs/database.md) for the schema.
