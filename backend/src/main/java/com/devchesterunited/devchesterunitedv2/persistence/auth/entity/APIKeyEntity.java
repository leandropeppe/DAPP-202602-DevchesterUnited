package com.devchesterunited.devchesterunitedv2.persistence.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class APIKeyEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID usuarioId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String hashClave;
    @Column(name = "key_prefix", nullable = false, length = 14)
    private String prefijo;
    @Column(name = "is_active", nullable = false)
    private boolean activa;
    @Column(name = "created_at", nullable = false)
    private Instant creadaEn;
    @Column(name = "revoked_at")
    private Instant revocadaEn;

    protected APIKeyEntity() { }

    public APIKeyEntity(UUID id, UUID usuarioId, String hashClave, String prefijo, boolean activa,
            Instant creadaEn, Instant revocadaEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.hashClave = hashClave;
        this.prefijo = prefijo;
        this.activa = activa;
        this.creadaEn = creadaEn;
        this.revocadaEn = revocadaEn;
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getHashClave() { return hashClave; }
    public String getPrefijo() { return prefijo; }
    public boolean isActiva() { return activa; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getRevocadaEn() { return revocadaEn; }
}
