"""load_prices against a real Postgres with the real migrations; only Yahoo is faked.

Run with tests/run.sh, which starts the database and sets DB_URL.
"""

import os
from datetime import date

import pandas as pd
import psycopg
import pytest

import load


@pytest.fixture
def cur():
    # each test runs in a transaction that is rolled back, so tests do not see each other's rows
    with psycopg.connect(os.environ["DB_URL"]) as conn:
        with conn.cursor() as cur:
            yield cur
        conn.rollback()


def fake_download(close_by_symbol, days=(date(2025, 1, 2), date(2025, 1, 3))):
    """A yf.download stand-in shaped like group_by="ticker": columns are (symbol, field)."""

    def download(symbols, **_):
        frames = {}
        for s in symbols:
            close = close_by_symbol.get(s, 100.0)
            frames[s] = pd.DataFrame(
                {
                    "Open": close - 1, "High": close + 1, "Low": close - 2,
                    "Close": close, "Adj Close": close, "Volume": 1000,
                },
                index=pd.DatetimeIndex(days),
            )
        return pd.concat(frames, axis=1)

    return download


def rows(cur):
    cur.execute("SELECT count(*) FROM price_data")
    return cur.fetchone()[0]


def close_of(cur, symbol, day):
    cur.execute("SELECT close FROM price_data WHERE symbol = %s AND trade_date = %s", (symbol, day))
    return float(cur.fetchone()[0])


def instruments(cur):
    cur.execute("SELECT count(*) FROM instruments")
    return cur.fetchone()[0]


def test_writes_a_row_per_symbol_and_day(cur, monkeypatch):
    monkeypatch.setattr(load.yf, "download", fake_download({"AAPL": 195.5}))

    load.load_prices(cur, "2025-01-01")

    assert rows(cur) == 2 * instruments(cur)
    assert close_of(cur, "AAPL", date(2025, 1, 3)) == 195.5


def test_rerun_updates_existing_days_instead_of_duplicating(cur, monkeypatch):
    monkeypatch.setattr(load.yf, "download", fake_download({"AAPL": 195.5}))
    load.load_prices(cur, "2025-01-01")

    monkeypatch.setattr(load.yf, "download", fake_download({"AAPL": 210.0}))
    load.load_prices(cur, "2025-01-01")

    assert rows(cur) == 2 * instruments(cur)
    assert close_of(cur, "AAPL", date(2025, 1, 3)) == 210.0


def test_aborts_without_writing_when_a_symbol_has_no_data(cur, monkeypatch):
    # yfinance reports a failed ticker as all-NaN columns rather than raising
    monkeypatch.setattr(load.yf, "download", fake_download({"AAPL": float("nan")}))

    with pytest.raises(SystemExit, match="AAPL"):
        load.load_prices(cur, "2025-01-01")

    assert rows(cur) == 0
