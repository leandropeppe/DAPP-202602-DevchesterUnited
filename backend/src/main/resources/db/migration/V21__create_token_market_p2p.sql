-- Token Market P2P foundation.  This migration is additive and preserves
-- primary-market holdings, accounts and confirmed legacy transactions.

ALTER TABLE market_holdings
    ADD COLUMN reserved_quantity BIGINT NOT NULL DEFAULT 0;

ALTER TABLE market_holdings
    ADD CONSTRAINT ck_market_holding_reserved_quantity
        CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity);

-- Keep the existing primary-market operation types and add namespaced P2P
-- operations for idempotency claims performed by the P2P services.
ALTER TABLE market_idempotency_requests
    DROP CONSTRAINT ck_market_idempotency_operation;

ALTER TABLE market_idempotency_requests
    ADD CONSTRAINT ck_market_idempotency_operation CHECK (
        operation_type IN (
            'PURCHASE',
            'SALE',
            'P2P_OFFER',
            'P2P_PURCHASE',
            'P2P_CANCEL'
        )
    );

CREATE TABLE market_p2p_offers (
    id UUID PRIMARY KEY,
    seller_user_id UUID NOT NULL,
    player_id UUID NOT NULL,
    original_quantity BIGINT NOT NULL,
    remaining_quantity BIGINT NOT NULL,
    sold_quantity BIGINT NOT NULL DEFAULT 0,
    released_quantity BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    idempotency_request_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_p2p_offer_seller
        FOREIGN KEY (seller_user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_p2p_offer_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT fk_market_p2p_offer_holding
        FOREIGN KEY (seller_user_id, player_id)
        REFERENCES market_holdings (user_id, player_id),
    CONSTRAINT fk_market_p2p_offer_idempotency
        FOREIGN KEY (idempotency_request_id, seller_user_id)
        REFERENCES market_idempotency_requests (id, user_id),
    CONSTRAINT uq_market_p2p_offer_seller_player
        UNIQUE (id, seller_user_id, player_id),
    CONSTRAINT uq_market_p2p_offer_seller
        UNIQUE (id, seller_user_id),
    CONSTRAINT uq_market_p2p_offer_idempotency
        UNIQUE (idempotency_request_id),
    CONSTRAINT ck_market_p2p_offer_status
        CHECK (status IN ('OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED')),
    CONSTRAINT ck_market_p2p_offer_quantities
        CHECK (
            original_quantity > 0
            AND remaining_quantity >= 0
            AND sold_quantity >= 0
            AND released_quantity >= 0
            AND original_quantity = sold_quantity + remaining_quantity + released_quantity
        ),
    CONSTRAINT ck_market_p2p_offer_state_quantities
        CHECK (
            (status = 'OPEN'
                AND sold_quantity = 0
                AND remaining_quantity = original_quantity
                AND released_quantity = 0)
            OR
            (status = 'PARTIALLY_FILLED'
                AND sold_quantity > 0
                AND remaining_quantity > 0
                AND remaining_quantity < original_quantity
                AND released_quantity = 0)
            OR
            (status = 'FILLED'
                AND sold_quantity = original_quantity
                AND remaining_quantity = 0
                AND released_quantity = 0)
            OR
            (status = 'CANCELLED'
                AND sold_quantity < original_quantity
                AND remaining_quantity = 0
                AND released_quantity > 0)
        ),
    CONSTRAINT ck_market_p2p_offer_dates
        CHECK (updated_at >= created_at)
);

CREATE INDEX ix_market_p2p_offers_player_status_created
    ON market_p2p_offers (player_id, status, created_at DESC, id DESC);

CREATE INDEX ix_market_p2p_offers_seller_player_status
    ON market_p2p_offers (seller_user_id, player_id, status, created_at DESC);

CREATE TABLE market_p2p_trades (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    seller_user_id UUID NOT NULL,
    buyer_user_id UUID NOT NULL,
    player_id UUID NOT NULL,
    player_name_snapshot VARCHAR(200) NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price NUMERIC(20, 2) NOT NULL,
    total_amount NUMERIC(20, 2) NOT NULL,
    quote_id UUID NOT NULL,
    quote_version BIGINT NOT NULL,
    idempotency_request_id UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_p2p_trade_offer
        FOREIGN KEY (offer_id, seller_user_id, player_id)
        REFERENCES market_p2p_offers (id, seller_user_id, player_id),
    CONSTRAINT fk_market_p2p_trade_seller
        FOREIGN KEY (seller_user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_p2p_trade_buyer
        FOREIGN KEY (buyer_user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_p2p_trade_seller_holding
        FOREIGN KEY (seller_user_id, player_id)
        REFERENCES market_holdings (user_id, player_id),
    CONSTRAINT fk_market_p2p_trade_buyer_holding
        FOREIGN KEY (buyer_user_id, player_id)
        REFERENCES market_holdings (user_id, player_id),
    CONSTRAINT fk_market_p2p_trade_quote
        FOREIGN KEY (quote_id, quote_version, player_id)
        REFERENCES market_quote_versions (quote_id, version, player_id),
    CONSTRAINT fk_market_p2p_trade_idempotency
        FOREIGN KEY (idempotency_request_id, buyer_user_id)
        REFERENCES market_idempotency_requests (id, user_id),
    CONSTRAINT uq_market_p2p_trade_idempotency
        UNIQUE (idempotency_request_id),
    CONSTRAINT ck_market_p2p_trade_distinct_users
        CHECK (seller_user_id <> buyer_user_id),
    CONSTRAINT ck_market_p2p_trade_quantity
        CHECK (quantity > 0),
    CONSTRAINT ck_market_p2p_trade_prices
        CHECK (unit_price > 0 AND total_amount > 0),
    CONSTRAINT ck_market_p2p_trade_amount
        CHECK (total_amount = unit_price * quantity),
    CONSTRAINT ck_market_p2p_trade_quote_version
        CHECK (quote_version > 0)
);

CREATE INDEX ix_market_p2p_trades_offer_date
    ON market_p2p_trades (offer_id, confirmed_at DESC);

CREATE INDEX ix_market_p2p_trades_seller_date
    ON market_p2p_trades (seller_user_id, confirmed_at DESC);

CREATE INDEX ix_market_p2p_trades_buyer_date
    ON market_p2p_trades (buyer_user_id, confirmed_at DESC);

CREATE TABLE market_p2p_trade_entries (
    id UUID PRIMARY KEY,
    trade_id UUID NOT NULL,
    user_id UUID NOT NULL,
    entry_role VARCHAR(20) NOT NULL,
    quantity BIGINT NOT NULL,
    amount NUMERIC(20, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_p2p_entry_trade
        FOREIGN KEY (trade_id) REFERENCES market_p2p_trades(id),
    CONSTRAINT fk_market_p2p_entry_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id),
    CONSTRAINT uq_market_p2p_entry_trade_role
        UNIQUE (trade_id, entry_role),
    CONSTRAINT uq_market_p2p_entry_trade_user_role
        UNIQUE (trade_id, user_id, entry_role),
    CONSTRAINT ck_market_p2p_entry_role
        CHECK (entry_role IN ('P2P_PURCHASE', 'P2P_SALE')),
    CONSTRAINT ck_market_p2p_entry_quantity
        CHECK (quantity > 0),
    CONSTRAINT ck_market_p2p_entry_amount
        CHECK (amount > 0)
);

CREATE INDEX ix_market_p2p_entries_user_date
    ON market_p2p_trade_entries (user_id, created_at DESC);

CREATE TABLE market_p2p_offer_cancellations (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    original_quantity BIGINT NOT NULL,
    sold_quantity BIGINT NOT NULL,
    released_quantity BIGINT NOT NULL,
    idempotency_request_id UUID NOT NULL,
    cancelled_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_market_p2p_cancellation_offer
        FOREIGN KEY (offer_id, actor_user_id)
        REFERENCES market_p2p_offers (id, seller_user_id),
    CONSTRAINT fk_market_p2p_cancellation_actor
        FOREIGN KEY (actor_user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_market_p2p_cancellation_idempotency
        FOREIGN KEY (idempotency_request_id, actor_user_id)
        REFERENCES market_idempotency_requests (id, user_id),
    CONSTRAINT uq_market_p2p_cancellation_offer
        UNIQUE (offer_id),
    CONSTRAINT uq_market_p2p_cancellation_idempotency
        UNIQUE (idempotency_request_id),
    CONSTRAINT ck_market_p2p_cancellation_quantities
        CHECK (
            original_quantity > 0
            AND sold_quantity >= 0
            AND released_quantity > 0
            AND original_quantity = sold_quantity + released_quantity
        )
);

CREATE INDEX ix_market_p2p_cancellations_actor_date
    ON market_p2p_offer_cancellations (actor_user_id, cancelled_at DESC);

-- Confirmed P2P records and cancellation audit are append-only.  The
-- existing V19 trigger function provides the same immutable-record policy.
CREATE TRIGGER trg_market_p2p_trades_immutable
BEFORE UPDATE OR DELETE ON market_p2p_trades
FOR EACH ROW EXECUTE FUNCTION market_reject_immutable_change();

CREATE TRIGGER trg_market_p2p_trade_entries_immutable
BEFORE UPDATE OR DELETE ON market_p2p_trade_entries
FOR EACH ROW EXECUTE FUNCTION market_reject_immutable_change();

CREATE TRIGGER trg_market_p2p_cancellations_immutable
BEFORE UPDATE OR DELETE ON market_p2p_offer_cancellations
FOR EACH ROW EXECUTE FUNCTION market_reject_immutable_change();
