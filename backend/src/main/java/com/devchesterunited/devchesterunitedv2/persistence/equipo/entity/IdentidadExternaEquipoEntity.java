package com.devchesterunited.devchesterunitedv2.persistence.equipo.entity;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "identidades_externas_equipo", uniqueConstraints = {
        @UniqueConstraint(name = "ux_identidades_equipo_proveedor", columnNames = {"equipo_id", "proveedor"}),
        @UniqueConstraint(name = "ux_identidades_equipo_proveedor_external_id",
                columnNames = {"proveedor", "external_id"})
})
public class IdentidadExternaEquipoEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_id", nullable = false)
    private EquipoEntity equipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProveedorIdentidadExterna proveedor;

    @Column(name = "external_id", nullable = false)
    private Long externalId;

    protected IdentidadExternaEquipoEntity() { }

    public IdentidadExternaEquipoEntity(UUID id, EquipoEntity equipo,
            ProveedorIdentidadExterna proveedor, Long externalId) {
        this.id = id;
        this.equipo = equipo;
        this.proveedor = proveedor;
        this.externalId = externalId;
    }

    public UUID getId() { return id; }
    public EquipoEntity getEquipo() { return equipo; }
    public ProveedorIdentidadExterna getProveedor() { return proveedor; }
    public Long getExternalId() { return externalId; }
}
