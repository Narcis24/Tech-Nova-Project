-- Value and date sanity checks

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_cash_balance CHECK (cash_balance >= 0 AND cash_balance < 1000000000),
    ADD CONSTRAINT chk_accounts_last_updated CHECK (last_updated <= NOW());

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_quantity CHECK (quantity > 0 AND quantity < 1000000000),
    ADD CONSTRAINT chk_orders_price CHECK (price > 0 AND price < 1000000000),
    ADD CONSTRAINT chk_orders_created_on CHECK (created_on <= NOW());

ALTER TABLE positions
    ADD CONSTRAINT chk_positions_quantity CHECK (quantity >= 0 AND quantity < 1000000000),
    ADD CONSTRAINT chk_positions_average_cost CHECK (average_cost IS NULL OR (average_cost >= 0 AND average_cost < 1000000000));
