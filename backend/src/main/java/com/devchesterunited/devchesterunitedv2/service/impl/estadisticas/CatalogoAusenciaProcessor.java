package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CatalogoAusenciaProcessor {
    private final CatalogoEquipoWriter equipoWriter;
    private final CatalogoJugadorWriter jugadorWriter;

    public CatalogoAusenciaProcessor(CatalogoEquipoWriter equipoWriter,
            CatalogoJugadorWriter jugadorWriter) {
        this.equipoWriter = equipoWriter;
        this.jugadorWriter = jugadorWriter;
    }

    public void procesar(CatalogoSincronizado snapshot, Set<UUID> jugadoresPresentes,
            Instant ahora, boolean primerSnapshot,
            List<IdentidadExternaEquipoEntity> identidadesFootballData) {
        Set<Long> equiposPresentes = snapshot.identidadesExternasEquipos().stream()
                .map(IdentidadExternaEquipo::externalId).collect(Collectors.toSet());
        procesarEquipos(equiposPresentes, ahora, identidadesFootballData);
        Set<UUID> jugadoresConIdentidad = jugadorWriter.obtenerJugadoresConIdentidadFootballData();
        procesarJugadores(jugadoresConIdentidad, jugadoresPresentes, ahora, primerSnapshot);
    }

    private void procesarEquipos(Set<Long> equiposPresentes, Instant ahora,
            List<IdentidadExternaEquipoEntity> identidadesFootballData) {
        for (IdentidadExternaEquipoEntity identidad : identidadesFootballData) {
            EquipoEntity equipo = identidad.getEquipo();
            if (debeInactivarEquipo(identidad, equiposPresentes, equipo)) {
                equipoWriter.inactivar(equipo, ahora);
            }
        }
    }

    private boolean debeInactivarEquipo(IdentidadExternaEquipoEntity identidad,
            Set<Long> equiposPresentes, EquipoEntity equipo) {
        return !equiposPresentes.contains(identidad.getExternalId()) && equipo.isActivo();
    }

    private void procesarJugadores(Set<UUID> jugadoresConIdentidad,
            Set<UUID> jugadoresPresentes, Instant ahora, boolean primerSnapshot) {
        for (JugadorEntity jugador : jugadorWriter.obtenerTodos()) {
            if (debeInactivarJugador(jugador, jugadoresConIdentidad, jugadoresPresentes, primerSnapshot)) {
                jugadorWriter.inactivar(jugador, ahora);
            }
        }
    }

    private boolean debeInactivarJugador(JugadorEntity jugador,
            Set<UUID> jugadoresConIdentidad, Set<UUID> jugadoresPresentes,
            boolean primerSnapshot) {
        boolean tieneIdentidad = jugadoresConIdentidad.contains(jugador.getId());
        return (!tieneIdentidad && primerSnapshot)
                || (tieneIdentidad && !jugadoresPresentes.contains(jugador.getId()));
    }
}
