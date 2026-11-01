package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import jakarta.persistence.*;

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

    private TransaccionConfirmadaMercadoEntity(Builder builder) {
        this.id = builder.id;
        this.usuarioId = builder.usuarioId;
        this.jugadorId = builder.jugadorId;
        this.nombreJugadorSnapshot = builder.nombreJugadorSnapshot;
        this.tipoOperacion = builder.tipoOperacion;
        this.quantity = builder.quantity;
        this.precioUnitario = builder.precioUnitario;
        this.montoTotal = builder.montoTotal;
        this.contraparteId = builder.contraparteId;
        this.quoteId = builder.quoteId;
        this.versionCotizacion = builder.versionCotizacion;
        this.solicitudIdempotenciaId = builder.solicitudIdempotenciaId;
        this.pnlRealizado = builder.pnlRealizado;
        this.confirmadoEn = builder.confirmadoEn;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private UUID usuarioId;
        private UUID jugadorId;
        private String nombreJugadorSnapshot;
        private String tipoOperacion;
        private Long quantity;
        private BigDecimal precioUnitario;
        private BigDecimal montoTotal;
        private UUID contraparteId;
        private UUID quoteId;
        private Long versionCotizacion;
        private UUID solicitudIdempotenciaId;
        private BigDecimal pnlRealizado;
        private Instant confirmadoEn;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder usuarioId(UUID usuarioId) { this.usuarioId = usuarioId; return this; }
        public Builder jugadorId(UUID jugadorId) { this.jugadorId = jugadorId; return this; }
        public Builder nombreJugadorSnapshot(String value) {
            this.nombreJugadorSnapshot = value;
            return this;
        }
        public Builder tipoOperacion(String tipoOperacion) {
            this.tipoOperacion = tipoOperacion;
            return this;
        }
        public Builder quantity(Long quantity) { this.quantity = quantity; return this; }
        public Builder precioUnitario(BigDecimal value) {
            this.precioUnitario = value;
            return this;
        }
        public Builder montoTotal(BigDecimal montoTotal) {
            this.montoTotal = montoTotal;
            return this;
        }
        public Builder contraparteId(UUID contraparteId) {
            this.contraparteId = contraparteId;
            return this;
        }
        public Builder quoteId(UUID quoteId) { this.quoteId = quoteId; return this; }
        public Builder versionCotizacion(Long value) {
            this.versionCotizacion = value;
            return this;
        }
        public Builder solicitudIdempotenciaId(UUID value) {
            this.solicitudIdempotenciaId = value;
            return this;
        }
        public Builder pnlRealizado(BigDecimal pnlRealizado) {
            this.pnlRealizado = pnlRealizado;
            return this;
        }
        public Builder confirmadoEn(Instant confirmadoEn) {
            this.confirmadoEn = confirmadoEn;
            return this;
        }
        public TransaccionConfirmadaMercadoEntity build() {
            return new TransaccionConfirmadaMercadoEntity(this);
        }
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
