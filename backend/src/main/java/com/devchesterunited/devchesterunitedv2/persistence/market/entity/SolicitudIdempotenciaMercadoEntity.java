package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import jakarta.persistence.*;

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
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_UNCERTAIN = "UNCERTAIN";
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

    private SolicitudIdempotenciaMercadoEntity(Builder builder) {
        this.id = builder.id;
        this.usuarioId = builder.usuarioId;
        this.claveIdempotencia = builder.claveIdempotencia;
        this.tipoOperacion = builder.tipoOperacion;
        this.hashParametros = builder.hashParametros;
        this.status = builder.status;
        this.tokenEjecucion = builder.tokenEjecucion;
        this.referenciaResultado = builder.referenciaResultado;
        this.creadoEn = builder.creadoEn;
        this.actualizadoEn = builder.actualizadoEn;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private UUID usuarioId;
        private String claveIdempotencia;
        private String tipoOperacion;
        private String hashParametros;
        private String status;
        private UUID tokenEjecucion;
        private UUID referenciaResultado;
        private Instant creadoEn;
        private Instant actualizadoEn;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder usuarioId(UUID usuarioId) { this.usuarioId = usuarioId; return this; }
        public Builder claveIdempotencia(String value) {
            this.claveIdempotencia = value;
            return this;
        }
        public Builder tipoOperacion(String tipoOperacion) {
            this.tipoOperacion = tipoOperacion;
            return this;
        }
        public Builder hashParametros(String hashParametros) {
            this.hashParametros = hashParametros;
            return this;
        }
        public Builder status(String status) { this.status = status; return this; }
        public Builder tokenEjecucion(UUID tokenEjecucion) {
            this.tokenEjecucion = tokenEjecucion;
            return this;
        }
        public Builder referenciaResultado(UUID value) {
            this.referenciaResultado = value;
            return this;
        }
        public Builder creadoEn(Instant creadoEn) { this.creadoEn = creadoEn; return this; }
        public Builder actualizadoEn(Instant actualizadoEn) {
            this.actualizadoEn = actualizadoEn;
            return this;
        }
        public SolicitudIdempotenciaMercadoEntity build() {
            return new SolicitudIdempotenciaMercadoEntity(this);
        }
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

    public void confirmar(UUID token, UUID resultado, Instant ahora) {
        validarTokenVigente(token);
        if (!STATUS_PROCESSING.equals(status)) {
            return;
        }
        if (resultado == null) {
            throw new IllegalArgumentException("La referencia del resultado es obligatoria.");
        }
        status = STATUS_CONFIRMED;
        referenciaResultado = resultado;
        tokenEjecucion = null;
        actualizadoEn = ahora;
    }

    public void rechazar(UUID token, Instant ahora) {
        validarTokenVigente(token);
        if (!STATUS_PROCESSING.equals(status)) {
            return;
        }
        status = STATUS_REJECTED;
        tokenEjecucion = null;
        actualizadoEn = ahora;
    }

    public void marcarIncierta(UUID token, Instant ahora) {
        validarTokenVigente(token);
        if (!STATUS_PROCESSING.equals(status)) {
            return;
        }
        status = STATUS_UNCERTAIN;
        tokenEjecucion = null;
        actualizadoEn = ahora;
    }

    public void reclamarNuevamente(UUID nuevoToken, Instant ahora) {
        if (!STATUS_PROCESSING.equals(status)) {
            throw new IllegalStateException("Solo una solicitud PROCESSING puede reclamarse.");
        }
        if (nuevoToken == null) {
            throw new IllegalArgumentException("El token de ejecucion es obligatorio.");
        }
        tokenEjecucion = nuevoToken;
        actualizadoEn = ahora;
    }

    private void validarTokenVigente(UUID token) {
        if (!STATUS_PROCESSING.equals(status) || token == null || !token.equals(tokenEjecucion)) {
            throw new IllegalStateException("El token de ejecucion ya no es vigente.");
        }
    }
}
