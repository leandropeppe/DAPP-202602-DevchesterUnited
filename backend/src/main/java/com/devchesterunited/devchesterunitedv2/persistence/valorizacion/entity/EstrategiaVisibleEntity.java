package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "estrategia_visible")
public class EstrategiaVisibleEntity {
    @Id
    @Column(name = "singleton_id")
    private short singletonId;
    @Column(name = "estrategia_id", nullable = false)
    private String estrategiaId;
    @Column(name = "estrategia_version", nullable = false)
    private int estrategiaVersion;
    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected EstrategiaVisibleEntity() { }
    public EstrategiaVisibleEntity(String estrategiaId, int estrategiaVersion, Instant actualizadoEn) {
        this.singletonId = 1;
        this.estrategiaId = estrategiaId;
        this.estrategiaVersion = estrategiaVersion;
        this.actualizadoEn = actualizadoEn;
    }
    public short getSingletonId() { return singletonId; }
    public String getEstrategiaId() { return estrategiaId; }
    public int getEstrategiaVersion() { return estrategiaVersion; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
