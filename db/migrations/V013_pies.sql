-- Pies: target mixes an account can be rebalanced to. Weights live in immutable versions.
CREATE TABLE pies (
    pie_id          UUID          PRIMARY KEY,
    owner_username  VARCHAR(50)   NOT NULL,
    name            VARCHAR(60)   NOT NULL,
    description     VARCHAR(280),
    visibility      VARCHAR(10)   NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    latest_version  INT           NOT NULL,
    published_at    TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL,
    deleted_at      TIMESTAMP
);
CREATE INDEX idx_pies_owner ON pies(owner_username);

CREATE TABLE pie_versions (
    pie_id      UUID      NOT NULL REFERENCES pies(pie_id),
    version     INT       NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (pie_id, version)
);

-- Weights of one version; the sum (at most 100) is checked by the app, the rest is the cash target
CREATE TABLE pie_slices (
    pie_id      UUID         NOT NULL,
    version     INT          NOT NULL,
    symbol      VARCHAR(20)  NOT NULL REFERENCES instruments(symbol),
    weight_pct  NUMERIC(5,2) NOT NULL CHECK (weight_pct > 0 AND weight_pct <= 100),
    PRIMARY KEY (pie_id, version, symbol),
    FOREIGN KEY (pie_id, version) REFERENCES pie_versions(pie_id, version)
);

-- An account follows at most one pie, covering the whole account
CREATE TABLE account_pies (
    account_id       VARCHAR(50) PRIMARY KEY REFERENCES accounts(account_id) ON DELETE CASCADE,
    pie_id           UUID        NOT NULL REFERENCES pies(pie_id),
    mode             VARCHAR(5)  NOT NULL CHECK (mode IN ('OWN', 'COPY')),
    applied_version  INT,
    auto_sync        BOOLEAN     NOT NULL DEFAULT FALSE,
    linked_at        TIMESTAMP   NOT NULL
);
CREATE INDEX idx_account_pies_pie ON account_pies(pie_id);

-- Used from #221: one execution of "bring this account to version N"
CREATE TABLE rebalance_runs (
    run_id           UUID        PRIMARY KEY,
    account_id       VARCHAR(50) NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
    pie_id           UUID        NOT NULL REFERENCES pies(pie_id),
    target_version   INT         NOT NULL,
    status           VARCHAR(12) NOT NULL CHECK (status IN ('SELLING', 'BUYING', 'COMPLETED', 'PARTIAL', 'CANCELLED')),
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    failure_summary  VARCHAR(1000)
);
CREATE UNIQUE INDEX uq_rebalance_runs_active ON rebalance_runs(account_id) WHERE status IN ('SELLING', 'BUYING');

CREATE TABLE rebalance_legs (
    run_id    UUID        NOT NULL REFERENCES rebalance_runs(run_id) ON DELETE CASCADE,
    order_id  UUID        NOT NULL UNIQUE REFERENCES orders(id),
    symbol    VARCHAR(20) NOT NULL,
    side      VARCHAR(4)  NOT NULL CHECK (side IN ('SELL', 'BUY')),
    quantity  INT         NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (run_id, order_id)
);

-- Used from #222: daily strategy index of a public pie, 100 on its publish date
CREATE TABLE pie_index_points (
    pie_id  UUID          NOT NULL REFERENCES pies(pie_id),
    day     DATE          NOT NULL,
    value   NUMERIC(18,6) NOT NULL,
    PRIMARY KEY (pie_id, day)
);
