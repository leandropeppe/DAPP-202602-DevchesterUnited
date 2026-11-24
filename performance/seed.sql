-- Fixture determinista para el esquema Flyway V1..V23.
-- Debe ejecutarse exclusivamente contra la base temporal verificada por el runner.
-- No modifica flyway_schema_history ni el esquema.

BEGIN;

SET LOCAL TIME ZONE 'UTC';

-- El runner verifica proyecto, contenedor, puerto y base antes de ejecutar este
-- TRUNCATE. CASCADE solo puede afectar tablas de esta base temporal descartable.
TRUNCATE TABLE api_keys, jugadores, equipos, usuarios RESTART IDENTITY CASCADE;

-- 400 equipos sintéticos con el modelo normalizado vigente.
WITH equipos_sinteticos AS (
    SELECT serie.equipo
    FROM generate_series(1, 400) AS serie(equipo)
)
INSERT INTO equipos (
    id,
    name,
    league,
    short_name,
    tla,
    crest_url,
    is_active,
    provider_season_id,
    season_start,
    season_end,
    created_at,
    updated_at
)
SELECT
    md5('performance-team-' || equipo)::uuid,
    CASE
        WHEN equipo = 1 THEN 'Real Madrid'
        WHEN equipo = 2 THEN 'Atletico de Madrid'
        ELSE format('Performance Team %s', lpad(equipo::text, 3, '0'))
    END,
    CASE
        WHEN equipo IN (1, 2) THEN 'LaLiga'
        ELSE 'Performance League'
    END,
    CASE
        WHEN equipo = 1 THEN 'Real Madrid'
        WHEN equipo = 2 THEN 'Atletico'
        ELSE format('Perf Team %s', lpad(equipo::text, 3, '0'))
    END,
    format('P%s', lpad(equipo::text, 3, '0')),
    NULL,
    TRUE,
    NULL,
    NULL,
    NULL,
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
FROM equipos_sinteticos;

-- 25 jugadores por equipo = 10.000 jugadores, relacionados solo por team_id.
WITH jugadores_sinteticos AS (
    SELECT equipo, jugador,
        ((equipo - 1) * 25 + jugador) AS ordinal
    FROM generate_series(1, 400) AS equipos(equipo)
    CROSS JOIN generate_series(1, 25) AS jugadores(jugador)
)
INSERT INTO jugadores (
    id,
    name,
    position,
    jersey_number,
    nationality,
    team_id,
    is_active,
    created_at,
    updated_at
)
SELECT
    md5('performance-player-' || ordinal)::uuid,
    format('Synthetic Player %s', lpad(ordinal::text, 5, '0')),
    (ARRAY['Goalkeeper', 'Defender', 'Midfielder', 'Forward'])[((jugador - 1) % 4) + 1],
    jugador,
    'Syntheticland',
    md5('performance-team-' || equipo)::uuid,
    TRUE,
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
FROM jugadores_sinteticos;

-- 1.000 usuarios sintéticos. El hash cumple el constraint sin documentar una clave.
INSERT INTO usuarios (id, username, email, password_hash, created_at, updated_at)
SELECT
    md5('performance-user-' || usuario)::uuid,
    format('perfuser%s', lpad(usuario::text, 4, '0')),
    format('perfuser%s@example.test', lpad(usuario::text, 4, '0')),
    '$2a$12$FyncB0rO79wXUe5Z1biea.ogzHvbevOdAD7Lh11NEV6N/G0/dGzzq',
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
FROM generate_series(1, 1000) AS serie(usuario);

ANALYZE equipos;
ANALYZE jugadores;
ANALYZE usuarios;

DO $$
BEGIN
    IF (SELECT count(*) FROM equipos) <> 400
       OR (SELECT count(*) FROM equipos WHERE is_active) <> 400 THEN
        RAISE EXCEPTION 'Fixture invalido: se esperaban 400 equipos activos';
    END IF;

    IF (SELECT count(*) FROM jugadores) <> 10000
       OR (SELECT count(*) FROM jugadores WHERE is_active) <> 10000 THEN
        RAISE EXCEPTION 'Fixture invalido: se esperaban 10000 jugadores activos';
    END IF;

    IF (SELECT count(*) FROM usuarios) <> 1000 THEN
        RAISE EXCEPTION 'Fixture invalido: se esperaban 1000 usuarios';
    END IF;

    IF (SELECT count(*) FROM api_keys) <> 0 THEN
        RAISE EXCEPTION 'Fixture invalido: api_keys debe quedar vacia';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM jugadores j
        LEFT JOIN equipos e ON e.id = j.team_id
        WHERE e.id IS NULL OR NOT e.is_active
    ) THEN
        RAISE EXCEPTION 'Fixture invalido: jugador sin equipo activo';
    END IF;
END $$;

COMMIT;
