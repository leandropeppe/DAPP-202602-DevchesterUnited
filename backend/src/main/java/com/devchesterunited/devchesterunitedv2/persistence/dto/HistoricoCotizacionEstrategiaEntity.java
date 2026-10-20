package com.devchesterunited.devchesterunitedv2.persistence.dto;

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
    public HistoricoCotizacionEstrategiaEntity(UUID id, UUID jugadorId, String estrategiaId, int version,
            UUID ejecucionId, Instant calculadaAt, BigDecimal score, BigDecimal cotizacion,
            BigDecimal valorBase, BigDecimal factorEscala, String trazabilidadJson) {
        this.id=id; this.jugadorId=jugadorId; this.estrategiaId=estrategiaId; this.estrategiaVersion=version;
        this.ejecucionId=ejecucionId; this.calculadaAt=calculadaAt; this.score=score; this.cotizacion=cotizacion;
        this.valorBase=valorBase; this.factorEscala=factorEscala; this.trazabilidadJson=trazabilidadJson;
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
