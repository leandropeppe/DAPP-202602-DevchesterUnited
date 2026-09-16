-- V4 agrega identidad externa y actividad sin borrar las columnas legacy de V3.
-- Se conservan para que el despliegue coordinado no rompa lectores US3 existentes.
CREATE TABLE equipos (
    id UUID PRIMARY KEY,
    external_id BIGINT UNIQUE,
    name VARCHAR(100) NOT NULL,
    short_name VARCHAR(100),
    tla VARCHAR(10),
    league VARCHAR(100) NOT NULL,
    crest_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    provider_season_id BIGINT,
    season_start DATE,
    season_end DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_equipos_external_id CHECK (external_id IS NULL OR external_id > 0),
    CONSTRAINT ck_equipos_provider_season CHECK (provider_season_id IS NULL OR provider_season_id > 0),
    CONSTRAINT ck_equipos_dates CHECK (season_start IS NULL AND season_end IS NULL OR season_end >= season_start),
    CONSTRAINT ck_equipos_updated CHECK (updated_at >= created_at),
    CONSTRAINT ck_equipos_crest_https CHECK (crest_url IS NULL OR crest_url ~ '^https://')
);

INSERT INTO equipos (id, name, league, is_active, created_at, updated_at)
SELECT (md5(league || '::' || team))::uuid, team, league, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT DISTINCT league, team FROM jugadores) AS origen;

ALTER TABLE jugadores ADD COLUMN external_id BIGINT;
ALTER TABLE jugadores ADD COLUMN team_id UUID;
ALTER TABLE jugadores ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE jugadores ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE jugadores ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE jugadores ALTER COLUMN jersey_number DROP NOT NULL;

UPDATE jugadores j
SET team_id = e.id
FROM equipos e
WHERE e.league = j.league AND e.name = j.team;

-- Compatibilidad transitoria para fixtures legacy que siguen escribiendo league/team.
-- No asigna external_id: solo mantiene la FK normalizada y la identidad local.
CREATE OR REPLACE FUNCTION sync_legacy_jugador_team() RETURNS trigger AS $$
BEGIN
    IF NEW.team_id IS NULL THEN
        INSERT INTO equipos (id, name, league, is_active, created_at, updated_at)
        VALUES ((md5(NEW.league || '::' || NEW.team))::uuid, NEW.team, NEW.league, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (id) DO NOTHING;
        NEW.team_id := (md5(NEW.league || '::' || NEW.team))::uuid;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_jugadores_legacy_team
BEFORE INSERT OR UPDATE OF league, team ON jugadores
FOR EACH ROW EXECUTE FUNCTION sync_legacy_jugador_team();

ALTER TABLE jugadores ALTER COLUMN team_id SET NOT NULL;
ALTER TABLE jugadores ADD CONSTRAINT fk_jugadores_equipo FOREIGN KEY (team_id) REFERENCES equipos(id);
ALTER TABLE jugadores ADD CONSTRAINT ck_jugadores_external_id CHECK (external_id IS NULL OR external_id > 0);
ALTER TABLE jugadores ADD CONSTRAINT ck_jugadores_active_updated CHECK (updated_at >= created_at);
ALTER TABLE jugadores ADD CONSTRAINT ck_jugadores_jersey_nullable CHECK (jersey_number IS NULL OR jersey_number BETWEEN 1 AND 99);
CREATE UNIQUE INDEX ux_jugadores_external_id ON jugadores(external_id) WHERE external_id IS NOT NULL;
CREATE INDEX ix_jugadores_team_id ON jugadores(team_id);
