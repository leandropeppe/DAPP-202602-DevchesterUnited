package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "market_p2p_trades")
public class TradeP2PEntity {
    public record Identidad(UUID id, UUID ofertaId, UUID vendedorId, UUID compradorId,
            UUID jugadorId) { }

    public record Liquidacion(String nombreJugadorSnapshot, Long quantity,
            BigDecimal precioUnitario, BigDecimal montoTotal) { }

    public record Confirmacion(UUID quoteId, Long versionCotizacion,
            UUID solicitudIdempotenciaId, Instant confirmadoEn) { }

    public record Snapshot(String offerStatus, Long remainingQuantity,
            Long reservedQuantity) { }
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID ofertaId;
    @Column(name = "seller_user_id", nullable = false)
    private UUID vendedorId;
    @Column(name = "buyer_user_id", nullable = false)
    private UUID compradorId;
    @Column(name = "player_id", nullable = false)
    private UUID jugadorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "offer_id", referencedColumnName = "id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "seller_user_id", referencedColumnName = "seller_user_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "player_id", referencedColumnName = "player_id",
                    insertable = false, updatable = false)
    })
    private OfertaVentaP2PEntity oferta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_user_id", insertable = false, updatable = false,
            nullable = false)
    private UsuarioEntity vendedor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_user_id", insertable = false, updatable = false,
            nullable = false)
    private UsuarioEntity comprador;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", insertable = false, updatable = false, nullable = false)
    private JugadorEntity jugador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "seller_user_id", referencedColumnName = "user_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "player_id", referencedColumnName = "player_id",
                    insertable = false, updatable = false)
    })
    private TenenciaMercadoEntity tenenciaVendedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "buyer_user_id", referencedColumnName = "user_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "player_id", referencedColumnName = "player_id",
                    insertable = false, updatable = false)
    })
    private TenenciaMercadoEntity tenenciaComprador;

    @Column(name = "player_name_snapshot", nullable = false, length = 200)
    private String nombreJugadorSnapshot;
    @Column(nullable = false)
    private Long quantity;
    @Column(name = "unit_price", nullable = false, precision = 20, scale = 2)
    private BigDecimal precioUnitario;
    @Column(name = "total_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal montoTotal;
    @Column(name = "realized_pnl", precision = 28, scale = 8)
    private BigDecimal pnlRealizado;
    @Column(name = "offer_status_after_trade", length = 20)
    private String estadoOfertaPosterior;
    @Column(name = "remaining_quantity_after_trade")
    private Long cantidadRestantePosterior;
    @Column(name = "reserved_quantity_after_trade")
    private Long cantidadReservadaPosterior;
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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "quote_id", referencedColumnName = "quote_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "quote_version", referencedColumnName = "version",
                    insertable = false, updatable = false),
            @JoinColumn(name = "player_id", referencedColumnName = "player_id",
                    insertable = false, updatable = false)
    })
    private VersionCotizacionMercadoEntity cotizacion;
    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmadoEn;

    protected TradeP2PEntity() { }

    public TradeP2PEntity(Identidad identidad, Liquidacion liquidacion,
            Confirmacion confirmacion, Snapshot snapshot) {
        this.id = identidad.id();
        this.ofertaId = identidad.ofertaId();
        this.vendedorId = identidad.vendedorId();
        this.compradorId = identidad.compradorId();
        this.jugadorId = identidad.jugadorId();
        this.nombreJugadorSnapshot = liquidacion.nombreJugadorSnapshot();
        this.quantity = liquidacion.quantity();
        this.precioUnitario = liquidacion.precioUnitario();
        this.montoTotal = liquidacion.montoTotal();
        this.quoteId = confirmacion.quoteId();
        this.versionCotizacion = confirmacion.versionCotizacion();
        this.solicitudIdempotenciaId = confirmacion.solicitudIdempotenciaId();
        this.confirmadoEn = confirmacion.confirmadoEn();
        if (snapshot != null) {
            this.estadoOfertaPosterior = snapshot.offerStatus();
            this.cantidadRestantePosterior = snapshot.remainingQuantity();
            this.cantidadReservadaPosterior = snapshot.reservedQuantity();
        }
    }

    public UUID getId() { return id; }
    public UUID getOfertaId() { return ofertaId; }
    public OfertaVentaP2PEntity getOferta() { return oferta; }
    public UUID getVendedorId() { return vendedorId; }
    public UsuarioEntity getVendedor() { return vendedor; }
    public UUID getCompradorId() { return compradorId; }
    public UsuarioEntity getComprador() { return comprador; }
    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public TenenciaMercadoEntity getTenenciaVendedor() { return tenenciaVendedor; }
    public TenenciaMercadoEntity getTenenciaComprador() { return tenenciaComprador; }
    public String getNombreJugadorSnapshot() { return nombreJugadorSnapshot; }
    public Long getQuantity() { return quantity; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getMontoTotal() { return montoTotal; }
    public BigDecimal getPnlRealizado() { return pnlRealizado; }
    public String getEstadoOfertaPosterior() { return estadoOfertaPosterior; }
    public Long getCantidadRestantePosterior() { return cantidadRestantePosterior; }
    public Long getCantidadReservadaPosterior() { return cantidadReservadaPosterior; }
    public void registrarPnlRealizado(BigDecimal pnl) {
        if (pnl == null) {
            throw new IllegalArgumentException("El PnL realizado es obligatorio.");
        }
        this.pnlRealizado = pnl.setScale(8, RoundingMode.UNNECESSARY);
    }
    public UUID getQuoteId() { return quoteId; }
    public Long getVersionCotizacion() { return versionCotizacion; }
    public UUID getSolicitudIdempotenciaId() { return solicitudIdempotenciaId; }
    public SolicitudIdempotenciaMercadoEntity getSolicitudIdempotencia() {
        return solicitudIdempotencia;
    }
    public VersionCotizacionMercadoEntity getCotizacion() { return cotizacion; }
    public Instant getConfirmadoEn() { return confirmadoEn; }
}
