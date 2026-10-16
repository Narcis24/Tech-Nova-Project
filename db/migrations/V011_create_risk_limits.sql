-- Exposure limits checked before an order is placed. Breaches only warn; orders are never blocked.
-- risk_limits holds the defaults; account_risk_limits holds an account holder's own overrides.
CREATE TABLE risk_limits (
    rule          VARCHAR(30)  PRIMARY KEY,
    threshold_pct NUMERIC(5,2) NOT NULL CHECK (threshold_pct BETWEEN 0 AND 100),
    direction     VARCHAR(5)   NOT NULL CHECK (direction IN ('ABOVE', 'BELOW'))
);

INSERT INTO risk_limits (rule, threshold_pct, direction) VALUES
('SINGLE_INSTRUMENT', 20, 'ABOVE'),
('ASSET_CLASS',       60, 'ABOVE'),
('CASH_FLOOR',        20, 'BELOW');

-- threshold_pct NULL means "use the default"; enabled = false turns the rule off for this account.
CREATE TABLE account_risk_limits (
    account_id    VARCHAR(50)  NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
    rule          VARCHAR(30)  NOT NULL REFERENCES risk_limits(rule),
    threshold_pct NUMERIC(5,2) CHECK (threshold_pct BETWEEN 0 AND 100),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (account_id, rule)
);
