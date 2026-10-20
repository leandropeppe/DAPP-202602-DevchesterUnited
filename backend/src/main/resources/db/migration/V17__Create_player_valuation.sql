CREATE TABLE estrategias_valorizacion (
    id VARCHAR(40) NOT NULL,
    version INTEGER NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    habilitada_para_calculo BOOLEAN NOT NULL,
    es_provisoria BOOLEAN NOT NULL,
    valor_base NUMERIC(19, 4) NOT NULL,
    factor_escala NUMERIC(19, 4) NOT NULL,
    configuracion_json TEXT NOT NULL,
    PRIMARY KEY (id, version),
    CONSTRAINT ck_estrategia_tipo CHECK (tipo IN ('GENERAL', 'POSICIONAL')),
    CONSTRAINT ck_estrategia_valores CHECK (valor_base >= 0 AND factor_escala >= 0)
);

CREATE TABLE estrategia_visible (
    singleton_id SMALLINT PRIMARY KEY,
    estrategia_id VARCHAR(40) NOT NULL,
    estrategia_version INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_estrategia_visible_singleton CHECK (singleton_id = 1),
    CONSTRAINT fk_estrategia_visible_estrategia FOREIGN KEY (estrategia_id, estrategia_version)
        REFERENCES estrategias_valorizacion(id, version)
);

CREATE TABLE ejecuciones_valorizacion (
    id UUID PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    snapshot_at TIMESTAMPTZ NOT NULL,
    iniciada_at TIMESTAMPTZ NOT NULL,
    finalizada_at TIMESTAMPTZ,
    estado VARCHAR(20) NOT NULL,
    CONSTRAINT ck_ejecucion_tipo CHECK (tipo IN ('MANUAL', 'SEMANAL')),
    CONSTRAINT ck_ejecucion_estado CHECK (estado IN ('INICIADA', 'COMPLETADA', 'FALLIDA')),
    CONSTRAINT ck_ejecucion_fechas CHECK (finalizada_at IS NULL OR finalizada_at >= iniciada_at)
);

CREATE TABLE cotizacion_actual_estrategia (
    jugador_id UUID NOT NULL,
    estrategia_id VARCHAR(40) NOT NULL,
    estrategia_version INTEGER NOT NULL,
    ejecucion_id UUID NOT NULL,
    score NUMERIC(19, 8) NOT NULL,
    cotizacion NUMERIC(19, 4) NOT NULL,
    valor_base NUMERIC(19, 4) NOT NULL,
    factor_escala NUMERIC(19, 4) NOT NULL,
    calculada_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (jugador_id, estrategia_id, estrategia_version),
    CONSTRAINT fk_cotizacion_actual_jugador FOREIGN KEY (jugador_id) REFERENCES jugadores(id),
    CONSTRAINT fk_cotizacion_actual_estrategia FOREIGN KEY (estrategia_id, estrategia_version)
        REFERENCES estrategias_valorizacion(id, version),
    CONSTRAINT fk_cotizacion_actual_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecuciones_valorizacion(id),
    CONSTRAINT ck_cotizacion_actual_score CHECK (score >= 0 AND score <= 1),
    CONSTRAINT ck_cotizacion_actual_valores CHECK (cotizacion >= 0 AND valor_base >= 0 AND factor_escala >= 0)
);

CREATE TABLE historico_cotizacion_estrategia (
    id UUID PRIMARY KEY,
    jugador_id UUID NOT NULL,
    estrategia_id VARCHAR(40) NOT NULL,
    estrategia_version INTEGER NOT NULL,
    ejecucion_id UUID NOT NULL,
    calculada_at TIMESTAMPTZ NOT NULL,
    score NUMERIC(19, 8) NOT NULL,
    cotizacion NUMERIC(19, 4) NOT NULL,
    valor_base NUMERIC(19, 4) NOT NULL,
    factor_escala NUMERIC(19, 4) NOT NULL,
    trazabilidad_json TEXT NOT NULL,
    CONSTRAINT fk_historico_cotizacion_jugador FOREIGN KEY (jugador_id) REFERENCES jugadores(id),
    CONSTRAINT fk_historico_cotizacion_estrategia FOREIGN KEY (estrategia_id, estrategia_version)
        REFERENCES estrategias_valorizacion(id, version),
    CONSTRAINT fk_historico_cotizacion_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecuciones_valorizacion(id),
    CONSTRAINT ck_historico_cotizacion_score CHECK (score >= 0 AND score <= 1),
    CONSTRAINT ck_historico_cotizacion_valores CHECK (cotizacion >= 0 AND valor_base >= 0 AND factor_escala >= 0)
);

CREATE TABLE resultado_elegibilidad_valorizacion (
    ejecucion_id UUID NOT NULL,
    jugador_id UUID NOT NULL,
    estrategia_id VARCHAR(40) NOT NULL,
    estrategia_version INTEGER NOT NULL,
    elegible BOOLEAN NOT NULL,
    causas_json TEXT NOT NULL,
    metricas_negativas_ausentes_json TEXT NOT NULL,
    PRIMARY KEY (ejecucion_id, jugador_id, estrategia_id, estrategia_version),
    CONSTRAINT fk_resultado_ejecucion FOREIGN KEY (ejecucion_id) REFERENCES ejecuciones_valorizacion(id),
    CONSTRAINT fk_resultado_jugador FOREIGN KEY (jugador_id) REFERENCES jugadores(id),
    CONSTRAINT fk_resultado_estrategia FOREIGN KEY (estrategia_id, estrategia_version)
        REFERENCES estrategias_valorizacion(id, version)
);

CREATE INDEX ix_historico_cotizacion_jugador_estrategia
    ON historico_cotizacion_estrategia (jugador_id, estrategia_id, estrategia_version, calculada_at DESC);
