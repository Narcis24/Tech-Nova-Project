-- Module 09 Mock Data for Tech-Nova Trading Platform
-- Populates accounts, instruments, orders, and positions with realistic test data

-- ============================================================================
-- 1. INSERT INSTRUMENTS (Financial products available for trading)
-- ============================================================================

INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES
('AAPL', 'Apple Inc.', 'EQUITY', 'USD', true),
('GOOGL', 'Alphabet Inc.', 'EQUITY', 'USD', true),
('MSFT', 'Microsoft Corporation', 'EQUITY', 'USD', true),
('TSLA', 'Tesla Inc.', 'EQUITY', 'USD', true),
('GOLD', 'SPDR Gold Shares', 'COMMODITY', 'USD', true),
('BTC', 'Bitcoin', 'CRYPTO', 'USD', true),
('VANG', 'Vanguard S&P 500 ETF', 'ETF', 'USD', true),
('BOND10', 'US 10-Year Treasury', 'BOND', 'USD', true);

-- ============================================================================
-- 2. INSERT ACCOUNTS (Client trading accounts)
-- ============================================================================

INSERT INTO accounts (account_id, holder_name, cash_balance, status, version, last_updated) VALUES
('ACC001', 'Alice Johnson', 50000.00, 'ACTIVE', 0, NOW()),
('ACC002', 'Brian Osei', 75000.00, 'ACTIVE', 0, NOW()),
('ACC003', 'Carla Mendes', 100000.00, 'ACTIVE', 0, NOW()),
('ACC004', 'David Kim', 30000.00, 'ACTIVE', 0, NOW()),
('ACC005', 'Elena Petrova', 200000.00, 'ACTIVE', 0, NOW());

-- ============================================================================
-- 3. INSERT ORDERS (Trading transactions - ~3 per account)
-- ============================================================================

-- Alice's orders
INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
(gen_random_uuid(), 'ACC001', 'AAPL', 'BUY', 10, 150.50, 'FILLED', 'ACC001_001', '2024-01-10 09:30:00'),
(gen_random_uuid(), 'ACC001', 'GOOGL', 'BUY', 5, 140.25, 'FILLED', 'ACC001_002', '2024-01-15 10:15:00'),
(gen_random_uuid(), 'ACC001', 'BOND10', 'BUY', 100, 98.50, 'PENDING', 'ACC001_003', NOW());

-- Brian's orders
INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
(gen_random_uuid(), 'ACC002', 'TSLA', 'BUY', 20, 250.00, 'FILLED', 'ACC002_001', '2024-01-05 14:20:00'),
(gen_random_uuid(), 'ACC002', 'AAPL', 'SELL', 5, 155.00, 'FILLED', 'ACC002_002', '2024-01-18 11:45:00'),
(gen_random_uuid(), 'ACC002', 'GOLD', 'BUY', 50, 185.75, 'CANCELLED', 'ACC002_003', '2024-01-19 09:00:00');

-- Carla's orders
INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
(gen_random_uuid(), 'ACC003', 'MSFT', 'BUY', 15, 380.00, 'FILLED', 'ACC003_001', '2024-01-08 13:30:00'),
(gen_random_uuid(), 'ACC003', 'VANG', 'BUY', 30, 210.50, 'FILLED', 'ACC003_002', '2024-01-12 10:00:00'),
(gen_random_uuid(), 'ACC003', 'BTC', 'BUY', 1, 45000.00, 'PENDING', 'ACC003_003', NOW());

-- David's orders
INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
(gen_random_uuid(), 'ACC004', 'AAPL', 'BUY', 8, 148.00, 'FILLED', 'ACC004_001', '2024-01-07 09:15:00'),
(gen_random_uuid(), 'ACC004', 'GOOGL', 'BUY', 3, 138.00, 'FILLED', 'ACC004_002', '2024-01-17 14:45:00');

-- Elena's orders
INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) VALUES
(gen_random_uuid(), 'ACC005', 'TSLA', 'BUY', 50, 245.00, 'FILLED', 'ACC005_001', '2024-01-02 10:30:00'),
(gen_random_uuid(), 'ACC005', 'MSFT', 'BUY', 25, 375.50, 'FILLED', 'ACC005_002', '2024-01-14 11:20:00'),
(gen_random_uuid(), 'ACC005', 'VANG', 'BUY', 40, 208.00, 'FILLED', 'ACC005_003', '2024-01-20 15:00:00');

-- ============================================================================
-- 4. INSERT POSITIONS (Current holdings - calculated from filled orders)
-- ============================================================================

INSERT INTO positions (account_id, symbol, quantity, average_cost) VALUES
-- Alice's positions
('ACC001', 'AAPL', 10, 150.50),
('ACC001', 'GOOGL', 5, 140.25),

-- Brian's positions
('ACC002', 'TSLA', 20, 250.00),
('ACC002', 'AAPL', -5, 155.00),  -- Short position

-- Carla's positions
('ACC003', 'MSFT', 15, 380.00),
('ACC003', 'VANG', 30, 210.50),

-- David's positions
('ACC004', 'AAPL', 8, 148.00),
('ACC004', 'GOOGL', 3, 138.00),

-- Elena's positions
('ACC005', 'TSLA', 50, 245.00),
('ACC005', 'MSFT', 25, 375.50),
('ACC005', 'VANG', 40, 208.00);
