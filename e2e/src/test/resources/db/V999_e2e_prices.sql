-- Reference prices for MARKET orders. In a real deployment the data pipeline loads these from
-- Yahoo; the tests seed a few fixed rows instead. Fill prices come from the stub quotes, not here.
INSERT INTO price_data (symbol, trade_date, open, high, low, close, adj_close, volume) VALUES
('AAPL', CURRENT_DATE, 195.00, 196.00, 194.00, 195.00, 195.00, 1000000),
('MSFT', CURRENT_DATE, 390.00, 391.00, 389.00, 390.00, 390.00, 1000000),
('SLV',  CURRENT_DATE,  30.00,  30.50,  29.50,  30.00,  30.00, 1000000);
