-- Three public demo pies so the leaderboard is not empty. They belong to the reserved author "demo" (the app
-- shows that author's pies as demo pies) and say so in their names and descriptions. Their index history is
-- simulated: 120 daily points ending yesterday, from a fixed formula, so every install gets the same shape.
-- The nightly job carries on from there with real prices.

-- A real user row, so nobody can register as "demo". The hash is not valid bcrypt, so it can never log in.
INSERT INTO users (username, password_hash) VALUES ('demo', '!');

CREATE TEMP TABLE demo_pies (
    pie_id      UUID PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    description VARCHAR(280) NOT NULL,
    drift       NUMERIC NOT NULL,
    amp         NUMERIC NOT NULL,
    period      NUMERIC NOT NULL
);
INSERT INTO demo_pies (pie_id, name, description, drift, amp, period) VALUES
('d0000000-0000-4000-8000-000000000001', '[Demo] Mega-cap Tech',
 'Demo pie with a simulated index history, for illustration only. It is not a real track record.', 0.0030, 0.040, 6),
('d0000000-0000-4000-8000-000000000002', '[Demo] Dividend Core',
 'Demo pie with a simulated index history, for illustration only. It is not a real track record.', 0.0009, 0.015, 9),
('d0000000-0000-4000-8000-000000000003', '[Demo] Core and Bonds',
 'Demo pie with a simulated index history, for illustration only. It is not a real track record.', 0.0016, 0.025, 14);

-- Published 120 days ago, so the demo pies are old enough for every window
INSERT INTO pies (pie_id, owner_username, name, description, visibility, latest_version, published_at, created_at)
SELECT pie_id, 'demo', name, description, 'PUBLIC', 1,
       (NOW() AT TIME ZONE 'UTC')::date - 120, (NOW() AT TIME ZONE 'UTC')::date - 121
FROM demo_pies;

INSERT INTO pie_versions (pie_id, version, created_at)
SELECT pie_id, 1, (NOW() AT TIME ZONE 'UTC')::date - 121 FROM demo_pies;

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

-- 100 on the publish day, then a drift with a wave on top
INSERT INTO pie_index_points (pie_id, day, value)
SELECT d.pie_id, (NOW() AT TIME ZONE 'UTC')::date - 120 + g,
       round((100 * (1 + d.drift * g + d.amp * sin(g / d.period))::numeric), 6)
FROM demo_pies d CROSS JOIN generate_series(0, 119) AS g;

DROP TABLE demo_pies;
