-- How long a LIMIT order stays open: DAY (until the next close), GTC (until cancelled, at most 90 days)
-- or GTD (until the close on a chosen date). MARKET orders, and limit orders placed before this, have neither.
ALTER TABLE orders
    ADD COLUMN time_in_force VARCHAR(3) CHECK (time_in_force IN ('DAY', 'GTC', 'GTD')),
    ADD COLUMN expires_at    TIMESTAMP;

-- The expiry job looks only at open orders that can expire
CREATE INDEX idx_orders_pending_expiry ON orders(expires_at) WHERE status = 'PENDING' AND expires_at IS NOT NULL;
