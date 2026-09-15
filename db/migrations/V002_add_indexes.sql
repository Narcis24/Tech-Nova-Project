-- Indexes for common lookups; account_id, symbol PK/UNIQUE columns already have implicit indexes

-- speeds up "orders for an account" lookups (FK column, not auto-indexed on this side)
CREATE INDEX idx_orders_account_id ON orders (account_id);

-- speeds up "orders for an instrument" lookups (FK column, not auto-indexed on this side)
CREATE INDEX idx_orders_symbol ON orders (symbol);

-- speeds up filtering/polling for orders by status (e.g. open/pending orders)
CREATE INDEX idx_orders_status ON orders (status);

-- speeds up recent-orders queries and date-range reporting
CREATE INDEX idx_orders_created_on ON orders (created_on);

-- speeds up "positions holding this instrument" lookups across accounts (symbol is trailing in the composite PK)
CREATE INDEX idx_positions_symbol ON positions (symbol);

-- speeds up filtering accounts by status (e.g. active/closed)
CREATE INDEX idx_accounts_status ON accounts (status);
