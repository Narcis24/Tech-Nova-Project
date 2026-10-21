#!/usr/bin/env bash
# Local dev only: fills the admin analytics with realistic sample trading. Adds 20 demo traders
# (demo_trader_01..20, no password, so nobody can sign in as them), an ACTIVE account each, and about
# six months of orders priced from the real closes in price_data. Weekdays only, mostly in market
# hours (New York), busier towards today, with popular instruments traded more.
#
# Usage: db/scripts/seed-demo-orders.sh            add (or replace) the demo data
#        db/scripts/seed-demo-orders.sh --remove   remove it again
#
# Every demo row is marked (usernames demo_trader_*, accounts DEMO-*, idempotency keys DEMO_*), so
# running it again replaces the demo data and --remove takes it all out; real data is never touched.
# The orders are written straight to the database: no events are sent, and cash and positions are
# not changed. Orders still open are PUBLISHED, not PENDING, because the app sends every PENDING
# order to the engine again when the engine restarts, which would fill them.
set -euo pipefail
cd "$(dirname "$0")/../.."

remove_sql=$(cat <<'SQL'
DELETE FROM orders WHERE idempotency_key LIKE 'DEMO\_%';
DELETE FROM accounts WHERE account_id LIKE 'DEMO-%';
DELETE FROM users WHERE username LIKE 'demo\_trader\_%';   -- user_roles go with them (ON DELETE CASCADE)
SQL
)

if [ "${1:-}" = "--remove" ]; then
  docker-compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<SQL
BEGIN;
$remove_sql
COMMIT;
SELECT 'Demo data removed' AS result;
SQL
  exit 0
fi

docker-compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<SQL
BEGIN;
$remove_sql

-- The same "random" data every run, so screenshots and checks are repeatable
SELECT setseed(0.237) AS seed \gset

-- 20 traders, each with one account. The first few trade much more than the rest.
INSERT INTO users (username, password_hash, email)
SELECT format('demo_trader_%s', lpad(n::text, 2, '0')), NULL,
       format('demo_trader_%s@demo.technova.local', lpad(n::text, 2, '0'))
FROM generate_series(1, 20) AS n;

INSERT INTO user_roles (username, role)
SELECT format('demo_trader_%s', lpad(n::text, 2, '0')), 'TRADER' FROM generate_series(1, 20) AS n;

INSERT INTO accounts (account_id, holder_name, cash_balance, status, owner_username)
SELECT format('DEMO-%s', lpad(n::text, 2, '0')), format('Demo Trader %s', n), 100000, 'ACTIVE',
       format('demo_trader_%s', lpad(n::text, 2, '0'))
FROM generate_series(1, 20) AS n;

-- Weekdays from 182 days ago to today, New York dates
CREATE TEMP TABLE demo_days ON COMMIT DROP AS
SELECT d::date AS day, row_number() OVER (ORDER BY d) AS day_number
FROM generate_series((now() AT TIME ZONE 'America/New_York')::date - 182,
                     (now() AT TIME ZONE 'America/New_York')::date, interval '1 day') AS d
WHERE extract(isodow FROM d) < 6;

-- How often each instrument is picked: the big names and index ETFs most
CREATE TEMP TABLE demo_weights ON COMMIT DROP AS
SELECT symbol,
       CASE WHEN symbol IN ('NVDA', 'AAPL', 'SPY', 'TSLA', 'MSFT') THEN 10
            WHEN symbol IN ('AMZN', 'QQQ', 'META', 'GOOGL') THEN 6
            WHEN asset_class = 'EQUITY' THEN 2
            WHEN asset_class = 'ETF' THEN 3
            ELSE 2 END AS weight
FROM instruments WHERE tradable;

CREATE TEMP TABLE demo_symbols ON COMMIT DROP AS
SELECT symbol,
       sum(weight) OVER (ORDER BY symbol) - weight AS lower_bound,
       sum(weight) OVER (ORDER BY symbol) AS upper_bound,
       sum(weight) OVER () AS total
FROM demo_weights;

-- One row per order: busier towards today (about 15 a day at the start, 45 now), with noise
CREATE TEMP TABLE demo_orders ON COMMIT DROP AS
SELECT d.day,
       random() AS pick_symbol,
       random() AS pick_trader,
       random() AS pick_time,
       random() AS pick_type,
       random() AS pick_side,
       random() AS pick_status,
       random() AS pick_size,
       random() AS pick_limit
FROM demo_days d
CROSS JOIN LATERAL generate_series(1, (15 + d.day_number * 0.23 + (random() - 0.5) * 12)::int);

INSERT INTO orders (id, account_id, symbol, side, order_type, quantity, price, status,
                    rejection_reason, idempotency_key, created_on, version)
SELECT gen_random_uuid(), account_id, symbol, side, order_type,
       greatest(1, round(target_value / close))::int,
       round((CASE WHEN order_type = 'LIMIT' THEN close * (1 + (pick_limit - 0.5) * 0.04) ELSE close END)::numeric, 2),
       status,
       CASE WHEN status = 'REJECTED' THEN
            CASE WHEN order_type = 'MARKET' THEN 'No fresh quote for ' || symbol
                 ELSE 'Insufficient funds to settle the fill' END END,
       'DEMO_' || gen_random_uuid(),
       created_on, 0
FROM (
    SELECT o.*, s.symbol, p.close,
           -- 1 to 20, the low numbers far more often
           format('DEMO-%s', lpad((1 + floor(power(o.pick_trader, 2.2) * 20))::int::text, 2, '0')) AS account_id,
           CASE WHEN o.pick_side < 0.56 THEN 'BUY' ELSE 'SELL' END AS side,
           CASE WHEN o.pick_type < 0.64 THEN 'MARKET' ELSE 'LIMIT' END AS order_type,
           -- Mostly between 9:30 and 16:00 New York, a few before the open; stored as UTC
           ((o.day + CASE WHEN o.pick_time < 0.06 THEN time '07:00' + (o.pick_time / 0.06) * interval '150 minutes'
                          ELSE time '09:30' + ((o.pick_time - 0.06) / 0.94) * interval '390 minutes' END)
              AT TIME ZONE 'America/New_York') AT TIME ZONE 'UTC' AS created_on,
           -- Worth 1,000 to about 40,000 USD (no dollar signs: the shell would expand them here)
           1000 * exp(o.pick_size * 3.7) AS target_value
    FROM demo_orders o
    JOIN demo_symbols s ON o.pick_symbol * s.total >= s.lower_bound AND o.pick_symbol * s.total < s.upper_bound
    -- The last close on or before the order's day, so prices follow the real market
    CROSS JOIN LATERAL (SELECT close FROM price_data pd
                        WHERE pd.symbol = s.symbol AND pd.trade_date <= o.day
                        ORDER BY pd.trade_date DESC LIMIT 1) p
) o
CROSS JOIN LATERAL (SELECT CASE
    -- Limit orders from the last two hours are mostly still waiting
    WHEN o.order_type = 'LIMIT' AND o.created_on > (now() AT TIME ZONE 'UTC') - interval '2 hours' AND o.pick_status < 0.7
        THEN 'PUBLISHED'
    WHEN o.order_type = 'MARKET' THEN
        CASE WHEN o.pick_status < 0.91 THEN 'FILLED' WHEN o.pick_status < 0.97 THEN 'REJECTED' ELSE 'CANCELLED' END
    ELSE
        CASE WHEN o.pick_status < 0.62 THEN 'FILLED' WHEN o.pick_status < 0.78 THEN 'CANCELLED'
             WHEN o.pick_status < 0.95 THEN 'EXPIRED' ELSE 'REJECTED' END
    END AS status) st
-- Nothing later than now
WHERE o.created_on <= now() AT TIME ZONE 'UTC';

COMMIT;

SELECT count(*) AS demo_orders,
       count(DISTINCT account_id) AS demo_accounts,
       min(created_on)::date AS first_day,
       max(created_on) AS last_order_utc,
       count(*) FILTER (WHERE status = 'FILLED') AS filled,
       count(*) FILTER (WHERE status = 'PUBLISHED') AS still_open
FROM orders WHERE idempotency_key LIKE 'DEMO\_%';
SQL
