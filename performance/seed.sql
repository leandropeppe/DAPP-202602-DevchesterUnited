-- Fixture determinista de performance para el esquema Flyway V1..V8.
-- Debe ejecutarse exclusivamente contra la base devchester_performance,
-- después de que Flyway haya aplicado las migraciones del backend.
--
-- Credencial sintética de los usuarios: password
-- Hash BCrypt real, coste 12, compartido por todos los usuarios del fixture.
-- Esta credencial existe únicamente para pruebas locales de performance.

BEGIN;

SET LOCAL TIME ZONE 'UTC';

-- La base de performance es descartable. No se toca flyway_schema_history.
TRUNCATE TABLE api_keys, jugadores, equipos, usuarios RESTART IDENTITY;

-- 20 ligas x 20 equipos = 400 equipos.
WITH ligas AS (
    SELECT liga
    FROM generate_series(1, 20) AS serie(liga)
), equipos_por_liga AS (
    SELECT l.liga, serie.equipo
    FROM ligas l
    CROSS JOIN generate_series(1, 20) AS serie(equipo)
)
INSERT INTO equipos (
    id,
    external_id,
    name,
    short_name,
    tla,
    league,
    crest_url,
    is_active,
    provider_season_id,
    season_start,
    season_end,
    created_at,
    updated_at
)
SELECT
    md5('performance-team-' || liga || '-' || equipo)::uuid,
    NULL,
    CASE
        WHEN liga = 1 AND equipo = 1 THEN 'Real Madrid'
        WHEN liga = 1 AND equipo = 2 THEN U&'Atl\00E9tico de Madrid'
        ELSE format('Performance Team %s-%s', lpad(liga::text, 2, '0'), lpad(equipo::text, 2, '0'))
    END,
    CASE
        WHEN liga = 1 AND equipo = 1 THEN 'Real Madrid'
        WHEN liga = 1 AND equipo = 2 THEN U&'Atl\00E9tico'
        ELSE format('Perf Team %s-%s', lpad(liga::text, 2, '0'), lpad(equipo::text, 2, '0'))
    END,
    format('P%s', lpad(((liga - 1) * 20 + equipo)::text, 3, '0')),
    CASE WHEN liga = 1 THEN 'LaLiga' ELSE format('Performance League %s', lpad(liga::text, 2, '0')) END,
    NULL,
    TRUE,
    NULL,
    NULL,
    NULL,
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
FROM equipos_por_liga;

-- 25 jugadores por equipo = 10.000 jugadores.
WITH equipos_por_liga AS (
    SELECT l.liga, e.equipo
    FROM generate_series(1, 20) AS l(liga)
    CROSS JOIN generate_series(1, 20) AS e(equipo)
), jugadores_por_equipo AS (
    SELECT l.liga, l.equipo, p.jugador
    FROM equipos_por_liga l
    CROSS JOIN generate_series(1, 25) AS p(jugador)
)
INSERT INTO jugadores (
    id,
    name,
    league,
    team,
    position,
    jersey_number,
    nationality,
    external_id,
    team_id,
    is_active,
    created_at,
    updated_at
)
SELECT
    md5('performance-player-' || ((liga - 1) * 500 + (equipo - 1) * 25 + jugador))::uuid,
    format('Synthetic Player %s', lpad(((liga - 1) * 500 + (equipo - 1) * 25 + jugador)::text, 5, '0')),
    CASE WHEN liga = 1 THEN 'LaLiga' ELSE format('Performance League %s', lpad(liga::text, 2, '0')) END,
    CASE
        WHEN liga = 1 AND equipo = 1 THEN 'Real Madrid'
        WHEN liga = 1 AND equipo = 2 THEN U&'Atl\00E9tico de Madrid'
        ELSE format('Performance Team %s-%s', lpad(liga::text, 2, '0'), lpad(equipo::text, 2, '0'))
    END,
    (ARRAY['Goalkeeper', 'Defender', 'Midfielder', 'Forward'])[((jugador - 1) % 4) + 1],
    jugador,
    'Syntheticland',
    NULL,
    md5('performance-team-' || liga || '-' || equipo)::uuid,
    TRUE,
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
FROM jugadores_por_equipo;

-- 1.000 usuarios con username strict-compatible y correo example.test.
INSERT INTO usuarios (id, username, email, password_hash, created_at, updated_at)
SELECT
    md5('performance-user-' || usuario)::uuid,
    format('perfuser%s', lpad(usuario::text, 4, '0')),
    format('perfuser%s@example.test', lpad(usuario::text, 4, '0')),
    '$2a$12$FyncB0rO79wXUe5Z1biea.ogzHvbevOdAD7Lh11NEV6N/G0/dGzzq',
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
FROM generate_series(1, 1000) AS serie(usuario);

-- Validaciones internas: un seed incompleto debe abortar la transacción.
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
