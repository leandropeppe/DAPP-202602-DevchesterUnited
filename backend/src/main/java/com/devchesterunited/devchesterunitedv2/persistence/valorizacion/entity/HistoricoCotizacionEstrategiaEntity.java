package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historico_cotizacion_estrategia")
public class HistoricoCotizacionEstrategiaEntity {
    @Id private UUID id;
    @Column(name = "jugador_id", nullable = false) private UUID jugadorId;
    @Column(name = "estrategia_id", nullable = false) private String estrategiaId;
    @Column(name = "estrategia_version", nullable = false) private int estrategiaVersion;
    @Column(name = "ejecucion_id", nullable = false) private UUID ejecucionId;
    @Column(name = "calculada_at", nullable = false) private Instant calculadaAt;
    @Column(nullable = false) private BigDecimal score;
    @Column(nullable = false) private BigDecimal cotizacion;
    @Column(name = "valor_base", nullable = false) private BigDecimal valorBase;
    @Column(name = "factor_escala", nullable = false) private BigDecimal factorEscala;
    @Column(name = "trazabilidad_json", nullable = false) private String trazabilidadJson;
    protected HistoricoCotizacionEstrategiaEntity() { }
    private HistoricoCotizacionEstrategiaEntity(Builder builder) {
        this.id = builder.id;
        this.jugadorId = builder.jugadorId;
        this.estrategiaId = builder.estrategiaId;
        this.estrategiaVersion = builder.version;
        this.ejecucionId = builder.ejecucionId;
        this.calculadaAt = builder.calculadaAt;
        this.score = builder.score;
        this.cotizacion = builder.cotizacion;
        this.valorBase = builder.valorBase;
        this.factorEscala = builder.factorEscala;
        this.trazabilidadJson = builder.trazabilidadJson;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private UUID jugadorId;
        private String estrategiaId;
        private int version;
        private UUID ejecucionId;
        private Instant calculadaAt;
        private BigDecimal score;
        private BigDecimal cotizacion;
        private BigDecimal valorBase;
        private BigDecimal factorEscala;
        private String trazabilidadJson;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder jugadorId(UUID jugadorId) { this.jugadorId = jugadorId; return this; }
        public Builder estrategiaId(String estrategiaId) {
            this.estrategiaId = estrategiaId;
            return this;
        }
        public Builder version(int version) { this.version = version; return this; }
        public Builder ejecucionId(UUID ejecucionId) {
            this.ejecucionId = ejecucionId;
            return this;
        }
        public Builder calculadaAt(Instant calculadaAt) {
            this.calculadaAt = calculadaAt;
            return this;
        }
        public Builder score(BigDecimal score) { this.score = score; return this; }
        public Builder cotizacion(BigDecimal cotizacion) {
            this.cotizacion = cotizacion;
            return this;
        }
        public Builder valorBase(BigDecimal valorBase) {
            this.valorBase = valorBase;
            return this;
        }
        public Builder factorEscala(BigDecimal factorEscala) {
            this.factorEscala = factorEscala;
            return this;
        }
        public Builder trazabilidadJson(String trazabilidadJson) {
            this.trazabilidadJson = trazabilidadJson;
            return this;
        }
        public HistoricoCotizacionEstrategiaEntity build() {
            return new HistoricoCotizacionEstrategiaEntity(this);
        }
    }
    public UUID getId() { return id; }
    public UUID getJugadorId() { return jugadorId; }
    public String getEstrategiaId() { return estrategiaId; }
    public int getEstrategiaVersion() { return estrategiaVersion; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getCotizacion() { return cotizacion; }
    public Instant getCalculadaAt() { return calculadaAt; }
    public UUID getEjecucionId() { return ejecucionId; }
    public String getTrazabilidadJson() { return trazabilidadJson; }
}
