"""Load company and fund facts from Yahoo Finance for every instrument in the database.

Usage: python fundamentals.py

Fills instrument_profiles (valuation, profile, fund facts) and instrument_financials (income
statement lines per quarter and year). They change quarterly, so a daily or weekly run is plenty.
Unlike prices, one symbol failing does not stop the rest: its old rows stay and it is retried next run.
"""

import math
import os
import sys
from datetime import datetime, timezone

import psycopg
import yfinance as yf

# Yahoo statement row -> instrument_financials column
STATEMENT_LINES = {
    "Total Revenue": "revenue",
    "Gross Profit": "gross_profit",
    "Operating Income": "operating_income",
    "Net Income": "net_income",
    "Diluted EPS": "eps_diluted",
}
# The most recent periods to keep per statement
QUARTERS = 8
YEARS = 4


def number(value):
    """A finite number, or None for Yahoo's missing values (None, NaN, 'Infinity')."""
    try:
        value = float(value)
    except (TypeError, ValueError):
        return None
    return value if math.isfinite(value) else None


def percent(value):
    """Yahoo gives some ratios in percent (0.31 meaning 0.31%); the database keeps fractions."""
    value = number(value)
    return None if value is None else value / 100


def profile_row(symbol, info, now):
    """The instrument_profiles row for one symbol, from yfinance's Ticker.info."""
    employees = number(info.get("fullTimeEmployees"))
    return {
        "symbol": symbol,
        "quote_type": info.get("quoteType"),
        "sector": info.get("sector"),
        "industry": info.get("industry"),
        "category": info.get("category"),
        "fund_family": info.get("fundFamily"),
        "description": info.get("longBusinessSummary"),
        "website": info.get("website"),
        "employees": int(employees) if employees is not None else None,
        "market_cap": number(info.get("marketCap")),
        "total_assets": number(info.get("totalAssets")),
        "trailing_pe": number(info.get("trailingPE")),
        "forward_pe": number(info.get("forwardPE")),
        "eps_ttm": number(info.get("trailingEps")),
        "eps_forward": number(info.get("forwardEps")),
        "dividend_yield": percent(info.get("dividendYield")),
        "expense_ratio": percent(info.get("netExpenseRatio")),
        "beta": number(info.get("beta")),
        "revenue_ttm": number(info.get("totalRevenue")),
        "gross_profit_ttm": number(info.get("grossProfits")),
        "profit_margin": number(info.get("profitMargins")),
        "revenue_growth": number(info.get("revenueGrowth")),
        "updated_at": now,
    }


def statement_rows(symbol, statement, period_type, keep):
    """instrument_financials rows from a yfinance income statement: lines as rows, periods as columns."""
    if statement is None or statement.empty:
        return []
    periods = sorted(statement.columns, reverse=True)[:keep]
    rows = []
    for period in periods:
        values = {
            column: number(statement.at[line, period]) if line in statement.index else None
            for line, column in STATEMENT_LINES.items()
        }
        # Yahoo lists the oldest period with every line empty when it has no figures for it
        if all(value is None for value in values.values()):
            continue
        rows.append({"symbol": symbol, "period_type": period_type, "period_end": period.date(), **values})
    return rows


def save(cur, symbol, profile, financials):
    columns = list(profile)
    cur.execute(
        f"""INSERT INTO instrument_profiles ({", ".join(columns)})
            VALUES ({", ".join("%(" + c + ")s" for c in columns)})
            ON CONFLICT (symbol) DO UPDATE SET
            {", ".join(c + " = EXCLUDED." + c for c in columns if c != "symbol")}""",
        profile,
    )
    # A restated period replaces the old figures; periods Yahoo no longer lists are kept
    cur.executemany(
        """INSERT INTO instrument_financials
               (symbol, period_type, period_end, revenue, gross_profit, operating_income, net_income, eps_diluted)
           VALUES (%(symbol)s, %(period_type)s, %(period_end)s, %(revenue)s, %(gross_profit)s,
                   %(operating_income)s, %(net_income)s, %(eps_diluted)s)
           ON CONFLICT (symbol, period_type, period_end) DO UPDATE SET
               revenue = EXCLUDED.revenue, gross_profit = EXCLUDED.gross_profit,
               operating_income = EXCLUDED.operating_income, net_income = EXCLUDED.net_income,
               eps_diluted = EXCLUDED.eps_diluted""",
        financials,
    )


def load_fundamentals(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT symbol FROM instruments ORDER BY symbol")
        symbols = [r[0] for r in cur.fetchall()]
    failed = []
    for symbol in symbols:
        try:
            ticker = yf.Ticker(symbol)
            profile = profile_row(symbol, ticker.info, datetime.now(timezone.utc).replace(tzinfo=None))
            # Funds and commodities have no income statement
            financials = []
            if profile["quote_type"] == "EQUITY":
                financials = statement_rows(symbol, ticker.quarterly_income_stmt, "QUARTERLY", QUARTERS)
                financials += statement_rows(symbol, ticker.income_stmt, "ANNUAL", YEARS)
            with conn.transaction(), conn.cursor() as cur:
                save(cur, symbol, profile, financials)
            print(f"{symbol}: {profile['quote_type']}, {len(financials)} periods")
        except Exception as error:  # noqa: BLE001 - one symbol's bad data must not stop the others
            failed.append(symbol)
            print(f"{symbol}: skipped ({error})", file=sys.stderr)
    if failed:
        print(f"no fundamentals for {', '.join(failed)}; rerun later", file=sys.stderr)
    return failed


if __name__ == "__main__":
    with psycopg.connect(os.environ["DB_URL"], autocommit=True) as connection:
        sys.exit(1 if load_fundamentals(connection) else 0)
