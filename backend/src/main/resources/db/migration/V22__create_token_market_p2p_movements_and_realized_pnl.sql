-- P2P accounting effects. Primary-market movements remain in market_movements.

ALTER TABLE market_p2p_trades
    ADD COLUMN realized_pnl NUMERIC(28, 8);

CREATE TABLE market_p2p_movements (
    id UUID PRIMARY KEY,
    trade_id UUID NOT NULL,
    movement_role VARCHAR(30) NOT NULL,
    asset_type VARCHAR(10) NOT NULL,
    credit_account_id UUID,
    token_user_id UUID,
    token_counterparty_user_id UUID,
    player_id UUID,
    quantity_delta NUMERIC(28, 8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_p2p_movement_trade
        FOREIGN KEY (trade_id) REFERENCES market_p2p_trades(id),
    CONSTRAINT fk_p2p_movement_credit_account
        FOREIGN KEY (credit_account_id) REFERENCES market_credit_accounts(id),
    CONSTRAINT fk_p2p_movement_token_user
        FOREIGN KEY (token_user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_p2p_movement_token_counterparty
        FOREIGN KEY (token_counterparty_user_id) REFERENCES usuarios(id),
    CONSTRAINT fk_p2p_movement_player
        FOREIGN KEY (player_id) REFERENCES jugadores(id),
    CONSTRAINT uq_p2p_movement_trade_role
        UNIQUE (trade_id, movement_role),
    CONSTRAINT ck_p2p_movement_role
        CHECK (movement_role IN (
            'BUYER_CREDIT_DEBIT',
            'SELLER_CREDIT_CREDIT',
            'SELLER_TOKEN_DEBIT',
            'BUYER_TOKEN_CREDIT'
        )),
    CONSTRAINT ck_p2p_movement_asset
        CHECK (
            (
                asset_type = 'CREDIT'
                AND movement_role IN ('BUYER_CREDIT_DEBIT', 'SELLER_CREDIT_CREDIT')
                AND credit_account_id IS NOT NULL
                AND token_user_id IS NULL
                AND token_counterparty_user_id IS NULL
                AND player_id IS NULL
            )
            OR
            (
                asset_type = 'TOKEN'
                AND movement_role IN ('SELLER_TOKEN_DEBIT', 'BUYER_TOKEN_CREDIT')
                AND credit_account_id IS NULL
                AND token_user_id IS NOT NULL
                AND token_counterparty_user_id IS NOT NULL
                AND token_user_id <> token_counterparty_user_id
                AND player_id IS NOT NULL
            )
        ),
    CONSTRAINT ck_p2p_movement_sign
        CHECK (
            (movement_role IN ('BUYER_CREDIT_DEBIT', 'SELLER_TOKEN_DEBIT')
                AND quantity_delta < 0)
            OR
            (movement_role IN ('SELLER_CREDIT_CREDIT', 'BUYER_TOKEN_CREDIT')
                AND quantity_delta > 0)
        ),
    CONSTRAINT ck_p2p_movement_delta
        CHECK (quantity_delta <> 0),
    CONSTRAINT ck_p2p_movement_created_at
        CHECK (created_at IS NOT NULL)
);

CREATE INDEX ix_p2p_movements_trade
    ON market_p2p_movements (trade_id);

CREATE INDEX ix_p2p_movements_user_date
    ON market_p2p_movements (token_user_id, created_at DESC);

CREATE INDEX ix_p2p_movements_player_date
    ON market_p2p_movements (player_id, created_at DESC);

CREATE INDEX ix_p2p_movements_credit_account_date
    ON market_p2p_movements (credit_account_id, created_at DESC);

CREATE OR REPLACE FUNCTION market_p2p_movements_immutable_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Los movimientos P2P confirmados son inmutables';
END;
$$;

CREATE TRIGGER trg_market_p2p_movements_immutable
BEFORE UPDATE OR DELETE ON market_p2p_movements
FOR EACH ROW EXECUTE FUNCTION market_p2p_movements_immutable_change();
