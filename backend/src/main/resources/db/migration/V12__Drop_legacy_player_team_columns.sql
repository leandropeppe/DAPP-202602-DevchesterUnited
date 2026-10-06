-- V12 elimina la duplicación de liga/equipo luego de normalizar jugadores.team_id.
-- Se retiran primero las dependencias conocidas: trigger, función e índices de V9.
DROP TRIGGER IF EXISTS trg_jugadores_legacy_team ON jugadores;
DROP FUNCTION IF EXISTS sync_legacy_jugador_team();
DROP INDEX IF EXISTS ix_jugadores_league_trgm_active;
DROP INDEX IF EXISTS ix_jugadores_team_trgm_active;

-- Las constraints de V3 están ligadas a las columnas que se eliminan.
ALTER TABLE jugadores DROP CONSTRAINT IF EXISTS ck_jugadores_league;
ALTER TABLE jugadores DROP CONSTRAINT IF EXISTS ck_jugadores_team;

ALTER TABLE jugadores DROP COLUMN league;
ALTER TABLE jugadores DROP COLUMN team;
