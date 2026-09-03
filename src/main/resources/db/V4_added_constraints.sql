-- Constraints on cash balances, last updated dates, order quantities, prices, and position holdings
ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_cash_balance CHECK (cash_balance >= 0),
    ADD CONSTRAINT chk_accounts_last_updated CHECK (last_updated <= NOW()),
    ADD CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'));

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_quantity CHECK (quantity > 0),
    ADD CONSTRAINT chk_orders_price CHECK (price > 0),
    ADD CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'FILLED', 'CANCELLED')),
    ADD CONSTRAINT chk_orders_created_on CHECK (created_on <= NOW());

ALTER TABLE positions
    ADD CONSTRAINT chk_positions_quantity CHECK (quantity >= 0),
    ADD CONSTRAINT chk_positions_average_cost CHECK (average_cost >= 0);

ALTER TABLE instruments
    ADD CONSTRAINT chk_asset_class CHECK (asset_class IN ('EQUITY', 'BOND', 'ETF'));
