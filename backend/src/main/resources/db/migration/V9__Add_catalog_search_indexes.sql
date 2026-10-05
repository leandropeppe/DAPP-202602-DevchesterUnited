-- Los filtros del catálogo son subcadenas case-insensitive (ILIKE '%...%').
-- Los índices trigram aceleran esas búsquedas sin cambiar su semántica literal.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_jugadores_league_trgm_active
    ON jugadores USING gin (league gin_trgm_ops)
    WHERE is_active = TRUE;

CREATE INDEX ix_jugadores_team_trgm_active
    ON jugadores USING gin (team gin_trgm_ops)
    WHERE is_active = TRUE;

CREATE INDEX ix_jugadores_position_trgm_active
    ON jugadores USING gin (position gin_trgm_ops)
    WHERE is_active = TRUE;
