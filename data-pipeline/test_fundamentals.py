"""Unit tests for the Yahoo-to-row mapping in fundamentals.py; no network or database.

Run: python -m unittest
"""

import math
import unittest
from datetime import date, datetime

import pandas as pd

from fundamentals import number, percent, profile_row, statement_rows

NOW = datetime(2026, 10, 23, 12, 0)


class NumberTest(unittest.TestCase):
    def test_keeps_finite_numbers_and_drops_missing_ones(self):
        self.assertEqual(number(8.72), 8.72)
        self.assertEqual(number("1.5"), 1.5)
        for missing in (None, math.nan, "Infinity", "n/a"):
            self.assertIsNone(number(missing))

    def test_percent_becomes_a_fraction(self):
        self.assertAlmostEqual(percent(0.31), 0.0031)
        self.assertIsNone(percent(None))


class ProfileRowTest(unittest.TestCase):
    def test_maps_a_company(self):
        row = profile_row(
            "AAPL",
            {
                "quoteType": "EQUITY",
                "sector": "Technology",
                "fullTimeEmployees": 150000,
                "marketCap": 5081985449984,
                "trailingPE": 39.93,
                "trailingEps": 8.72,
                "dividendYield": 0.31,
                "profitMargins": 0.276,
                "longBusinessSummary": "Apple designs phones.",
            },
            NOW,
        )
        self.assertEqual(row["symbol"], "AAPL")
        self.assertEqual(row["quote_type"], "EQUITY")
        self.assertEqual(row["employees"], 150000)
        self.assertEqual(row["market_cap"], 5081985449984)
        self.assertEqual(row["eps_ttm"], 8.72)
        self.assertAlmostEqual(row["dividend_yield"], 0.0031)
        self.assertEqual(row["profit_margin"], 0.276)
        self.assertIsNone(row["total_assets"])
        self.assertEqual(row["updated_at"], NOW)

    def test_maps_a_fund(self):
        row = profile_row(
            "SPY",
            {"quoteType": "ETF", "category": "Large Blend", "totalAssets": 8.09e11, "netExpenseRatio": 0.0945},
            NOW,
        )
        self.assertEqual(row["category"], "Large Blend")
        self.assertEqual(row["total_assets"], 8.09e11)
        self.assertAlmostEqual(row["expense_ratio"], 0.000945)
        self.assertIsNone(row["eps_ttm"])
        self.assertIsNone(row["employees"])


class StatementRowsTest(unittest.TestCase):
    statement = pd.DataFrame(
        {
            pd.Timestamp("2026-06-30"): [100.0, 46.0, 30.0, 25.0, 1.6],
            pd.Timestamp("2026-03-31"): [90.0, 41.0, 27.0, 22.0, 1.4],
            pd.Timestamp("2025-12-31"): [124.0, 58.0, 42.0, 36.0, 2.4],
            pd.Timestamp("2025-03-31"): [math.nan] * 5,
        },
        index=["Total Revenue", "Gross Profit", "Operating Income", "Net Income", "Diluted EPS"],
    )

    def test_newest_periods_first_up_to_the_limit(self):
        rows = statement_rows("AAPL", self.statement, "QUARTERLY", 2)
        self.assertEqual([r["period_end"] for r in rows], [date(2026, 6, 30), date(2026, 3, 31)])
        self.assertEqual(
            rows[0],
            {
                "symbol": "AAPL",
                "period_type": "QUARTERLY",
                "period_end": date(2026, 6, 30),
                "revenue": 100.0,
                "gross_profit": 46.0,
                "operating_income": 30.0,
                "net_income": 25.0,
                "eps_diluted": 1.6,
            },
        )

    def test_skips_periods_without_figures_and_tolerates_missing_lines(self):
        rows = statement_rows("AAPL", self.statement.drop(index="Gross Profit"), "ANNUAL", 10)
        self.assertEqual(len(rows), 3)
        self.assertTrue(all(r["gross_profit"] is None for r in rows))

    def test_no_statement_means_no_rows(self):
        self.assertEqual(statement_rows("SPY", pd.DataFrame(), "ANNUAL", 4), [])
        self.assertEqual(statement_rows("SPY", None, "ANNUAL", 4), [])


if __name__ == "__main__":
    unittest.main()
