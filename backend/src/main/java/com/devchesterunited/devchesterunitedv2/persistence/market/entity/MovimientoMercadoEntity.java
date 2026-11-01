package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_movements")
public class MovimientoMercadoEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transaccionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false, nullable = false)
    private TransaccionConfirmadaMercadoEntity transaccion;

    @Column(name = "asset_type", nullable = false, length = 10)
    private String tipoActivo;

    @Column(name = "credit_account_id")
    private UUID cuentaCreditosId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_account_id", insertable = false, updatable = false)
    private CuentaCreditosMercadoEntity cuentaCreditos;

    @Column(name = "token_user_id")
    private UUID usuarioTokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_user_id", insertable = false, updatable = false)
    private UsuarioEntity usuarioToken;

    @Column(name = "token_counterparty_id")
    private UUID contraparteTokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_counterparty_id", insertable = false, updatable = false)
    private ContraparteMercadoEntity contraparteToken;

    @Column(name = "player_id")
    private UUID jugadorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", insertable = false, updatable = false)
    private JugadorEntity jugador;

    @Column(name = "quantity_delta", nullable = false, precision = 28, scale = 8)
    private BigDecimal variacionCantidad;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    protected MovimientoMercadoEntity() { }

    private MovimientoMercadoEntity(Builder builder) {
        this.id = builder.id;
        this.transaccionId = builder.transaccionId;
        this.tipoActivo = builder.tipoActivo;
        this.cuentaCreditosId = builder.cuentaCreditosId;
        this.usuarioTokenId = builder.usuarioTokenId;
        this.contraparteTokenId = builder.contraparteTokenId;
        this.jugadorId = builder.jugadorId;
        this.variacionCantidad = builder.variacionCantidad;
        this.creadoEn = builder.creadoEn;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private UUID transaccionId;
        private String tipoActivo;
        private UUID cuentaCreditosId;
        private UUID usuarioTokenId;
        private UUID contraparteTokenId;
        private UUID jugadorId;
        private BigDecimal variacionCantidad;
        private Instant creadoEn;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder transaccionId(UUID transaccionId) {
            this.transaccionId = transaccionId;
            return this;
        }
        public Builder tipoActivo(String tipoActivo) { this.tipoActivo = tipoActivo; return this; }
        public Builder cuentaCreditosId(UUID value) {
            this.cuentaCreditosId = value;
            return this;
        }
        public Builder usuarioTokenId(UUID value) {
            this.usuarioTokenId = value;
            return this;
        }
        public Builder contraparteTokenId(UUID value) {
            this.contraparteTokenId = value;
            return this;
        }
        public Builder jugadorId(UUID jugadorId) { this.jugadorId = jugadorId; return this; }
        public Builder variacionCantidad(BigDecimal value) {
            this.variacionCantidad = value;
            return this;
        }
        public Builder creadoEn(Instant creadoEn) { this.creadoEn = creadoEn; return this; }
        public MovimientoMercadoEntity build() { return new MovimientoMercadoEntity(this); }
    }

    public UUID getId() { return id; }
    public UUID getTransaccionId() { return transaccionId; }
    public TransaccionConfirmadaMercadoEntity getTransaccion() { return transaccion; }
    public String getTipoActivo() { return tipoActivo; }
    public UUID getCuentaCreditosId() { return cuentaCreditosId; }
    public CuentaCreditosMercadoEntity getCuentaCreditos() { return cuentaCreditos; }
    public UUID getUsuarioTokenId() { return usuarioTokenId; }
    public UsuarioEntity getUsuarioToken() { return usuarioToken; }
    public UUID getContraparteTokenId() { return contraparteTokenId; }
    public ContraparteMercadoEntity getContraparteToken() { return contraparteToken; }
    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public BigDecimal getVariacionCantidad() { return variacionCantidad; }
    public Instant getCreadoEn() { return creadoEn; }
}
