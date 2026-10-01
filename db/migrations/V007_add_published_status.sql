-- Add PUBLISHED status to order status check constraint
-- Since we cannot directly modify a constraint, we drop and recreate it

ALTER TABLE orders
    DROP CONSTRAINT chk_orders_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FILLED', 'CANCELLED', 'REJECTED', 'PARTIALLY_FILLED', 'EXPIRED'));
