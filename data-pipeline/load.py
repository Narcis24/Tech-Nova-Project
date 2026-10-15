"""Load daily prices from Yahoo Finance for every instrument in the database.

Usage: python load.py [start_date]   (default 2025-01-01, which covers the seed orders)
"""

import os
import argparse
import time
from datetime import date, timedelta

import psycopg
import yfinance as yf


def load_prices(cur, start):
    cur.execute("SELECT symbol FROM instruments ORDER BY symbol")
    symbols = [r[0] for r in cur.fetchall()]
    data = yf.download(symbols, start=start, auto_adjust=False, group_by="ticker", progress=False)
    # yfinance reports failed tickers as empty columns instead of raising; abort so the transaction rolls back
    bars_by_symbol = {s: data[s].dropna() for s in symbols}
    missing = [s for s, bars in bars_by_symbol.items() if bars.empty]
    if missing:
        raise RuntimeError(f"no price data for {', '.join(missing)}; nothing was written, rerun")
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


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("start_date", nargs="?")
    parser.add_argument("--watch", action="store_true", help="Refresh recent daily bars continuously (provider-delayed)")
    parser.add_argument("--interval", type=int, default=60, help="Seconds between refreshes, minimum 60")
    args = parser.parse_args()
    if args.interval < 60:
        parser.error("interval must be at least 60 seconds")
    while True:
        start = args.start_date or ((date.today() - timedelta(days=7)).isoformat() if args.watch else "2025-01-01")
        try:
            with psycopg.connect(os.environ["DB_URL"]) as conn, conn.cursor() as cur:
                load_prices(cur, start)
            print("Price refresh committed", flush=True)
        except Exception as error:
            if not args.watch:
                raise
            # The connection context rolled back the entire refresh. Keep the last good prices.
            print(f"Price refresh failed; retrying in {args.interval}s: {error}", flush=True)
        if not args.watch:
            break
        time.sleep(args.interval)
