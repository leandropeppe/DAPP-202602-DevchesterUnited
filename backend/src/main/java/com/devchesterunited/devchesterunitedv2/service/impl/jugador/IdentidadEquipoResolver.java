package com.devchesterunited.devchesterunitedv2.service.impl.jugador;

import com.devchesterunited.devchesterunitedv2.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Resuelve el equipo del flujo normal exclusivamente por identidad persistida. */
@Service
public class IdentidadEquipoResolver {
    private final IdentidadExternaEquipoRepository identidades;

    public IdentidadEquipoResolver(IdentidadExternaEquipoRepository identidades) {
        this.identidades = identidades;
    }

    public Resultado resolver(Long teamId, String nombreEquipo, CompeticionEstadistica competicion) {
        var persisted = teamId == null ? java.util.Optional.<com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity>empty()
                : identidades.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, teamId);
        if (persisted.isPresent() && persisted.get().getEquipo().isActivo()) {
            EquipoEntity equipo = persisted.get().getEquipo();
            return new Resultado(equipo.getId(), null, equipo.getNombre(), equipo);
        }
        var motivo = persisted.isPresent()
                ? IdentidadEstadisticaNoResueltaException.Motivo.EQUIPO_INACTIVO
                : IdentidadEstadisticaNoResueltaException.Motivo.SIN_CANDIDATOS;
        throw new IdentidadEstadisticaNoResueltaException(
                new IdentidadEstadisticaNoResueltaException.Diagnostico(
                        IdentidadEstadisticaNoResueltaException.Tipo.EQUIPO, "WHOSCORED", teamId,
                        nombreEquipo, competicion.name(), nombreEquipo, null, null, null, motivo));
    }

    public record Resultado(UUID equipoId, NuevaIdentidad identidadNueva, String nombreEquipoLocal,
            EquipoEntity equipo) {
        public Resultado(UUID equipoId, NuevaIdentidad identidadNueva) {
            this(equipoId, identidadNueva, null, null);
        }
    }

    /** Se conserva para el contrato de promoción histórica; el flujo normal no la produce. */
    public record NuevaIdentidad(UUID equipoId, long externalId, EquipoEntity equipo) { }
}
