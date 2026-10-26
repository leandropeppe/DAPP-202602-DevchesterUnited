package com.devchesterunited.devchesterunitedv2.persistence.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class TenenciaMercadoId implements Serializable {
    @Column(name = "user_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "player_id", nullable = false)
    private UUID jugadorId;

    protected TenenciaMercadoId() { }

    public TenenciaMercadoId(UUID usuarioId, UUID jugadorId) {
        this.usuarioId = usuarioId;
        this.jugadorId = jugadorId;
    }

    public UUID getUsuarioId() { return usuarioId; }
    public UUID getJugadorId() { return jugadorId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TenenciaMercadoId that)) return false;
        return Objects.equals(usuarioId, that.usuarioId)
                && Objects.equals(jugadorId, that.jugadorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuarioId, jugadorId);
    }
}
