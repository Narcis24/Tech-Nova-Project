-- Instruments, prices, orders and positions are loaded by data-pipeline/load.py
INSERT INTO accounts (account_id, holder_name, cash_balance, status, version, last_updated) VALUES
('ACC001', 'Alice Johnson', 50000.00, 'ACTIVE', 0, NOW()),
('ACC002', 'Brian Osei', 75000.00, 'ACTIVE', 0, NOW()),
('ACC003', 'Carla Mendes', 100000.00, 'ACTIVE', 0, NOW()),
('ACC004', 'David Kim', 30000.00, 'ACTIVE', 0, NOW()),
('ACC005', 'Elena Petrova', 200000.00, 'ACTIVE', 0, NOW());
