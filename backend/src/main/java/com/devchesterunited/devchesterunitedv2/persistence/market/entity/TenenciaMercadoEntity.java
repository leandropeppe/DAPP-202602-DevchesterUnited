package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "market_holdings")
public class TenenciaMercadoEntity {
    public record Estado(Long quantity, Long cantidadReservada,
            BigDecimal costoAdquisicion, Instant actualizadoEn) { }
    @EmbeddedId
    private TenenciaMercadoId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("usuarioId")
    @JoinColumn(name = "user_id", nullable = false)
    private UsuarioEntity usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("jugadorId")
    @JoinColumn(name = "player_id", nullable = false)
    private JugadorEntity jugador;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Long cantidadReservada;

    @Column(name = "acquisition_cost", nullable = false, precision = 28, scale = 8)
    private BigDecimal costoAdquisicion;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected TenenciaMercadoEntity() { }

    public TenenciaMercadoEntity(TenenciaMercadoId id, UsuarioEntity usuario,
            JugadorEntity jugador, Long quantity, BigDecimal costoAdquisicion,
            Instant actualizadoEn) {
        this(id, usuario, jugador, new Estado(quantity, 0L, costoAdquisicion, actualizadoEn));
    }

    public TenenciaMercadoEntity(TenenciaMercadoId id, UsuarioEntity usuario,
            JugadorEntity jugador, Estado estado) {
        this.id = id;
        this.usuario = usuario;
        this.jugador = jugador;
        this.quantity = estado.quantity();
        this.cantidadReservada = estado.cantidadReservada();
        this.costoAdquisicion = estado.costoAdquisicion();
        this.actualizadoEn = estado.actualizadoEn();
        validarEstadoInicial();
    }

    public TenenciaMercadoId getId() { return id; }
    public UsuarioEntity getUsuario() { return usuario; }
    public JugadorEntity getJugador() { return jugador; }
    public Long getQuantity() { return quantity; }
    public Long getCantidadReservada() { return cantidadReservada; }
    public Long getCantidadDisponible() {
        return Math.subtractExact(quantity, cantidadReservada);
    }
    public BigDecimal getCostoAdquisicion() { return costoAdquisicion; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    public void reservar(Long cantidad, Instant momento) {
        validarCantidadReserva(cantidad, momento);
        if (cantidad > getCantidadDisponible()) {
            throw new IllegalStateException("La tenencia no tiene cantidad disponible.");
        }
        cantidadReservada = Math.addExact(cantidadReservada, cantidad);
        actualizadoEn = momento;
    }

    public void liberarReserva(Long cantidad, Instant momento) {
        validarCantidadReserva(cantidad, momento);
        if (cantidad > cantidadReservada) {
            throw new IllegalStateException("La tenencia no tiene suficiente reserva.");
        }
        cantidadReservada -= cantidad;
        actualizadoEn = momento;
    }

    public void consumirReserva(Long cantidad, Instant momento) {
        consumirReserva(cantidad, null, momento);
    }

    /** Consume una reserva y, cuando existe importe, distribuye el costo vendido. */
    public BigDecimal consumirReserva(Long cantidad, BigDecimal importeVenta, Instant momento) {
        validarCantidadReserva(cantidad, momento);
        if (cantidad > cantidadReservada || cantidad > quantity) {
            throw new IllegalStateException("La tenencia no tiene suficiente reserva.");
        }
        BigDecimal costoVendido = costoProporcional(cantidad);
        BigDecimal pnl = importeVenta == null ? BigDecimal.ZERO.setScale(8)
                : importeVenta.setScale(8, RoundingMode.UNNECESSARY).subtract(costoVendido)
                .setScale(8, RoundingMode.UNNECESSARY);
        cantidadReservada -= cantidad;
        quantity -= cantidad;
        costoAdquisicion = quantity == 0 ? BigDecimal.ZERO.setScale(8)
                : costoAdquisicion.subtract(costoVendido).setScale(8, RoundingMode.UNNECESSARY);
        actualizadoEn = momento;
        return pnl;
    }

    /** Adds a purchase to the stored total acquisition cost, not to an average. */
    public void aplicarCompra(Long cantidadComprada, BigDecimal costoCompra, Instant momento) {
        if (cantidadComprada == null || cantidadComprada <= 0 || costoCompra == null
                || costoCompra.signum() <= 0 || momento == null) {
            throw new IllegalArgumentException("La compra de la tenencia no es válida.");
        }
        long nuevaCantidad;
        try {
            nuevaCantidad = Math.addExact(quantity, cantidadComprada);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("La cantidad de la tenencia excede el límite.",
                    exception);
        }
        BigDecimal nuevoCosto = costoAdquisicion.add(costoCompra)
                .setScale(8, RoundingMode.UNNECESSARY);
        quantity = nuevaCantidad;
        costoAdquisicion = nuevoCosto;
        actualizadoEn = momento;
    }

    /**
     * Reduces a holding using its proportional acquisition cost and returns
     * the realized P/L for the sale. The stored cost has eight decimals and
     * proportional allocations use deterministic half-even rounding.
     */
    public BigDecimal aplicarVenta(Long cantidadVendida, BigDecimal importeVenta,
            Instant momento) {
        if (cantidadVendida == null || cantidadVendida <= 0 || importeVenta == null
                || importeVenta.signum() <= 0 || momento == null) {
            throw new IllegalArgumentException("La venta de la tenencia no es vÃ¡lida.");
        }
        if (quantity == null || quantity <= 0 || cantidadVendida > getCantidadDisponible()) {
            throw new IllegalStateException("La tenencia no alcanza para la venta.");
        }

        BigDecimal costoVendido;
        BigDecimal costoRestante;
        if (cantidadVendida.equals(quantity)) {
            costoVendido = costoAdquisicion;
            costoRestante = BigDecimal.ZERO.setScale(8);
        } else {
            costoVendido = costoAdquisicion.multiply(BigDecimal.valueOf(cantidadVendida))
                    .divide(BigDecimal.valueOf(quantity), 8, RoundingMode.HALF_EVEN)
                    .setScale(8, RoundingMode.UNNECESSARY);
            costoRestante = costoAdquisicion.subtract(costoVendido);
        }

        BigDecimal pnl = importeVenta.setScale(8, RoundingMode.UNNECESSARY)
                .subtract(costoVendido).setScale(8, RoundingMode.UNNECESSARY);
        quantity -= cantidadVendida;
        costoAdquisicion = quantity == 0 ? BigDecimal.ZERO.setScale(8) : costoRestante;
        actualizadoEn = momento;
        return pnl;
    }

    private void validarCantidadReserva(Long cantidad, Instant momento) {
        if (cantidad == null || cantidad <= 0 || momento == null) {
            throw new IllegalArgumentException("La cantidad de reserva no es válida.");
        }
        if (quantity == null || cantidadReservada == null || cantidadReservada < 0
                || cantidadReservada > quantity) {
            throw new IllegalStateException("La reserva de la tenencia no es válida.");
        }
    }

    private BigDecimal costoProporcional(Long cantidad) {
        if (cantidad.equals(quantity)) {
            return costoAdquisicion;
        }
        return costoAdquisicion.multiply(BigDecimal.valueOf(cantidad))
                .divide(BigDecimal.valueOf(quantity), 8, RoundingMode.HALF_EVEN)
                .setScale(8, RoundingMode.UNNECESSARY);
    }

    private void validarEstadoInicial() {
        if (quantity == null || quantity < 0 || cantidadReservada == null
                || cantidadReservada < 0 || cantidadReservada > quantity
                || costoAdquisicion == null || costoAdquisicion.signum() < 0
                || actualizadoEn == null) {
            throw new IllegalArgumentException("El estado inicial de la tenencia no es válido.");
        }
    }
}
