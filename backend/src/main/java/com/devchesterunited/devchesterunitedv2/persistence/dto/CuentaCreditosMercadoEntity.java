package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_credit_accounts")
public class CuentaCreditosMercadoEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UsuarioEntity usuario;

    @Column(name = "counterparty_id")
    private UUID contraparteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id", insertable = false, updatable = false)
    private ContraparteMercadoEntity contraparte;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected CuentaCreditosMercadoEntity() { }

    public CuentaCreditosMercadoEntity(UUID id, UUID usuarioId, UUID contraparteId,
            BigDecimal balance, Instant creadoEn, Instant actualizadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.contraparteId = contraparteId;
        this.balance = balance;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public UsuarioEntity getUsuario() { return usuario; }
    public UUID getContraparteId() { return contraparteId; }
    public ContraparteMercadoEntity getContraparte() { return contraparte; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
