package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_idempotency_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uq_market_idempotency_user_key",
                columnNames = {"user_id", "idempotency_key"}),
        @UniqueConstraint(name = "uq_market_idempotency_id_user",
                columnNames = {"id", "user_id"})
})
public class SolicitudIdempotenciaMercadoEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    private UsuarioEntity usuario;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String claveIdempotencia;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String tipoOperacion;

    @Column(name = "parameters_hash", nullable = false, length = 128)
    private String hashParametros;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "execution_token")
    private UUID tokenEjecucion;

    @Column(name = "result_reference")
    private UUID referenciaResultado;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected SolicitudIdempotenciaMercadoEntity() { }

    public SolicitudIdempotenciaMercadoEntity(UUID id, UUID usuarioId, String claveIdempotencia,
            String tipoOperacion, String hashParametros, String status, UUID tokenEjecucion,
            UUID referenciaResultado, Instant creadoEn, Instant actualizadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.claveIdempotencia = claveIdempotencia;
        this.tipoOperacion = tipoOperacion;
        this.hashParametros = hashParametros;
        this.status = status;
        this.tokenEjecucion = tokenEjecucion;
        this.referenciaResultado = referenciaResultado;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public UsuarioEntity getUsuario() { return usuario; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public String getTipoOperacion() { return tipoOperacion; }
    public String getHashParametros() { return hashParametros; }
    public String getStatus() { return status; }
    public UUID getTokenEjecucion() { return tokenEjecucion; }
    public UUID getReferenciaResultado() { return referenciaResultado; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
