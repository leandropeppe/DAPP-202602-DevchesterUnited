CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    key_hash CHAR(64) NOT NULL,
    key_prefix VARCHAR(14) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
 CONSTRAINT fk_api_keys_usuario FOREIGN KEY (user_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT uk_api_keys_hash UNIQUE (key_hash),
    CONSTRAINT ck_api_keys_hash CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_api_keys_prefix CHECK (key_prefix ~ '^dev_key_[A-Za-z0-9_-]{6}$'),
    CONSTRAINT ck_api_keys_estado CHECK (
        (is_active AND revoked_at IS NULL)
        OR (NOT is_active AND revoked_at IS NOT NULL AND revoked_at >= created_at)
    )
);

CREATE UNIQUE INDEX ux_api_keys_usuario_activa
    ON api_keys (user_id)
    WHERE is_active = TRUE;
