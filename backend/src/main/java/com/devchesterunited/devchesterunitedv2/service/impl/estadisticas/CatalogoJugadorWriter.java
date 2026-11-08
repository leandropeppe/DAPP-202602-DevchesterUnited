package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.mapper.JugadorMapper;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class CatalogoJugadorWriter {
    private final JugadorRepository jugadores;
    private final IdentidadExternaJugadorRepository identidades;
    private final JugadorMapper jugadorMapper;

    public CatalogoJugadorWriter(JugadorRepository jugadores,
            IdentidadExternaJugadorRepository identidades, JugadorMapper jugadorMapper) {
        this.jugadores = jugadores;
        this.identidades = identidades;
        this.jugadorMapper = jugadorMapper;
    }

    public Resultado escribir(CatalogoSincronizado snapshot,
            Map<UUID, UUID> equiposPersistidos, Instant ahora) {
        Map<Long, IdentidadExternaJugadorEntity> identidadesPorExternalId = cargarIdentidades(snapshot);
        Set<UUID> jugadoresSnapshot = new HashSet<>();
        List<UUID> jugadoresActivados = new ArrayList<>();
        int creados = 0;

        for (Jugador jugador : snapshot.jugadores()) {
            long externalId = snapshot.getExternalId(jugador.getId());
            IdentidadExternaJugadorEntity identidadExistente = identidadesPorExternalId.get(externalId);
            JugadorEntity anterior = identidadExistente == null ? null : identidadExistente.getJugador();
            boolean activacion = anterior == null || !anterior.isActivo();
            if (anterior == null) {
                creados++;
            }

            Equipo equipo = equipo(snapshot, jugador.getEquipoId());
            UUID equipoIdPersistido = equiposPersistidos.get(equipo.id());
            if (equipoIdPersistido == null) {
                throw new IllegalStateException("No se pudo resolver el equipo local del jugador.");
            }
            JugadorEntity persistido = jugadores.save(
                    jugadorMapper.toEntity(jugador, equipoIdPersistido, anterior, ahora));
            guardarIdentidad(persistido, externalId, identidadExistente);
            jugadoresSnapshot.add(persistido.getId());
            if (activacion && persistido.isActivo()) {
                jugadoresActivados.add(persistido.getId());
            }
        }

        return new Resultado(jugadoresSnapshot, jugadoresActivados, creados);
    }

    public void flush() {
        jugadores.flush();
    }

    public Set<UUID> obtenerJugadoresConIdentidadFootballData() {
        Set<UUID> resultado = new HashSet<>();
        for (IdentidadExternaJugadorEntity identidad : identidades
                .findAllByProveedor(ProveedorIdentidadExterna.FOOTBALL_DATA)) {
            resultado.add(identidad.getJugador().getId());
        }
        return resultado;
    }

    public List<JugadorEntity> obtenerTodos() {
        return jugadores.findAll();
    }

    public void inactivar(JugadorEntity jugador, Instant ahora) {
        jugadores.save(jugadorMapper.toInactiveEntity(jugador, ahora));
    }

    private Map<Long, IdentidadExternaJugadorEntity> cargarIdentidades(
            CatalogoSincronizado snapshot) {
        List<Long> externalIds = snapshot.jugadores().stream()
                .map(jugador -> snapshot.getExternalId(jugador.getId())).toList();
        Map<Long, IdentidadExternaJugadorEntity> resultado = new HashMap<>();
        for (IdentidadExternaJugadorEntity identidad : identidades
                .findAllByProveedorAndExternalIdIn(ProveedorIdentidadExterna.FOOTBALL_DATA, externalIds)) {
            resultado.put(identidad.getExternalId(), identidad);
        }
        return resultado;
    }

    private void guardarIdentidad(JugadorEntity jugador, long externalId,
            IdentidadExternaJugadorEntity identidadExistente) {
        if (identidadExistente == null) {
            identidades.save(new IdentidadExternaJugadorEntity(
                    UUID.randomUUID(), jugador, ProveedorIdentidadExterna.FOOTBALL_DATA, externalId));
        }
    }

    private static Equipo equipo(CatalogoSincronizado snapshot, UUID id) {
        return snapshot.equipos().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    public record Resultado(Set<UUID> jugadoresSnapshot, List<UUID> jugadoresActivados, int creados) {
        public Resultado {
            jugadoresSnapshot = Set.copyOf(jugadoresSnapshot);
            jugadoresActivados = List.copyOf(jugadoresActivados);
        }
    }
}
