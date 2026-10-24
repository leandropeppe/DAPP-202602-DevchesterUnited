package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_confirmed_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_market_transaction_idempotency",
                columnNames = "idempotency_request_id")
})
public class TransaccionConfirmadaMercadoEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    private UsuarioEntity usuario;

    @Column(name = "player_id", nullable = false)
    private UUID jugadorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", insertable = false, updatable = false, nullable = false)
    private JugadorEntity jugador;

    @Column(name = "player_name_snapshot", nullable = false, length = 200)
    private String nombreJugadorSnapshot;

    @Column(name = "operation_type", nullable = false, length = 10)
    private String tipoOperacion;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "unit_price", nullable = false, precision = 20, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "total_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "counterparty_id", nullable = false)
    private UUID contraparteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id", insertable = false, updatable = false, nullable = false)
    private ContraparteMercadoEntity contraparte;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "quote_version", nullable = false)
    private Long versionCotizacion;

    @Column(name = "idempotency_request_id", nullable = false)
    private UUID solicitudIdempotenciaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idempotency_request_id", insertable = false, updatable = false,
            nullable = false)
    private SolicitudIdempotenciaMercadoEntity solicitudIdempotencia;

    @Column(name = "realized_pnl", precision = 28, scale = 8)
    private BigDecimal pnlRealizado;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmadoEn;

    protected TransaccionConfirmadaMercadoEntity() { }

    public TransaccionConfirmadaMercadoEntity(UUID id, UUID usuarioId, UUID jugadorId,
            String nombreJugadorSnapshot, String tipoOperacion, Long quantity,
            BigDecimal precioUnitario, BigDecimal montoTotal, UUID contraparteId,
            UUID quoteId, Long versionCotizacion, UUID solicitudIdempotenciaId,
            BigDecimal pnlRealizado, Instant confirmadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.jugadorId = jugadorId;
        this.nombreJugadorSnapshot = nombreJugadorSnapshot;
        this.tipoOperacion = tipoOperacion;
        this.quantity = quantity;
        this.precioUnitario = precioUnitario;
        this.montoTotal = montoTotal;
        this.contraparteId = contraparteId;
        this.quoteId = quoteId;
        this.versionCotizacion = versionCotizacion;
        this.solicitudIdempotenciaId = solicitudIdempotenciaId;
        this.pnlRealizado = pnlRealizado;
        this.confirmadoEn = confirmadoEn;
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public UsuarioEntity getUsuario() { return usuario; }
    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public String getNombreJugadorSnapshot() { return nombreJugadorSnapshot; }
    public String getTipoOperacion() { return tipoOperacion; }
    public Long getQuantity() { return quantity; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getMontoTotal() { return montoTotal; }
    public UUID getContraparteId() { return contraparteId; }
    public ContraparteMercadoEntity getContraparte() { return contraparte; }
    public UUID getQuoteId() { return quoteId; }
    public Long getVersionCotizacion() { return versionCotizacion; }
    public UUID getSolicitudIdempotenciaId() { return solicitudIdempotenciaId; }
    public SolicitudIdempotenciaMercadoEntity getSolicitudIdempotencia() { return solicitudIdempotencia; }
    public BigDecimal getPnlRealizado() { return pnlRealizado; }
    public Instant getConfirmadoEn() { return confirmadoEn; }
}
