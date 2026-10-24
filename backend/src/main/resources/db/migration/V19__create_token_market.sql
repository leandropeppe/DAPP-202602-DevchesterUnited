CREATE TABLE market_state (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    epoch BIGINT NOT NULL DEFAULT 1,
    initial_reserve_configured NUMERIC(20, 2),
    initial_reserve_initialized NUMERIC(20, 2),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_market_state_singleton CHECK (id = 1),
    CONSTRAINT ck_market_state_status CHECK (status IN ('UNINITIALIZED', 'INITIALIZING', 'READY', 'FAILED')),
    CONSTRAINT ck_market_state_epoch CHECK (epoch > 0),
    CONSTRAINT ck_market_state_configured_reserve CHECK (
        initial_reserve_configured IS NULL OR initial_reserve_configured >= 0
    ),
    CONSTRAINT ck_market_state_initialized_reserve CHECK (
        initial_reserve_initialized IS NULL OR initial_reserve_initialized >= 0
    ),
    CONSTRAINT ck_market_state_dates CHECK (updated_at >= created_at)
);

CREATE TABLE market_counterparties (
    id UUID PRIMARY KEY,
    counterparty_type VARCHAR(20) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_market_counterparty_type CHECK (counterparty_type = 'SUPERUSER')
);

CREATE UNIQUE INDEX ux_market_counterparties_superuser
    ON market_counterparties (counterparty_type)
    WHERE counterparty_type = 'SUPERUSER';

CREATE TABLE market_credit_accounts (
    id UUID PRIMARY KEY,
    user_id UUID,
    counterparty_id UUID,
    balance NUMERIC(20, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_credit_account_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_credit_account_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES market_counterparties(id),
    CONSTRAINT ck_market_credit_account_owner CHECK (
        (user_id IS NOT NULL AND counterparty_id IS NULL)
        OR (user_id IS NULL AND counterparty_id IS NOT NULL)
    ),
    CONSTRAINT ck_market_credit_account_balance CHECK (balance >= 0),
    CONSTRAINT ck_market_credit_account_dates CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX ux_market_credit_account_user
    ON market_credit_accounts (user_id)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX ux_market_credit_account_counterparty
    ON market_credit_accounts (counterparty_id)
    WHERE counterparty_id IS NOT NULL;

CREATE TABLE market_initial_credit_grants (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    credit_account_id UUID NOT NULL,
    grant_type VARCHAR(30) NOT NULL,
    amount NUMERIC(20, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_initial_grant_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_initial_grant_account
        FOREIGN KEY (credit_account_id) REFERENCES market_credit_accounts(id),
    CONSTRAINT uq_market_initial_grant_user_type UNIQUE (user_id, grant_type),
    CONSTRAINT ck_market_initial_grant_type CHECK (grant_type = 'INITIAL_GRANT'),
    CONSTRAINT ck_market_initial_grant_amount CHECK (amount = 1000.00)
);

CREATE TABLE market_token_issuances (
    player_id UUID PRIMARY KEY,
    counterparty_id UUID NOT NULL,
    issued_quantity BIGINT NOT NULL,
    inventory_quantity BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_token_issuance_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT fk_market_token_issuance_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES market_counterparties(id),
    CONSTRAINT ck_market_token_issuance_total CHECK (issued_quantity = 100),
    CONSTRAINT ck_market_token_issuance_inventory CHECK (
        inventory_quantity >= 0 AND inventory_quantity <= issued_quantity
    ),
    CONSTRAINT ck_market_token_issuance_dates CHECK (updated_at >= created_at)
);

CREATE TABLE market_quote_versions (
    quote_id UUID NOT NULL,
    version BIGINT NOT NULL,
    player_id UUID NOT NULL,
    price_per_token NUMERIC(20, 2) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    validity_status VARCHAR(20) NOT NULL,
    strategy_reference VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (quote_id, version),
    CONSTRAINT fk_market_quote_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT uq_market_quote_player_version UNIQUE (quote_id, version, player_id),
    CONSTRAINT ck_market_quote_version CHECK (version > 0),
    CONSTRAINT ck_market_quote_price CHECK (price_per_token > 0),
    CONSTRAINT ck_market_quote_dates CHECK (
        published_at <= valid_from
        AND (valid_until IS NULL OR valid_until >= valid_from)
    ),
    CONSTRAINT ck_market_quote_status CHECK (
        validity_status IN ('VALID', 'REPLACED', 'INVALIDATED', 'EXPIRED')
    )
);

CREATE INDEX ix_market_quote_versions_player
    ON market_quote_versions (player_id, published_at DESC);

CREATE TABLE market_quote_headers (
    player_id UUID PRIMARY KEY,
    quote_id UUID NOT NULL,
    current_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_quote_header_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT fk_market_quote_header_version
        FOREIGN KEY (quote_id, current_version, player_id)
        REFERENCES market_quote_versions (quote_id, version, player_id)
);

CREATE TABLE market_holdings (
    user_id UUID NOT NULL,
    player_id UUID NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 0,
    acquisition_cost NUMERIC(28, 8) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, player_id),
    CONSTRAINT fk_market_holding_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_holding_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT ck_market_holding_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_market_holding_cost CHECK (acquisition_cost >= 0)
);

CREATE TABLE market_realized_pnl (
    user_id UUID PRIMARY KEY,
    realized_pnl NUMERIC(28, 8) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_realized_pnl_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id)
);

CREATE TABLE market_idempotency_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    parameters_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    execution_token UUID,
    result_reference UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_idempotency_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id),
    CONSTRAINT uq_market_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_market_idempotency_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_market_idempotency_key CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_market_idempotency_operation CHECK (operation_type IN ('PURCHASE', 'SALE')),
    CONSTRAINT ck_market_idempotency_status CHECK (
        status IN ('PROCESSING', 'CONFIRMED', 'REJECTED', 'UNCERTAIN')
    ),
    CONSTRAINT ck_market_idempotency_dates CHECK (updated_at >= created_at)
);

CREATE INDEX ix_market_idempotency_status
    ON market_idempotency_requests (user_id, status, updated_at);

CREATE TABLE market_confirmed_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    player_id UUID NOT NULL,
    player_name_snapshot VARCHAR(200) NOT NULL,
    operation_type VARCHAR(10) NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price NUMERIC(20, 2) NOT NULL,
    total_amount NUMERIC(20, 2) NOT NULL,
    counterparty_id UUID NOT NULL,
    quote_id UUID NOT NULL,
    quote_version BIGINT NOT NULL,
    idempotency_request_id UUID NOT NULL,
    realized_pnl NUMERIC(28, 8),
    confirmed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_transaction_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_transaction_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT fk_market_transaction_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES market_counterparties(id),
    CONSTRAINT fk_market_transaction_quote
        FOREIGN KEY (quote_id, quote_version, player_id)
        REFERENCES market_quote_versions (quote_id, version, player_id),
    CONSTRAINT fk_market_transaction_idempotency
        FOREIGN KEY (idempotency_request_id, user_id)
        REFERENCES market_idempotency_requests (id, user_id),
    CONSTRAINT uq_market_transaction_idempotency UNIQUE (idempotency_request_id),
    CONSTRAINT ck_market_transaction_operation CHECK (operation_type IN ('PURCHASE', 'SALE')),
    CONSTRAINT ck_market_transaction_quantity CHECK (quantity > 0),
    CONSTRAINT ck_market_transaction_prices CHECK (unit_price > 0 AND total_amount > 0),
    CONSTRAINT ck_market_transaction_realized_pnl CHECK (
        operation_type = 'PURCHASE' AND realized_pnl IS NULL
        OR operation_type = 'SALE'
    )
);

CREATE INDEX ix_market_transactions_user_date
    ON market_confirmed_transactions (user_id, confirmed_at DESC);

CREATE INDEX ix_market_transactions_user_filters
    ON market_confirmed_transactions (user_id, player_id, operation_type, confirmed_at DESC);

CREATE TABLE market_movements (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    asset_type VARCHAR(10) NOT NULL,
    credit_account_id UUID,
    token_user_id UUID,
    token_counterparty_id UUID,
    player_id UUID,
    quantity_delta NUMERIC(28, 8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_movement_transaction
        FOREIGN KEY (transaction_id) REFERENCES market_confirmed_transactions(id),
    CONSTRAINT fk_market_movement_credit_account
        FOREIGN KEY (credit_account_id) REFERENCES market_credit_accounts(id),
    CONSTRAINT fk_market_movement_token_user
        FOREIGN KEY (token_user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_movement_token_counterparty
        FOREIGN KEY (token_counterparty_id) REFERENCES market_counterparties(id),
    CONSTRAINT fk_market_movement_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT ck_market_movement_asset CHECK (
        (asset_type = 'CREDIT'
            AND credit_account_id IS NOT NULL
            AND token_user_id IS NULL
            AND token_counterparty_id IS NULL
            AND player_id IS NULL)
        OR
        (asset_type = 'TOKEN'
            AND credit_account_id IS NULL
            AND player_id IS NOT NULL
            AND ((token_user_id IS NOT NULL AND token_counterparty_id IS NULL)
                OR (token_user_id IS NULL AND token_counterparty_id IS NOT NULL)))
    ),
    CONSTRAINT ck_market_movement_delta CHECK (quantity_delta <> 0)
);

CREATE INDEX ix_market_movements_transaction
    ON market_movements (transaction_id);

CREATE TABLE market_rejected_attempts (
    id UUID PRIMARY KEY,
    user_id UUID,
    operation_type VARCHAR(20) NOT NULL,
    player_id UUID,
    idempotency_key_hash VARCHAR(128),
    reason_code VARCHAR(50) NOT NULL,
    parameters_hash VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_rejected_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_rejected_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT ck_market_rejected_operation CHECK (operation_type IN ('PURCHASE', 'SALE'))
);

CREATE INDEX ix_market_rejected_user_date
    ON market_rejected_attempts (user_id, created_at DESC);

CREATE OR REPLACE FUNCTION market_reject_immutable_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Los registros confirmados de Token Market son inmutables';
END;
$$;

CREATE TRIGGER trg_market_quote_versions_immutable
BEFORE UPDATE OR DELETE ON market_quote_versions
FOR EACH ROW EXECUTE FUNCTION market_reject_immutable_change();

CREATE TRIGGER trg_market_confirmed_transactions_immutable
BEFORE UPDATE OR DELETE ON market_confirmed_transactions
FOR EACH ROW EXECUTE FUNCTION market_reject_immutable_change();

CREATE TRIGGER trg_market_movements_immutable
BEFORE UPDATE OR DELETE ON market_movements
FOR EACH ROW EXECUTE FUNCTION market_reject_immutable_change();
