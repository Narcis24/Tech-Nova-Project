-- Optimistic-lock column for orders, so a stale save cannot overwrite a newer status
-- (e.g. a cancel landing after settlement already marked the order FILLED).
ALTER TABLE orders
    ADD COLUMN version INT NOT NULL DEFAULT 0;
