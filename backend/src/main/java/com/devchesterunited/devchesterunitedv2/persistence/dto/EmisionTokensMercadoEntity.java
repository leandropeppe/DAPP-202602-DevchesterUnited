package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_token_issuances")
public class EmisionTokensMercadoEntity {
    @Id
    @Column(name = "player_id", nullable = false)
    private UUID jugadorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", insertable = false, updatable = false, nullable = false)
    private JugadorEntity jugador;

    @Column(name = "counterparty_id", nullable = false)
    private UUID contraparteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id", insertable = false, updatable = false, nullable = false)
    private ContraparteMercadoEntity contraparte;

    @Column(name = "issued_quantity", nullable = false)
    private Long cantidadEmitida;

    @Column(name = "inventory_quantity", nullable = false)
    private Long cantidadInventario;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected EmisionTokensMercadoEntity() { }

    public EmisionTokensMercadoEntity(UUID jugadorId, UUID contraparteId,
            Long cantidadEmitida, Long cantidadInventario,
            Instant creadoEn, Instant actualizadoEn) {
        this.jugadorId = jugadorId;
        this.contraparteId = contraparteId;
        this.cantidadEmitida = cantidadEmitida;
        this.cantidadInventario = cantidadInventario;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public UUID getContraparteId() { return contraparteId; }
    public ContraparteMercadoEntity getContraparte() { return contraparte; }
    public Long getCantidadEmitida() { return cantidadEmitida; }
    public Long getCantidadInventario() { return cantidadInventario; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    public void decrementarInventario(Long cantidad, Instant momento) {
        if (cantidad == null || cantidad <= 0 || momento == null) {
            throw new IllegalArgumentException("La cantidad a descontar debe ser positiva.");
        }
        if (cantidadInventario < cantidad) {
            throw new IllegalStateException("El inventario de tokens es insuficiente.");
        }
        cantidadInventario -= cantidad;
        actualizadoEn = momento;
    }
}
