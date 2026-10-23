-- Three example public templates for Discover.

-- A real user row, so nobody can register as "demo". The hash is not valid bcrypt, so it can never log in.
INSERT INTO users (username, password_hash) VALUES ('demo', '!');

CREATE TEMP TABLE demo_pies (
    pie_id      UUID PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    description VARCHAR(280) NOT NULL
);
INSERT INTO demo_pies (pie_id, name, description) VALUES
('d0000000-0000-4000-8000-000000000001', '[Demo] Mega-cap Tech',
 'Example mix of large technology companies with 5% cash.'),
('d0000000-0000-4000-8000-000000000002', '[Demo] Dividend Core',
 'Example mix of dividend stocks with 10% cash.'),
('d0000000-0000-4000-8000-000000000003', '[Demo] Core and Bonds',
 'Example mix of stock funds, bonds and gold with 5% cash.');

INSERT INTO pies (pie_id, owner_username, name, description, visibility, latest_version, published_at, created_at)
SELECT pie_id, 'demo', name, description, 'PUBLIC', 1,
       NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC'
FROM demo_pies;

INSERT INTO pie_versions (pie_id, version, created_at)
SELECT pie_id, 1, NOW() AT TIME ZONE 'UTC' FROM demo_pies;

INSERT INTO pie_slices (pie_id, version, symbol, weight_pct) VALUES
('d0000000-0000-4000-8000-000000000001', 1, 'AAPL', 25),
('d0000000-0000-4000-8000-000000000001', 1, 'MSFT', 25),
('d0000000-0000-4000-8000-000000000001', 1, 'NVDA', 20),
('d0000000-0000-4000-8000-000000000001', 1, 'GOOGL', 15),
('d0000000-0000-4000-8000-000000000001', 1, 'AMZN', 10),
('d0000000-0000-4000-8000-000000000002', 1, 'JNJ', 20),
('d0000000-0000-4000-8000-000000000002', 1, 'PG', 20),
('d0000000-0000-4000-8000-000000000002', 1, 'KO', 20),
('d0000000-0000-4000-8000-000000000002', 1, 'XOM', 15),
('d0000000-0000-4000-8000-000000000002', 1, 'JPM', 15),
('d0000000-0000-4000-8000-000000000003', 1, 'SPY', 40),
('d0000000-0000-4000-8000-000000000003', 1, 'QQQ', 20),
('d0000000-0000-4000-8000-000000000003', 1, 'TLT', 15),
('d0000000-0000-4000-8000-000000000003', 1, 'GLD', 10),
('d0000000-0000-4000-8000-000000000003', 1, 'AGG', 10);

DROP TABLE demo_pies;
