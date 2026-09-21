SELECT count(DISTINCT symbol) AS symbols, count(*) AS bars, min(trade_date), max(trade_date) FROM price_data;

-- instruments with no prices (expect 0)
SELECT count(*) FROM instruments i
WHERE NOT EXISTS (SELECT 1 FROM price_data p WHERE p.symbol = i.symbol);

-- orders not at the real close on their date (expect 0)
SELECT count(*) FROM orders o
LEFT JOIN price_data p ON p.symbol = o.symbol AND p.trade_date = o.created_on::date
WHERE p.symbol IS NULL OR o.price <> round(p.close, 2);

-- positions not matching filled orders (expect 0)
SELECT count(*) FROM (
    SELECT account_id, symbol, sum(quantity) AS qty FROM orders WHERE status = 'FILLED' GROUP BY 1, 2
) o FULL JOIN positions p USING (account_id, symbol)
WHERE o.qty IS DISTINCT FROM p.quantity;
