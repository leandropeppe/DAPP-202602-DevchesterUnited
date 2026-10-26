package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ejecuciones_valorizacion")
public class EjecucionValorizacionEntity {
    @Id private UUID id;
    private String tipo;
    private Instant snapshotAt;
    private Instant iniciadaAt;
    private Instant finalizadaAt;
    private String estado;
    protected EjecucionValorizacionEntity() { }
    public EjecucionValorizacionEntity(UUID id, String tipo, Instant snapshotAt, Instant iniciadaAt, String estado) {
        this.id = id; this.tipo = tipo; this.snapshotAt = snapshotAt; this.iniciadaAt = iniciadaAt; this.estado = estado;
    }
    public UUID getId() { return id; }
    public String getTipo() { return tipo; }
    public Instant getSnapshotAt() { return snapshotAt; }
    public Instant getIniciadaAt() { return iniciadaAt; }
    public Instant getFinalizadaAt() { return finalizadaAt; }
    public String getEstado() { return estado; }
    public void finalizar(Instant finalizadaAt, String estado) {
        this.finalizadaAt = finalizadaAt;
        this.estado = estado;
    }
}
