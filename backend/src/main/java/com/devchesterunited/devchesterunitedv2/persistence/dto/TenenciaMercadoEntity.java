package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.math.RoundingMode;

@Entity
@Table(name = "market_holdings")
public class TenenciaMercadoEntity {
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

    @Column(name = "acquisition_cost", nullable = false, precision = 28, scale = 8)
    private BigDecimal costoAdquisicion;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected TenenciaMercadoEntity() { }

    public TenenciaMercadoEntity(TenenciaMercadoId id, UsuarioEntity usuario,
            JugadorEntity jugador, Long quantity, BigDecimal costoAdquisicion,
            Instant actualizadoEn) {
        this.id = id;
        this.usuario = usuario;
        this.jugador = jugador;
        this.quantity = quantity;
        this.costoAdquisicion = costoAdquisicion;
        this.actualizadoEn = actualizadoEn;
    }

    public TenenciaMercadoId getId() { return id; }
    public UsuarioEntity getUsuario() { return usuario; }
    public JugadorEntity getJugador() { return jugador; }
    public Long getQuantity() { return quantity; }
    public BigDecimal getCostoAdquisicion() { return costoAdquisicion; }
    public Instant getActualizadoEn() { return actualizadoEn; }

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
}
