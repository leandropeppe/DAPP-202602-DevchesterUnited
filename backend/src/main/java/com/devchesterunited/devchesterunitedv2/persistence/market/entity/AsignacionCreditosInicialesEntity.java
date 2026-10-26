package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_initial_credit_grants", uniqueConstraints = {
        @UniqueConstraint(name = "uq_market_initial_grant_user_type",
                columnNames = {"user_id", "grant_type"})
})
public class AsignacionCreditosInicialesEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    private UsuarioEntity usuario;

    @Column(name = "credit_account_id", nullable = false)
    private UUID cuentaCreditosId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_account_id", insertable = false, updatable = false, nullable = false)
    private CuentaCreditosMercadoEntity cuentaCreditos;

    @Column(name = "grant_type", nullable = false, length = 30)
    private String tipoAsignacion;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    protected AsignacionCreditosInicialesEntity() { }

    public AsignacionCreditosInicialesEntity(UUID id, UUID usuarioId, UUID cuentaCreditosId,
            String tipoAsignacion, BigDecimal amount, Instant creadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.cuentaCreditosId = cuentaCreditosId;
        this.tipoAsignacion = tipoAsignacion;
        this.amount = amount;
        this.creadoEn = creadoEn;
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public UsuarioEntity getUsuario() { return usuario; }
    public UUID getCuentaCreditosId() { return cuentaCreditosId; }
    public CuentaCreditosMercadoEntity getCuentaCreditos() { return cuentaCreditos; }
    public String getTipoAsignacion() { return tipoAsignacion; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreadoEn() { return creadoEn; }
}
