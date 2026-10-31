ALTER TABLE market_p2p_trades
    ADD COLUMN offer_status_after_trade VARCHAR(20),
    ADD COLUMN remaining_quantity_after_trade BIGINT,
    ADD COLUMN reserved_quantity_after_trade BIGINT,
    ADD CONSTRAINT ck_market_p2p_trade_offer_snapshot_complete
        CHECK ((offer_status_after_trade IS NULL
                AND remaining_quantity_after_trade IS NULL
                AND reserved_quantity_after_trade IS NULL)
            OR (offer_status_after_trade IS NOT NULL
                AND remaining_quantity_after_trade IS NOT NULL
                AND reserved_quantity_after_trade IS NOT NULL)),
    ADD CONSTRAINT ck_market_p2p_trade_offer_snapshot_status
        CHECK (offer_status_after_trade IS NULL
            OR offer_status_after_trade IN ('PARTIALLY_FILLED', 'FILLED')),
    ADD CONSTRAINT ck_market_p2p_trade_offer_snapshot_remaining
        CHECK (remaining_quantity_after_trade IS NULL
            OR remaining_quantity_after_trade >= 0),
    ADD CONSTRAINT ck_market_p2p_trade_offer_snapshot_reserved
        CHECK (reserved_quantity_after_trade IS NULL
            OR reserved_quantity_after_trade >= 0),
    ADD CONSTRAINT ck_market_p2p_trade_offer_snapshot_coherence
        CHECK (offer_status_after_trade IS NULL
            OR (offer_status_after_trade = 'FILLED'
                AND remaining_quantity_after_trade = 0)
            OR (offer_status_after_trade = 'PARTIALLY_FILLED'
                AND remaining_quantity_after_trade > 0));
