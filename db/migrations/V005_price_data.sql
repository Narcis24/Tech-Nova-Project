CREATE TABLE price_data (
    symbol     VARCHAR(20)   NOT NULL REFERENCES instruments(symbol),
    trade_date DATE          NOT NULL,
    open       NUMERIC(18,6) NOT NULL,
    high       NUMERIC(18,6) NOT NULL,
    low        NUMERIC(18,6) NOT NULL,
    close      NUMERIC(18,6) NOT NULL,
    adj_close  NUMERIC(18,6),
    volume     BIGINT,
    PRIMARY KEY (symbol, trade_date)
);
