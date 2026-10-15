# Database

Migrations in `db/migrations/` run in filename order on a fresh volume only. Use `./db/scripts/reset.sh` after changing one.

| Table | Loaded by |
|---|---|
| `instruments`, `accounts`, `orders`, `positions` | `V003_seed_data.sql` |
| `price_data` | `data-pipeline/load.py`, daily bars from Yahoo Finance for every instrument |

To add an instrument, add it to `V003_seed_data.sql` and reset. Seed orders use real closing prices, and `db/scripts/checks.sql` fails if they drift from `price_data`.

`price_data` is one row per symbol per trading day (`open, high, low, close, adj_close, volume`). The columns are the same for every asset class, so there is one table for all of them.

Bonds and commodities are loaded as ETFs (`TLT`, `GLD`...). Yahoo has no tradable bond prices, and its futures tickers (`GC=F`) have gaps.

Name new migrations with the next free number, zero padded (currently `V012_...`). Without zero padding, `V10` would run before `V2`.

### Dashboard market data

Market overview uses the latest 20 stored daily closes per instrument. Its as-of dates are part of
the response; polling the dashboard does not make a daily price feed into an intraday quote feed.

Run `docker compose --profile market-data up -d --build market-data` to refresh recent daily
bars every 60 seconds. During the trading session, the current day's bar is provisional and
provider-delayed. Failed refreshes roll back atomically and retry without erasing good data.
This optional service does not require Alpaca credentials. The execution engine still requires
its own quote feed for market-order fills.
