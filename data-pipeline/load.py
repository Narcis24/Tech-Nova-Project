"""Load daily prices from Yahoo Finance for every instrument in the database.

Usage: python load.py [start_date]   (default 2025-01-01, which covers the seed orders)
"""

import os
import sys

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


if __name__ == "__main__":
    with psycopg.connect(os.environ["DB_URL"]) as conn, conn.cursor() as cur:
        load_prices(cur, sys.argv[1] if len(sys.argv) > 1 else "2025-01-01")
