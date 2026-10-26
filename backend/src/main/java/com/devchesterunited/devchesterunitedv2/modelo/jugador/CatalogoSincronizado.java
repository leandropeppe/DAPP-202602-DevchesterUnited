package com.devchesterunited.devchesterunitedv2.modelo.jugador;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Snapshot completo validado antes de cualquier mutacion de persistencia. */
public record CatalogoSincronizado(
        List<Equipo> equipos,
        List<Jugador> jugadores,
        Map<UUID, Long> identidadesFootballData,
        List<IdentidadExternaEquipo> identidadesExternasEquipos) {

    private static final Map<String, Integer> CANTIDAD_POR_LIGA = Map.of(
            "Premier League", 20, "LaLiga", 20, "Bundesliga", 18, "Serie A", 20, "Ligue 1", 18);

    public CatalogoSincronizado {
        equipos = List.copyOf(Objects.requireNonNull(equipos));
        jugadores = List.copyOf(Objects.requireNonNull(jugadores));
        identidadesFootballData = Map.copyOf(Objects.requireNonNull(identidadesFootballData));
        identidadesExternasEquipos = List.copyOf(
                Objects.requireNonNull(identidadesExternasEquipos));
        validar(equipos, jugadores, identidadesFootballData, identidadesExternasEquipos);
    }

    private static void validar(List<Equipo> equipos, List<Jugador> jugadores,
            Map<UUID, Long> identidadesFootballData,
            List<IdentidadExternaEquipo> identidadesExternasEquipos) {
        validarTamanosYDistribucion(equipos, jugadores, identidadesExternasEquipos);
        validarIdentidadesEquipos(equipos, identidadesExternasEquipos);
        validarIdentidadesJugadores(jugadores, identidadesFootballData);
        validarReferenciasEquipo(equipos, jugadores);
    }

    private static void validarTamanosYDistribucion(List<Equipo> equipos, List<Jugador> jugadores,
            List<IdentidadExternaEquipo> identidadesExternasEquipos) {
        if (equipos.size() != 96) throw new IllegalArgumentException("El snapshot debe contener 96 equipos.");
        if (jugadores.isEmpty() || jugadores.size() > 10_000) {
            throw new IllegalArgumentException("El snapshot debe contener entre 1 y 10000 jugadores.");
        }
        if (equipos.stream().anyMatch(e -> !e.isActivo()
                || identidadesExternasEquipos.stream().noneMatch(i -> i.equipoId().equals(e.id()))
                || !CANTIDAD_POR_LIGA.containsKey(e.liga()))) {
            throw new IllegalArgumentException("El snapshot contiene una liga no soportada.");
        }
        CANTIDAD_POR_LIGA.forEach((liga, cantidad) -> {
            long encontrados = equipos.stream().filter(e -> liga.equals(e.liga())).count();
            if (encontrados != cantidad) throw new IllegalArgumentException("Cantidad de equipos invalida para " + liga + ".");
        });
    }

    private static void validarIdentidadesEquipos(List<Equipo> equipos,
            List<IdentidadExternaEquipo> identidadesExternasEquipos) {
        if (identidadesExternasEquipos.size() != equipos.size()
                || identidadesExternasEquipos.stream().anyMatch(i -> i == null
                        || i.proveedor() != ProveedorIdentidadExterna.FOOTBALL_DATA
                        || i.externalId() <= 0)) {
            throw new IllegalArgumentException("El snapshot contiene un equipo sin ID externo valido.");
        }
        if (identidadesExternasEquipos.stream().anyMatch(i -> equipos.stream()
                .noneMatch(e -> e.id().equals(i.equipoId())))) {
            throw new IllegalArgumentException("El snapshot contiene un equipo sin identidad externa.");
        }
        unicos(identidadesExternasEquipos.stream()
                .map(i -> i.proveedor() + "|" + i.externalId()).toList(), "equipos");
    }

    private static void validarIdentidadesJugadores(List<Jugador> jugadores,
            Map<UUID, Long> identidadesFootballData) {
        if (identidadesFootballData.size() != jugadores.size()
                || jugadores.stream().anyMatch(j -> !identidadesFootballData.containsKey(j.getId()))) {
            throw new IllegalArgumentException("El snapshot contiene un jugador sin ID externo.");
        }
        if (identidadesFootballData.values().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("El snapshot contiene un ID externo invalido.");
        }
        unicos(identidadesFootballData.values().stream().toList(), "jugadores");
    }

    private static void validarReferenciasEquipo(List<Equipo> equipos, List<Jugador> jugadores) {
        Set<UUID> equiposIds = equipos.stream().map(Equipo::id).collect(Collectors.toSet());
        if (equiposIds.size() != equipos.size() || jugadores.stream().anyMatch(j -> j.getEquipoId() == null || !equiposIds.contains(j.getEquipoId()))) {
            throw new IllegalArgumentException("El snapshot contiene referencias de equipo invalidas.");
        }
        Map<UUID, Equipo> porId = equipos.stream().collect(Collectors.toMap(Equipo::id, Function.identity()));
        if (jugadores.stream().anyMatch(j -> !porId.get(j.getEquipoId()).isActivo())) {
            throw new IllegalArgumentException("Un jugador no puede pertenecer a un equipo inactivo.");
        }
    }

    private static void unicos(List<?> valores, String tipo) {
        if (Set.copyOf(valores).size() != valores.size()) throw new IllegalArgumentException("Hay IDs externos duplicados de " + tipo + ".");
    }

    public long getExternalId(UUID jugadorId) { return identidadesFootballData.get(jugadorId); }

    public IdentidadExternaEquipo getIdentidadExternaEquipo(UUID equipoId) {
        return identidadesExternasEquipos.stream()
                .filter(identidad -> identidad.equipoId().equals(equipoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "El snapshot no contiene identidad externa para el equipo."));
    }
}
