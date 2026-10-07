package com.devchesterunited.devchesterunitedv2.persistence.dto;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "estadisticas_sync")
public class EstadisticasSyncEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEstadisticasSync estado;
    @Column(name = "iniciada_at", nullable = false)
    private Instant iniciadaAt;
    @Column(name = "finalizada_at")
    private Instant finalizadaAt;
    @Column(name = "error_tecnico")
    private String errorTecnico;

    protected EstadisticasSyncEntity() { }

    public EstadisticasSyncEntity(UUID id, EstadoEstadisticasSync estado, Instant iniciadaAt) {
        this.id = id;
        this.estado = estado;
        this.iniciadaAt = iniciadaAt;
    }

    public UUID getId() { return id; }
    public EstadoEstadisticasSync getEstado() { return estado; }
    public Instant getIniciadaAt() { return iniciadaAt; }
    public Instant getFinalizadaAt() { return finalizadaAt; }
    public String getErrorTecnico() { return errorTecnico; }

    public void cambiarEstado(EstadoEstadisticasSync nuevoEstado, Instant finalizadaEn, String error) {
        this.estado = nuevoEstado;
        this.finalizadaAt = finalizadaEn;
        this.errorTecnico = error;
    }
}
