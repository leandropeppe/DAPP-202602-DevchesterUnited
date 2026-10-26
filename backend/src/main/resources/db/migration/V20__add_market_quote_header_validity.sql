ALTER TABLE market_quote_headers
    ADD COLUMN validity_status VARCHAR(20) NOT NULL DEFAULT 'VALID',
    ADD COLUMN invalidated_at TIMESTAMPTZ,
    ADD COLUMN invalidation_reason VARCHAR(200);

ALTER TABLE market_quote_headers
    ADD CONSTRAINT ck_market_quote_header_status
        CHECK (validity_status IN ('VALID', 'INVALIDATED')),
    ADD CONSTRAINT ck_market_quote_header_invalidation_data
        CHECK (
            (validity_status = 'VALID'
                AND invalidated_at IS NULL
                AND invalidation_reason IS NULL)
            OR
            (validity_status = 'INVALIDATED'
                AND invalidated_at IS NOT NULL
                AND invalidation_reason IS NOT NULL
                AND btrim(invalidation_reason) <> '')
        );
