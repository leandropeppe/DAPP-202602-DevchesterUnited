package com.devchesterunited.devchesterunitedv2.modelo;

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
        Map<UUID, Long> identidadesFootballDataEquipos) {

    private static final Map<String, Integer> CANTIDAD_POR_LIGA = Map.of(
            "Premier League", 20, "LaLiga", 20, "Bundesliga", 18, "Serie A", 20, "Ligue 1", 18);

    public CatalogoSincronizado {
        equipos = List.copyOf(Objects.requireNonNull(equipos));
        jugadores = List.copyOf(Objects.requireNonNull(jugadores));
        identidadesFootballData = Map.copyOf(Objects.requireNonNull(identidadesFootballData));
        identidadesFootballDataEquipos = Map.copyOf(
                Objects.requireNonNull(identidadesFootballDataEquipos));
        validar(equipos, jugadores, identidadesFootballData, identidadesFootballDataEquipos);
    }

    private static void validar(List<Equipo> equipos, List<Jugador> jugadores,
            Map<UUID, Long> identidadesFootballData,
            Map<UUID, Long> identidadesFootballDataEquipos) {
        if (equipos.size() != 96) throw new IllegalArgumentException("El snapshot debe contener 96 equipos.");
        if (jugadores.isEmpty() || jugadores.size() > 10_000) {
            throw new IllegalArgumentException("El snapshot debe contener entre 1 y 10000 jugadores.");
        }
        if (equipos.stream().anyMatch(e -> !e.isActivo()
                || !identidadesFootballDataEquipos.containsKey(e.id())
                || !CANTIDAD_POR_LIGA.containsKey(e.liga()))) {
            throw new IllegalArgumentException("El snapshot contiene una liga no soportada.");
        }
        CANTIDAD_POR_LIGA.forEach((liga, cantidad) -> {
            long encontrados = equipos.stream().filter(e -> liga.equals(e.liga())).count();
            if (encontrados != cantidad) throw new IllegalArgumentException("Cantidad de equipos invalida para " + liga + ".");
        });
        if (identidadesFootballDataEquipos.size() != equipos.size()
                || identidadesFootballDataEquipos.values().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("El snapshot contiene un equipo sin ID externo valido.");
        }
        if (equipos.stream().anyMatch(e -> !identidadesFootballDataEquipos.containsKey(e.id()))) {
            throw new IllegalArgumentException("El snapshot contiene un equipo sin identidad externa.");
        }
        unicos(identidadesFootballDataEquipos.values().stream().toList(), "equipos");
        if (identidadesFootballData.size() != jugadores.size()
                || jugadores.stream().anyMatch(j -> !identidadesFootballData.containsKey(j.getId()))) {
            throw new IllegalArgumentException("El snapshot contiene un jugador sin ID externo.");
        }
        if (identidadesFootballData.values().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("El snapshot contiene un ID externo invalido.");
        }
        unicos(identidadesFootballData.values().stream().toList(), "jugadores");
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

    public long getEquipoExternalId(UUID equipoId) {
        return identidadesFootballDataEquipos.get(equipoId);
    }
}
