-- Constraints on cash balances, order quantities, prices, and position holdings
ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_cash_balance CHECK (cash_balance >= 0),
    ADD CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'));

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_type CHECK (order_type IN ('MARKET', 'LIMIT')),
    ADD CONSTRAINT chk_orders_quantity CHECK (quantity > 0),
    ADD CONSTRAINT chk_orders_price CHECK (price > 0),
    ADD CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'FILLED', 'CANCELLED', 'REJECTED', 'PARTIALLY_FILLED', 'EXPIRED'));

ALTER TABLE positions
    ADD CONSTRAINT chk_positions_quantity CHECK (quantity >= 0),
    ADD CONSTRAINT chk_positions_average_cost CHECK (average_cost >= 0);

ALTER TABLE instruments
    ADD CONSTRAINT chk_asset_class CHECK (asset_class IN ('EQUITY', 'BOND', 'ETF', 'COMMODITY'));
