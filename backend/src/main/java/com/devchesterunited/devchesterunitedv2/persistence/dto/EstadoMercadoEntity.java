package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_state")
public class EstadoMercadoEntity {
    @Id
    @Column(nullable = false)
    private Short id;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Long epoch;

    @Column(name = "initial_reserve_configured", precision = 20, scale = 2)
    private BigDecimal reservaInicialConfigurada;

    @Column(name = "initial_reserve_initialized", precision = 20, scale = 2)
    private BigDecimal reservaInicialInicializada;

    @Column(name = "failure_reason")
    private String motivoFallo;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected EstadoMercadoEntity() { }

    public EstadoMercadoEntity(Short id, String status, Long epoch,
            BigDecimal reservaInicialConfigurada, BigDecimal reservaInicialInicializada,
            String motivoFallo, Instant creadoEn, Instant actualizadoEn) {
        this.id = id;
        this.status = status;
        this.epoch = epoch;
        this.reservaInicialConfigurada = reservaInicialConfigurada;
        this.reservaInicialInicializada = reservaInicialInicializada;
        this.motivoFallo = motivoFallo;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public Short getId() { return id; }
    public String getStatus() { return status; }
    public Long getEpoch() { return epoch; }
    public BigDecimal getReservaInicialConfigurada() { return reservaInicialConfigurada; }
    public BigDecimal getReservaInicialInicializada() { return reservaInicialInicializada; }
    public String getMotivoFallo() { return motivoFallo; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
