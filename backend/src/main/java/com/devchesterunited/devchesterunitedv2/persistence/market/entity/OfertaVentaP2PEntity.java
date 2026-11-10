package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_p2p_offers")
public class OfertaVentaP2PEntity {
    public static final String OPEN = "OPEN";
    public static final String PARTIALLY_FILLED = "PARTIALLY_FILLED";
    public static final String FILLED = "FILLED";
    public static final String CANCELLED = "CANCELLED";

    public record Cantidades(Long original, Long restante, Long vendida, Long liberada) { }

    public record Auditoria(UUID solicitudIdempotenciaId, Instant creadoEn,
            Instant actualizadoEn) { }

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "seller_user_id", nullable = false)
    private UUID vendedorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_user_id", insertable = false, updatable = false,
            nullable = false)
    private UsuarioEntity vendedor;

    @Column(name = "player_id", nullable = false)
    private UUID jugadorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", insertable = false, updatable = false, nullable = false)
    private JugadorEntity jugador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_user_id", referencedColumnName = "user_id",
            insertable = false, updatable = false)
    @JoinColumn(name = "player_id", referencedColumnName = "player_id",
            insertable = false, updatable = false)
    private TenenciaMercadoEntity tenencia;

    @Column(name = "original_quantity", nullable = false)
    private Long cantidadOriginal;

    @Column(name = "remaining_quantity", nullable = false)
    private Long cantidadRestante;

    @Column(name = "sold_quantity", nullable = false)
    private Long cantidadVendida;

    @Column(name = "released_quantity", nullable = false)
    private Long cantidadLiberada;

    @Column(name = "status", nullable = false, length = 20)
    private String estado;

    @Column(name = "idempotency_request_id", nullable = false)
    private UUID solicitudIdempotenciaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idempotency_request_id", insertable = false, updatable = false,
            nullable = false)
    private SolicitudIdempotenciaMercadoEntity solicitudIdempotencia;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    @Version
    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected OfertaVentaP2PEntity() { }

    public OfertaVentaP2PEntity(UUID id, UUID vendedorId, UUID jugadorId,
            Cantidades cantidades, String estado, Auditoria auditoria) {
        this.id = id;
        this.vendedorId = vendedorId;
        this.jugadorId = jugadorId;
        this.cantidadOriginal = cantidades.original();
        this.cantidadRestante = cantidades.restante();
        this.cantidadVendida = cantidades.vendida();
        this.cantidadLiberada = cantidades.liberada();
        this.estado = estado;
        this.solicitudIdempotenciaId = auditoria.solicitudIdempotenciaId();
        this.creadoEn = auditoria.creadoEn();
        // Hibernate asigna la version inicial al persistir. Mantenerla nula
        // durante la construccion evita que un UUID asignado sea clasificado
        // como entidad detached por el algoritmo de estado de Hibernate.
        this.actualizadoEn = null;
        validarInvariantes();
    }

    @PrePersist
    private void inicializarVersion() {
        actualizadoEn = creadoEn;
    }

    public void actualizarCantidades(Long restante, Long vendida, Long liberada,
            String nuevoEstado, Instant momento) {
        cantidadRestante = restante;
        cantidadVendida = vendida;
        cantidadLiberada = liberada;
        estado = nuevoEstado;
        actualizadoEn = momento;
        validarInvariantes();
    }

    public UUID getId() { return id; }
    public UUID getVendedorId() { return vendedorId; }
    public UsuarioEntity getVendedor() { return vendedor; }
    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public TenenciaMercadoEntity getTenencia() { return tenencia; }
    public Long getCantidadOriginal() { return cantidadOriginal; }
    public Long getCantidadRestante() { return cantidadRestante; }
    public Long getCantidadVendida() { return cantidadVendida; }
    public Long getCantidadLiberada() { return cantidadLiberada; }
    public String getEstado() { return estado; }
    public UUID getSolicitudIdempotenciaId() { return solicitudIdempotenciaId; }
    public SolicitudIdempotenciaMercadoEntity getSolicitudIdempotencia() {
        return solicitudIdempotencia;
    }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    private void validarInvariantes() {
        validarCantidades();
        validarIdentidadYEstado();
        validarFechas();
    }

    private void validarCantidades() {
        if (cantidadOriginal == null || cantidadOriginal <= 0 || cantidadRestante == null
                || cantidadVendida == null || cantidadLiberada == null
                || cantidadRestante < 0 || cantidadVendida < 0 || cantidadLiberada < 0
                || !cantidadOriginal.equals(cantidadVendida + cantidadRestante
                + cantidadLiberada)) {
            throw new IllegalArgumentException("Las cantidades de la oferta no son válidas.");
        }
    }

    private void validarIdentidadYEstado() {
        if (vendedorId == null || jugadorId == null || id == null || estado == null
                || solicitudIdempotenciaId == null || creadoEn == null) {
            throw new IllegalArgumentException("La oferta P2P está incompleta.");
        }
    }

    private void validarFechas() {
        if (actualizadoEn != null && actualizadoEn.isBefore(creadoEn)) {
            throw new IllegalArgumentException("La actualización de la oferta es anterior a su creación.");
        }
    }
}
