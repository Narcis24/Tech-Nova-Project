# TechNova - Enterprise Trading Platform

**An enterprise-grade trading application with multi-asset support, order management, and real-time position tracking.**

## Branching Strategy

**Gitflow** - Feature work on `feature/` branches off `develop`, with PR review before merging. Release branches for testing/bug fixes before merging to `main` and `develop`.

## Team

- Narcis Petrica Balint
- Jes Mariya Wilson
- Evan Lin
- Jay Popat

## Getting Started

### Prerequisites

- PostgreSQL client installed
- Docker & Docker Compose
- `.env` file with database credentials

### Setup

```bash
# Start services
docker-compose up -d

# Connect to database (optional for direct access)
psql -h localhost -p 5434 -U technova -d technova
```

### Database

The application uses PostgreSQL with the following schema:

- **accounts** - Trading accounts with cash balances
- **instruments** - Available trading instruments (stocks, ETFs, bonds, crypto)
- **orders** - Order history and current trades
- **positions** - Real-time position tracking



### Running just the Java File
mvn clean compile exec:java -Dexec.mainClass="com.neueda.leap.SP100Fetcher"

### Python File
Just use the run button