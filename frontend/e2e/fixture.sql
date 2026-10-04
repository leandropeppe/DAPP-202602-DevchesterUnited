TRUNCATE TABLE jugadores, equipos CASCADE;

INSERT INTO equipos (id, external_id, name, short_name, tla, league, is_active, created_at, updated_at)
VALUES
  ('40000000-0000-0000-0000-000000000001', NULL, 'Real Madrid', 'Real Madrid', 'RMA', 'LaLiga', TRUE, TIMESTAMPTZ '2026-01-01T00:00:00Z', TIMESTAMPTZ '2026-01-01T00:00:00Z'),
  ('40000000-0000-0000-0000-000000000002', NULL, 'Atletico Madrid', 'Atletico', 'ATM', 'LaLiga', TRUE, TIMESTAMPTZ '2026-01-01T00:00:00Z', TIMESTAMPTZ '2026-01-01T00:00:00Z');

INSERT INTO jugadores (id, name, position, jersey_number, nationality, team_id, is_active, created_at, updated_at)
SELECT
  ('50000000-0000-0000-0000-' || lpad(g::text, 12, '0'))::uuid,
  CASE WHEN g <= 12 THEN 'Jugador Real ' ELSE 'Jugador Atletico ' END || lpad(((g - 1) % 12 + 1)::text, 2, '0'),
  CASE (g - 1) % 4 WHEN 0 THEN 'Goalkeeper' WHEN 1 THEN 'Defender' WHEN 2 THEN 'Midfielder' ELSE 'Forward' END,
  CASE WHEN g % 5 = 1 THEN NULL ELSE ((g - 1) % 12 + 1) END,
  'Espana',
  CASE WHEN g <= 12 THEN '40000000-0000-0000-0000-000000000001'::uuid ELSE '40000000-0000-0000-0000-000000000002'::uuid END,
  TRUE,
  TIMESTAMPTZ '2026-01-01T00:00:00Z',
  TIMESTAMPTZ '2026-01-01T00:00:00Z'
FROM generate_series(1, 24) AS serie(g);
