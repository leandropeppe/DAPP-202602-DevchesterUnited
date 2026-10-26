package com.devchesterunited.devchesterunitedv2.persistence.jugador.entity;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "identidades_externas_jugador", uniqueConstraints = {
        @UniqueConstraint(name = "ux_identidades_jugador_proveedor", columnNames = {"jugador_id", "proveedor"}),
        @UniqueConstraint(name = "ux_identidades_proveedor_external_id", columnNames = {"proveedor", "external_id"})
})
public class IdentidadExternaJugadorEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id", nullable = false)
    private JugadorEntity jugador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProveedorIdentidadExterna proveedor;

    @Column(name = "external_id", nullable = false)
    private Long externalId;

    protected IdentidadExternaJugadorEntity() { }

    public IdentidadExternaJugadorEntity(UUID id, JugadorEntity jugador,
            ProveedorIdentidadExterna proveedor, Long externalId) {
        this.id = id;
        this.jugador = jugador;
        this.proveedor = proveedor;
        this.externalId = externalId;
    }

    public UUID getId() { return id; }
    public JugadorEntity getJugador() { return jugador; }
    public ProveedorIdentidadExterna getProveedor() { return proveedor; }
    public Long getExternalId() { return externalId; }
}
