package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolución conservadora de jugadores por identidad namespaced persistida o,
 * si no existe, por equipo resuelto, nombre canónico exacto y evidencia
 * compatible. Cero, múltiples o insuficientes candidatos se rechazan; una
 * nueva identidad solo se entrega como candidata para la promoción atómica.
 */
@Service
public class IdentidadJugadorResolver {
    private final JugadorRepository jugadores;
    private final IdentidadExternaJugadorRepository identidades;
    private final IdentidadEquipoResolver equipos;

    public IdentidadJugadorResolver(JugadorRepository jugadores,
            IdentidadExternaJugadorRepository identidades, IdentidadEquipoResolver equipos) {
        this.jugadores = jugadores;
        this.identidades = identidades;
        this.equipos = equipos;
    }

    public Resultado resolver(IdentidadEstadisticaJugador hint, CompeticionEstadistica competicion) {
        var persisted = identidades.findByProveedorAndExternalId(
                ProveedorIdentidadExterna.WHOSCORED, hint.playerId());
        if (persisted.isPresent()) {
            return new Resultado(persisted.get().getJugador().getId(), null,
                    persisted.get().getJugador().getEquipoId(), null);
        }
        var equipo = equipos.resolver(hint, competicion);
        List<JugadorEntity> candidatos = jugadores.findAllByEquipoIdAndActivoTrue(equipo.equipoId()).stream()
                .filter(jugador -> IdentidadEquipoResolver.canonico(jugador.getNombre())
                        .equals(IdentidadEquipoResolver.canonico(hint.nombre())))
                .filter(jugador -> compatibleSiInformado(jugador.getPosicion(), hint.posicion()))
                .filter(jugador -> compatibleSiInformado(jugador.getNacionalidad(), hint.nacionalidad()))
                .toList();
        if (candidatos.size() != 1) {
            throw new IdentidadEstadisticaNoResueltaException("La identidad del jugador no es inequívoca.");
        }
        JugadorEntity candidato = candidatos.get(0);
        return new Resultado(candidato.getId(), equipo.identidadNueva(), equipo.equipoId(),
                new NuevaIdentidad(candidato.getId(), hint.playerId(), candidato));
    }

    private static boolean compatibleSiInformado(String local, String externo) {
        return externo == null || (local != null
                && IdentidadEquipoResolver.canonico(local).equals(IdentidadEquipoResolver.canonico(externo)));
    }

    public record Resultado(UUID jugadorId, IdentidadEquipoResolver.NuevaIdentidad equipoNueva,
            UUID equipoId,
            NuevaIdentidad identidadNueva) { }

    public record NuevaIdentidad(UUID jugadorId, long externalId, JugadorEntity jugador) { }
}
