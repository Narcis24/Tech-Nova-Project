-- Actual end-of-session account values; never backfill with today's holdings or cash.
CREATE TABLE account_balance_history (
    account_id VARCHAR(255) NOT NULL REFERENCES accounts(account_id),
    session_date DATE NOT NULL,
    cash_balance NUMERIC(24,6) NOT NULL,
    portfolio_value NUMERIC(24,6) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, session_date)
);
