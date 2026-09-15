# Database

Migrations in `db/migrations/` run in filename order on a fresh volume only. Use `./db/scripts/reset.sh` after changing one.

| Table | Loaded by |
|---|---|
| `accounts` | `V003_seed_accounts.sql` |
| `instruments` | `data-pipeline/load.py`, from `symbols.csv` |
| `price_data` | `data-pipeline/load.py`, daily bars from Yahoo Finance |
| `orders`, `positions` | `data-pipeline/load.py`, generated from real closing prices |

`price_data` is one row per symbol per trading day (`open, high, low, close, adj_close, volume`). The columns are the same for every asset class, so there is one table for all of them.

Bonds and commodities are loaded as ETFs (`TLT`, `GLD`...). Yahoo has no tradable bond prices, and its futures tickers (`GC=F`) have gaps.

Name new migrations `V006_...`. Without zero padding, `V10` would run before `V2`.
