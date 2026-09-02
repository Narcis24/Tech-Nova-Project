### Branching Strategy

As a team, we revisited the trunk-based vs Gitflow-style comparison and discussed our team size, expected merge frequency, and the process overhead appropriate for a 10-week project.

**Decision:** The team has agreed to use a **Gitflow branching strategy**.

We expect to merge work back into the main development branch approximately **once every 3-5 days**, which fits well with Gitflow's structured use of feature, develop, and release branches.

In this scenario, usually teams would work by themselves or in pairs for pair programming. 

Each team member would work on a feature that doesn't conflict with another's work, using their own `feature/` branch created off `develop`. Once a feature is complete, the author opens
a pull request back into `develop`, and at least one other team member reviews and approves the changes before merging. This keeps `develop` stable while still letting people work in parallel.

When the team is ready to prepare a release, a `release/` branch is cut from `develop` for final testing and bug fixes, then merged into both `main` and `develop`. If an urgent fix is needed in production.


### Team
- Narcis Petrica Balint
- Jes Mariya Wilson
- Evan Lin
- Jay Popat

### Connecting to PSQL

#### Prerequisites
- PostgreSQL client (`psql`) installed on your machine
- Docker containers running (`docker-compose up -d`)
- `.env` file configured with database credentials

#### Connection Command

```bash
psql -h localhost -p 5434 -U technova -d technova
```

**Connection Parameters:**
- `-h localhost` - Host (localhost for local connections, or EC2 IP for remote)
- `-p 5434` - Port (PostgreSQL running on port 5434, mapped from internal 5432)
- `-U technova` - Username
- `-d technova` - Database name

**Password:** `insert_password_here` (when prompted)

#### Useful PSQL Commands

Once connected to the database:

```sql
-- List all tables
\dt

-- Describe a specific table (show columns and types)
\d table_name

-- View all data from a table
SELECT * FROM table_name;

-- View specific columns
SELECT column1, column2 FROM table_name LIMIT 10;

-- Get table row counts
SELECT COUNT(*) FROM table_name;

-- Exit PSQL
\q
```

#### Example Queries

```bash
# View all accounts
psql -h localhost -p 5434 -U technova -d technova -c "SELECT * FROM accounts;"

# View all trading instruments
psql -h localhost -p 5434 -U technova -d technova -c "SELECT * FROM instruments;"

# View all orders
psql -h localhost -p 5434 -U technova -d technova -c "SELECT * FROM orders;"
```

#### Available Tables

The database includes the following tables (auto-populated with mock data):

- **accounts** - Client trading accounts with cash balances
- **instruments** - Financial instruments available for trading (stocks, ETFs, bonds, crypto)
- **orders** - Trading orders (buy/sell transactions)
- **positions** - Current holdings and positions

#### Troubleshooting

**Connection Refused:**
- Verify Docker containers are running: `docker-compose ps`
- Check port is correct in `.env` file
- Ensure PostgreSQL container is healthy

**Authentication Failed:**
- Verify username and password in `.env`
- Check `.env` file exists in project root

**Database Doesn't Exist:**
- The database should be auto-created on first run
- If missing, restart containers: `docker-compose down && docker-compose up -d`