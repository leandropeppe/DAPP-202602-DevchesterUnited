package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "market_p2p_offer_cancellations")
public class AuditoriaCancelacionOfertaP2PEntity {
    public record Identidad(UUID id, UUID ofertaId, UUID actorId,
            UUID solicitudIdempotenciaId) { }

    public record Cantidades(Long original, Long vendida, Long liberada) { }
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "offer_id", nullable = false)
    private UUID ofertaId;
    @Column(name = "actor_user_id", nullable = false)
    private UUID actorId;
    @Column(name = "original_quantity", nullable = false)
    private Long cantidadOriginal;
    @Column(name = "sold_quantity", nullable = false)
    private Long cantidadVendida;
    @Column(name = "released_quantity", nullable = false)
    private Long cantidadLiberada;
    @Column(name = "idempotency_request_id", nullable = false)
    private UUID solicitudIdempotenciaId;
    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private Instant canceladaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "offer_id", referencedColumnName = "id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "actor_user_id", referencedColumnName = "seller_user_id",
                    insertable = false, updatable = false)
    })
    private OfertaVentaP2PEntity oferta;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", insertable = false, updatable = false,
            nullable = false)
    private UsuarioEntity actor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idempotency_request_id", insertable = false, updatable = false,
            nullable = false)
    private SolicitudIdempotenciaMercadoEntity solicitudIdempotencia;

    protected AuditoriaCancelacionOfertaP2PEntity() { }

    public AuditoriaCancelacionOfertaP2PEntity(Identidad identidad,
            Cantidades cantidades, Instant canceladaEn) {
        this.id = identidad.id();
        this.ofertaId = identidad.ofertaId();
        this.actorId = identidad.actorId();
        this.cantidadOriginal = cantidades.original();
        this.cantidadVendida = cantidades.vendida();
        this.cantidadLiberada = cantidades.liberada();
        this.solicitudIdempotenciaId = identidad.solicitudIdempotenciaId();
        this.canceladaEn = canceladaEn;
        validarInvariantes();
    }

    public UUID getId() { return id; }
    public UUID getOfertaId() { return ofertaId; }
    public OfertaVentaP2PEntity getOferta() { return oferta; }
    public UUID getActorId() { return actorId; }
    public UsuarioEntity getActor() { return actor; }
    public Long getCantidadOriginal() { return cantidadOriginal; }
    public Long getCantidadVendida() { return cantidadVendida; }
    public Long getCantidadLiberada() { return cantidadLiberada; }
    public UUID getSolicitudIdempotenciaId() { return solicitudIdempotenciaId; }
    public SolicitudIdempotenciaMercadoEntity getSolicitudIdempotencia() {
        return solicitudIdempotencia;
    }
    public Instant getCanceladaEn() { return canceladaEn; }

    private void validarInvariantes() {
        if (cantidadOriginal == null || cantidadOriginal <= 0 || cantidadVendida == null
                || cantidadVendida < 0 || cantidadLiberada == null || cantidadLiberada <= 0
                || !cantidadOriginal.equals(cantidadVendida + cantidadLiberada)) {
            throw new IllegalArgumentException("Las cantidades de cancelación no son válidas.");
        }
        if (id == null || ofertaId == null || actorId == null || solicitudIdempotenciaId == null
                || canceladaEn == null) {
            throw new IllegalArgumentException("La auditoría de cancelación está incompleta.");
        }
    }
}
