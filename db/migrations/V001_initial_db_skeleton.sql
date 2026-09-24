CREATE TABLE accounts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  VARCHAR(50)  NOT NULL UNIQUE,
    first_name  VARCHAR(255) NOT NULL,
    last_name   VARCHAR(255) NOT NULL,
    cash_balance NUMERIC     NOT NULL,
    status       VARCHAR(20) NOT NULL,
    VERSION      INT         NOT NULL DEFAULT 0,
    last_updated TIMESTAMP DEFAULT NOW()
 );

CREATE TABLE instruments (
    symbol VARCHAR(20) PRIMARY KEY,
    name VARCHAR(255),
    asset_class VARCHAR(50),
    currency VARCHAR(3),
    tradable BOOLEAN
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    account_id VARCHAR NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity INT NOT NULL,
    price NUMERIC(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE,
    created_on TIMESTAMP NOT NULL DEFAULT NOW()

);

CREATE TABLE positions (
    account_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    average_cost NUMERIC(18,2),
    PRIMARY KEY (account_id, symbol)
);

-- End of table creations
-- Start of relationships (foreign keys)

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_account_id FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    ADD CONSTRAINT fk_orders_symbol FOREIGN KEY (symbol) REFERENCES instruments(symbol);

ALTER TABLE positions
    ADD CONSTRAINT fk_positions_account_id FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    ADD CONSTRAINT fk_positions_symbol FOREIGN KEY (symbol) REFERENCES instruments(symbol);
