CREATE TABLE jugador_estadisticas (
    id UUID PRIMARY KEY,
    jugador_id UUID NOT NULL UNIQUE,
    posicion VARCHAR(30),
    minutos_jugados INTEGER,
    goles INTEGER,
    asistencias INTEGER,
    tiros NUMERIC(10, 3),
    pases_clave NUMERIC(10, 3),
    regates NUMERIC(10, 3),
    tackles NUMERIC(10, 3),
    valoracion NUMERIC(10, 3),
    tarjetas_amarillas INTEGER,
    tarjetas_rojas INTEGER,
    sync_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_jugador_estadisticas_jugador
        FOREIGN KEY (jugador_id) REFERENCES jugadores(id),
    CONSTRAINT ck_jugador_estadisticas_numeros CHECK (
        (minutos_jugados IS NULL OR minutos_jugados >= 0)
        AND (goles IS NULL OR goles >= 0)
        AND (asistencias IS NULL OR asistencias >= 0)
        AND (tiros IS NULL OR tiros >= 0)
        AND (pases_clave IS NULL OR pases_clave >= 0)
        AND (regates IS NULL OR regates >= 0)
        AND (tackles IS NULL OR tackles >= 0)
        AND (valoracion IS NULL OR valoracion >= 0)
        AND (tarjetas_amarillas IS NULL OR tarjetas_amarillas >= 0)
        AND (tarjetas_rojas IS NULL OR tarjetas_rojas >= 0)
    ),
    CONSTRAINT ck_jugador_estadisticas_fechas CHECK (updated_at >= created_at)
);

CREATE INDEX ix_jugador_estadisticas_sync_id ON jugador_estadisticas(sync_id);

CREATE TABLE estadisticas_sync (
    id UUID PRIMARY KEY,
    estado VARCHAR(20) NOT NULL,
    iniciada_at TIMESTAMPTZ NOT NULL,
    finalizada_at TIMESTAMPTZ,
    error_tecnico TEXT,
    CONSTRAINT ck_estadisticas_sync_estado CHECK (
        estado IN ('INICIADA', 'RECIBIENDO', 'VALIDADA', 'PUBLICADA', 'FALLIDA')
    ),
    CONSTRAINT ck_estadisticas_sync_fechas CHECK (
        finalizada_at IS NULL OR finalizada_at >= iniciada_at
    )
);

CREATE TABLE estadisticas_sync_staging (
    id UUID PRIMARY KEY,
    sync_id UUID NOT NULL,
    competicion VARCHAR(40) NOT NULL,
    whoscored_player_id BIGINT NOT NULL,
    whoscored_team_id BIGINT,
    jugador_id UUID,
    equipo_id UUID,
    nombre_jugador VARCHAR(200) NOT NULL,
    posicion VARCHAR(30),
    nacionalidad VARCHAR(100),
    nombre_equipo VARCHAR(100),
    familia_general_recibida BOOLEAN NOT NULL DEFAULT FALSE,
    familia_ofensiva_recibida BOOLEAN NOT NULL DEFAULT FALSE,
    familia_defensiva_recibida BOOLEAN NOT NULL DEFAULT FALSE,
    minutos_jugados INTEGER,
    goles INTEGER,
    asistencias INTEGER,
    tiros NUMERIC(10, 3),
    pases_clave NUMERIC(10, 3),
    regates NUMERIC(10, 3),
    tackles NUMERIC(10, 3),
    valoracion NUMERIC(10, 3),
    tarjetas_amarillas INTEGER,
    tarjetas_rojas INTEGER,
    CONSTRAINT fk_estadisticas_staging_sync
        FOREIGN KEY (sync_id) REFERENCES estadisticas_sync(id) ON DELETE CASCADE,
    CONSTRAINT fk_estadisticas_staging_jugador
        FOREIGN KEY (jugador_id) REFERENCES jugadores(id),
    CONSTRAINT fk_estadisticas_staging_equipo
        FOREIGN KEY (equipo_id) REFERENCES equipos(id),
    CONSTRAINT ck_estadisticas_staging_player_id CHECK (whoscored_player_id > 0),
    CONSTRAINT ck_estadisticas_staging_team_id CHECK (
        whoscored_team_id IS NULL OR whoscored_team_id > 0
    ),
    CONSTRAINT ck_estadisticas_staging_numeros CHECK (
        (minutos_jugados IS NULL OR minutos_jugados >= 0)
        AND (goles IS NULL OR goles >= 0)
        AND (asistencias IS NULL OR asistencias >= 0)
        AND (tiros IS NULL OR tiros >= 0)
        AND (pases_clave IS NULL OR pases_clave >= 0)
        AND (regates IS NULL OR regates >= 0)
        AND (tackles IS NULL OR tackles >= 0)
        AND (valoracion IS NULL OR valoracion >= 0)
        AND (tarjetas_amarillas IS NULL OR tarjetas_amarillas >= 0)
        AND (tarjetas_rojas IS NULL OR tarjetas_rojas >= 0)
    ),
    CONSTRAINT ux_estadisticas_staging_sync_competicion_player
        UNIQUE (sync_id, competicion, whoscored_player_id)
);

CREATE INDEX ix_estadisticas_staging_sync ON estadisticas_sync_staging(sync_id);
CREATE INDEX ix_estadisticas_staging_player ON estadisticas_sync_staging(jugador_id);
