-- Identidades por proveedor. La columna jugadores.external_id se conserva durante la transición.
CREATE TABLE identidades_externas_jugador (
    id UUID PRIMARY KEY,
    jugador_id UUID NOT NULL,
    proveedor VARCHAR(30) NOT NULL,
    external_id BIGINT NOT NULL,
    CONSTRAINT fk_identidades_jugador FOREIGN KEY (jugador_id) REFERENCES jugadores(id) ON DELETE CASCADE,
    CONSTRAINT ck_identidades_external_id CHECK (external_id > 0),
    CONSTRAINT ck_identidades_proveedor CHECK (proveedor IN ('FOOTBALL_DATA', 'WHOSCORED')),
    CONSTRAINT ux_identidades_proveedor_external_id UNIQUE (proveedor, external_id),
    CONSTRAINT ux_identidades_jugador_proveedor UNIQUE (jugador_id, proveedor)
);

-- No se elige silenciosamente entre datos incompatibles: la migración debe abortar.
DO $$
BEGIN
    IF EXISTS (
        SELECT external_id
        FROM jugadores
        WHERE external_id IS NOT NULL
        GROUP BY external_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'No se puede hacer backfill: jugadores.external_id contiene duplicados';
    END IF;
END $$;

INSERT INTO identidades_externas_jugador (id, jugador_id, proveedor, external_id)
SELECT md5('football-data::' || id::text)::uuid, id, 'FOOTBALL_DATA', external_id
FROM jugadores
WHERE external_id IS NOT NULL;
