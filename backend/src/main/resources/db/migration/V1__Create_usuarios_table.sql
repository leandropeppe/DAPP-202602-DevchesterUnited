CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    username VARCHAR(30) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_usuarios_username UNIQUE (username),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_username CHECK (username ~ '^[a-z0-9]{3,30}$'),
    CONSTRAINT ck_usuarios_email CHECK (
        email = lower(btrim(email)) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ),
    CONSTRAINT ck_usuarios_password_hash CHECK (
        password_hash ~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'
    ),
    CONSTRAINT ck_usuarios_fechas CHECK (updated_at >= created_at)
);
