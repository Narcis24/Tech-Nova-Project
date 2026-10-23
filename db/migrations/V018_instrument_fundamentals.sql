-- What an instrument is and how the company behind it is doing, from Yahoo Finance
-- (data-pipeline/fundamentals.py). Shown on the trade page; never used to price or fill orders.
-- Ratios are fractions (0.0031 is 0.31%), money is USD.
CREATE TABLE instrument_profiles (
    symbol            VARCHAR(20)   PRIMARY KEY REFERENCES instruments(symbol),
    quote_type        VARCHAR(20),
    sector            VARCHAR(100),
    industry          VARCHAR(100),
    category          VARCHAR(100),
    fund_family       VARCHAR(100),
    description       TEXT,
    website           VARCHAR(255),
    employees         INT,
    market_cap        NUMERIC(22,2),
    total_assets      NUMERIC(22,2),
    trailing_pe       NUMERIC(14,4),
    forward_pe        NUMERIC(14,4),
    eps_ttm           NUMERIC(14,4),
    eps_forward       NUMERIC(14,4),
    dividend_yield    NUMERIC(10,6),
    expense_ratio     NUMERIC(10,6),
    beta              NUMERIC(10,4),
    revenue_ttm       NUMERIC(22,2),
    gross_profit_ttm  NUMERIC(22,2),
    profit_margin     NUMERIC(10,6),
    revenue_growth    NUMERIC(10,6),
    updated_at        TIMESTAMP     NOT NULL
);

-- Income statement lines per reported period, for companies only
CREATE TABLE instrument_financials (
    symbol            VARCHAR(20)   NOT NULL REFERENCES instruments(symbol),
    period_type       VARCHAR(9)    NOT NULL CHECK (period_type IN ('QUARTERLY', 'ANNUAL')),
    period_end        DATE          NOT NULL,
    revenue           NUMERIC(22,2),
    gross_profit      NUMERIC(22,2),
    operating_income  NUMERIC(22,2),
    net_income        NUMERIC(22,2),
    eps_diluted       NUMERIC(14,4),
    PRIMARY KEY (symbol, period_type, period_end)
);
