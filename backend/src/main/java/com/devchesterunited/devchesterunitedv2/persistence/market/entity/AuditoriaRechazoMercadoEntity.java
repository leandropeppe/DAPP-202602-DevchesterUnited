package com.devchesterunited.devchesterunitedv2.persistence.market.entity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;

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
@Table(name = "market_rejected_attempts")
public class AuditoriaRechazoMercadoEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UsuarioEntity usuario;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String tipoOperacion;

    @Column(name = "player_id")
    private UUID jugadorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", insertable = false, updatable = false)
    private JugadorEntity jugador;

    @Column(name = "idempotency_key_hash", length = 128)
    private String hashClaveIdempotencia;

    @Column(name = "reason_code", nullable = false, length = 50)
    private String codigoMotivo;

    @Column(name = "parameters_hash", length = 128)
    private String hashParametros;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    protected AuditoriaRechazoMercadoEntity() { }

    public AuditoriaRechazoMercadoEntity(UUID id, UUID usuarioId, String tipoOperacion,
            UUID jugadorId, String hashClaveIdempotencia, String codigoMotivo,
            String hashParametros, Instant creadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipoOperacion = tipoOperacion;
        this.jugadorId = jugadorId;
        this.hashClaveIdempotencia = hashClaveIdempotencia;
        this.codigoMotivo = codigoMotivo;
        this.hashParametros = hashParametros;
        this.creadoEn = creadoEn;
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public UsuarioEntity getUsuario() { return usuario; }
    public String getTipoOperacion() { return tipoOperacion; }
    public UUID getJugadorId() { return jugadorId; }
    public JugadorEntity getJugador() { return jugador; }
    public String getHashClaveIdempotencia() { return hashClaveIdempotencia; }
    public String getCodigoMotivo() { return codigoMotivo; }
    public String getHashParametros() { return hashParametros; }
    public Instant getCreadoEn() { return creadoEn; }
}
