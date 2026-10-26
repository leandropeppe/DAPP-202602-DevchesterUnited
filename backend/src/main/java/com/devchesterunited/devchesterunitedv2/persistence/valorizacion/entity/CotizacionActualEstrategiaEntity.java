package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cotizacion_actual_estrategia")
@IdClass(CotizacionActualEstrategiaKey.class)
public class CotizacionActualEstrategiaEntity {
    @Id @Column(name = "jugador_id") private UUID jugadorId;
    @Id @Column(name = "estrategia_id") private String estrategiaId;
    @Id @Column(name = "estrategia_version") private int estrategiaVersion;
    @Column(name = "ejecucion_id") private UUID ejecucionId;
    private BigDecimal score;
    private BigDecimal cotizacion;
    @Column(name = "valor_base") private BigDecimal valorBase;
    @Column(name = "factor_escala") private BigDecimal factorEscala;
    @Column(name = "calculada_at") private Instant calculadaAt;
    protected CotizacionActualEstrategiaEntity() { }
    public CotizacionActualEstrategiaEntity(UUID jugadorId, String estrategiaId, int version, UUID ejecucionId,
            BigDecimal score, BigDecimal cotizacion, BigDecimal valorBase, BigDecimal factorEscala, Instant calculadaAt) {
        this.jugadorId=jugadorId; this.estrategiaId=estrategiaId; this.estrategiaVersion=version; this.ejecucionId=ejecucionId;
        this.score=score; this.cotizacion=cotizacion; this.valorBase=valorBase; this.factorEscala=factorEscala; this.calculadaAt=calculadaAt;
    }
    public UUID getJugadorId() { return jugadorId; }
    public String getEstrategiaId() { return estrategiaId; }
    public int getEstrategiaVersion() { return estrategiaVersion; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getCotizacion() { return cotizacion; }
    public Instant getCalculadaAt() { return calculadaAt; }
    public UUID getEjecucionId() { return ejecucionId; }
}
