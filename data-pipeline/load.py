"""Load instruments and daily prices from Yahoo Finance, then generate orders and positions from those prices.

Usage: python load.py [period]   (default 2y)
"""

import csv
import os
import random
import sys
import uuid
from datetime import datetime, time

import psycopg
import yfinance as yf


def load_market_data(cur, period):
    with open("symbols.csv") as f:
        instruments = list(csv.DictReader(f))
    cur.executemany(
        """INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
           VALUES (%(symbol)s, %(name)s, %(asset_class)s, 'USD', true)
           ON CONFLICT (symbol) DO UPDATE SET name = EXCLUDED.name, asset_class = EXCLUDED.asset_class""",
        instruments,
    )

    symbols = [i["symbol"] for i in instruments]
    data = yf.download(symbols, period=period, auto_adjust=False, group_by="ticker", progress=False)
    # yfinance reports failed tickers as empty columns instead of raising; abort so the transaction rolls back
    bars_by_symbol = {s: data[s].dropna() for s in symbols}
    missing = [s for s, bars in bars_by_symbol.items() if bars.empty]
    if missing:
        sys.exit(f"no price data for {', '.join(missing)}; nothing was written, rerun")
    for symbol, bars in bars_by_symbol.items():
        cur.executemany(
            """INSERT INTO price_data (symbol, trade_date, open, high, low, close, adj_close, volume)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
               ON CONFLICT (symbol, trade_date) DO UPDATE SET
                   open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low,
                   close = EXCLUDED.close, adj_close = EXCLUDED.adj_close, volume = EXCLUDED.volume""",
            [
                (symbol, day.date(), b["Open"], b["High"], b["Low"], b["Close"], b["Adj Close"], int(b["Volume"]))
                for day, b in bars.iterrows()
            ],
        )
        print(f"{symbol}: {len(bars)} bars")


def seed_orders(cur):
    rng = random.Random(42)
    cur.execute("DELETE FROM positions")
    cur.execute("DELETE FROM orders")
    cur.execute("SELECT account_id FROM accounts ORDER BY account_id")
    accounts = [r[0] for r in cur.fetchall()]
    # exclude today so created_on can't be in the future
    cur.execute(
        "SELECT symbol, trade_date, close FROM price_data WHERE trade_date < CURRENT_DATE ORDER BY symbol, trade_date"
    )
    bars = cur.fetchall()

    orders = []
    for account in accounts:
        for n, (symbol, day, close) in enumerate(rng.sample(bars, 5), start=1):
            price = round(close, 2)
            quantity = max(1, int(rng.uniform(2_000, 20_000) / float(price)))
            status = rng.choice(["FILLED"] * 8 + ["PENDING", "CANCELLED"])
            orders.append(
                (
                    uuid.uuid4(),
                    account,
                    symbol,
                    "BUY",
                    quantity,
                    price,
                    status,
                    f"{account}_{n:03d}",
                    datetime.combine(day, time(16)),
                )
            )
    cur.executemany(
        """INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on)
           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        orders,
    )
    cur.execute(
        """INSERT INTO positions (account_id, symbol, quantity, average_cost)
           SELECT account_id, symbol, sum(quantity), round(sum(quantity * price) / sum(quantity), 2)
           FROM orders WHERE status = 'FILLED'
           GROUP BY account_id, symbol"""
    )
    print(f"{len(orders)} orders")


if __name__ == "__main__":
    with psycopg.connect(os.environ["DB_URL"]) as conn, conn.cursor() as cur:
        load_market_data(cur, sys.argv[1] if len(sys.argv) > 1 else "2y")
        seed_orders(cur)
