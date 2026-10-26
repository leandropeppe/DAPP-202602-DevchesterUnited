package com.devchesterunited.devchesterunitedv2.persistence.market.entity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    public MovimientoMercadoEntity(UUID id, UUID transaccionId, String tipoActivo,
            UUID cuentaCreditosId, UUID usuarioTokenId, UUID contraparteTokenId,
            UUID jugadorId, BigDecimal variacionCantidad, Instant creadoEn) {
        this.id = id;
        this.transaccionId = transaccionId;
        this.tipoActivo = tipoActivo;
        this.cuentaCreditosId = cuentaCreditosId;
        this.usuarioTokenId = usuarioTokenId;
        this.contraparteTokenId = contraparteTokenId;
        this.jugadorId = jugadorId;
        this.variacionCantidad = variacionCantidad;
        this.creadoEn = creadoEn;
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
