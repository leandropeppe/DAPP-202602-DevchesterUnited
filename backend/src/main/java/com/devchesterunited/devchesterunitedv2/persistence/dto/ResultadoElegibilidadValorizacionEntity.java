package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "resultado_elegibilidad_valorizacion")
@IdClass(ResultadoElegibilidadValorizacionKey.class)
public class ResultadoElegibilidadValorizacionEntity {
    @Id private UUID ejecucionId;
    @Id private UUID jugadorId;
    @Id private String estrategiaId;
    @Id private int estrategiaVersion;
    private boolean elegible;
    private String causasJson;
    private String metricasNegativasAusentesJson;
    protected ResultadoElegibilidadValorizacionEntity() { }
    public ResultadoElegibilidadValorizacionEntity(UUID ejecucionId, UUID jugadorId, String estrategiaId,
            int estrategiaVersion, boolean elegible, String causasJson, String metricasNegativasAusentesJson) {
        this.ejecucionId=ejecucionId; this.jugadorId=jugadorId; this.estrategiaId=estrategiaId;
        this.estrategiaVersion=estrategiaVersion; this.elegible=elegible; this.causasJson=causasJson;
        this.metricasNegativasAusentesJson=metricasNegativasAusentesJson;
    }
    public UUID getEjecucionId() { return ejecucionId; }
    public UUID getJugadorId() { return jugadorId; }
    public String getEstrategiaId() { return estrategiaId; }
    public int getEstrategiaVersion() { return estrategiaVersion; }
    public boolean isElegible() { return elegible; }
    public String getCausasJson() { return causasJson; }
    public String getMetricasNegativasAusentesJson() { return metricasNegativasAusentesJson; }
}
