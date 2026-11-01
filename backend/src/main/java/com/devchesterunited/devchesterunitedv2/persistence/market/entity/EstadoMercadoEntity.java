package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_state")
public class EstadoMercadoEntity {
    private static final String INITIALIZING_STATUS = "INITIALIZING";

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

    private EstadoMercadoEntity(Builder builder) {
        this.id = builder.id;
        this.status = builder.status;
        this.epoch = builder.epoch;
        this.reservaInicialConfigurada = builder.reservaInicialConfigurada;
        this.reservaInicialInicializada = builder.reservaInicialInicializada;
        this.motivoFallo = builder.motivoFallo;
        this.creadoEn = builder.creadoEn;
        this.actualizadoEn = builder.actualizadoEn;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Short id;
        private String status;
        private Long epoch;
        private BigDecimal reservaInicialConfigurada;
        private BigDecimal reservaInicialInicializada;
        private String motivoFallo;
        private Instant creadoEn;
        private Instant actualizadoEn;

        public Builder id(Short id) { this.id = id; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder epoch(Long epoch) { this.epoch = epoch; return this; }
        public Builder reservaInicialConfigurada(BigDecimal value) {
            this.reservaInicialConfigurada = value;
            return this;
        }
        public Builder reservaInicialInicializada(BigDecimal value) {
            this.reservaInicialInicializada = value;
            return this;
        }
        public Builder motivoFallo(String motivoFallo) {
            this.motivoFallo = motivoFallo;
            return this;
        }
        public Builder creadoEn(Instant creadoEn) { this.creadoEn = creadoEn; return this; }
        public Builder actualizadoEn(Instant actualizadoEn) {
            this.actualizadoEn = actualizadoEn;
            return this;
        }
        public EstadoMercadoEntity build() { return new EstadoMercadoEntity(this); }
    }

    public Short getId() { return id; }
    public String getStatus() { return status; }
    public Long getEpoch() { return epoch; }
    public BigDecimal getReservaInicialConfigurada() { return reservaInicialConfigurada; }
    public BigDecimal getReservaInicialInicializada() { return reservaInicialInicializada; }
    public String getMotivoFallo() { return motivoFallo; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    /**
     * Persiste la reserva efectiva una sola vez, sin cambiar el estado ni la época.
     */
    public void inicializarReserva(BigDecimal reservaEfectiva, Instant momento) {
        if (reservaInicialInicializada != null) {
            return;
        }
        if (reservaInicialConfigurada == null) {
            reservaInicialConfigurada = reservaEfectiva;
        }
        reservaInicialInicializada = reservaEfectiva;
        actualizadoEn = momento;
    }

    public void pasarAInitializing(Instant momento) {
        if (!"UNINITIALIZED".equals(status) && !"FAILED".equals(status)) {
            throw new IllegalStateException(
                    "Solo UNINITIALIZED o FAILED pueden pasar a INITIALIZING.");
        }
        status = INITIALIZING_STATUS;
        epoch++;
        motivoFallo = null;
        actualizadoEn = momento;
    }

    public void marcarFailed(String motivo, Instant momento) {
        if (!INITIALIZING_STATUS.equals(status)) {
            throw new IllegalStateException(
                    "Solo INITIALIZING puede pasar a FAILED.");
        }
        status = "FAILED";
        epoch++;
        motivoFallo = motivo;
        actualizadoEn = momento;
    }

    public void marcarReady(Instant momento) {
        if (!INITIALIZING_STATUS.equals(status)) {
            throw new IllegalStateException(
                    "Solo INITIALIZING puede pasar a READY.");
        }
        status = "READY";
        epoch++;
        motivoFallo = null;
        actualizadoEn = momento;
    }
}
