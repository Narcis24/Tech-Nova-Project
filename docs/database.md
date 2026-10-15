# Database

Migrations in `db/migrations/` run in filename order on a fresh volume only. Use `./db/scripts/reset.sh` after changing one.

| Table | Loaded by |
|---|---|
| `instruments`, `accounts`, `orders`, `positions` | `V003_seed_data.sql` |
| `price_data` | `data-pipeline/load.py`, daily bars from Yahoo Finance for every instrument |

To add an instrument, add it to `V003_seed_data.sql` and reset. Seed orders use real closing prices, and `db/scripts/checks.sql` fails if they drift from `price_data`.

`price_data` is one row per symbol per trading day (`open, high, low, close, adj_close, volume`). The columns are the same for every asset class, so there is one table for all of them.

Bonds and commodities are loaded as ETFs (`TLT`, `GLD`...). Yahoo has no tradable bond prices, and its futures tickers (`GC=F`) have gaps.

Name new migrations `V006_...`. Without zero padding, `V10` would run before `V2`.

### Dashboard balance history

Apply `V010_account_balance_history.sql` to existing databases (Compose initialization scripts
only run on a new volume). The app captures actual cash and portfolio values at 16:15 America/New_York
on weekdays, after the US close. Run the price pipeline before then. Missing same-day prices
cause the affected account to be skipped; holidays with no bars are skipped entirely. The cron
is configurable with `dashboard.balance-snapshot-cron`.

Snapshots are immutable and never backfilled from current holdings. A new installation/account
shows history unavailable until a prior session has been captured. The UI displays the snapshot's
actual date, including gaps when the app or price pipeline was offline. Balance change includes
cash transfers and trades; it is not an investment-return calculation.

Market overview uses the latest 20 stored daily closes per instrument. Its as-of dates are part of
the response; polling the dashboard does not make a daily price feed into an intraday quote feed.

Run `docker compose --profile market-data up -d --build market-data` to refresh recent daily
bars every 60 seconds. During the trading session, the current day's bar is provisional and
provider-delayed. Failed refreshes roll back atomically and retry without erasing good data.
This optional service also keeps today's close available for the balance snapshot schedule;
it does not require Alpaca credentials. The execution engine still requires its own quote feed
for market-order fills.
