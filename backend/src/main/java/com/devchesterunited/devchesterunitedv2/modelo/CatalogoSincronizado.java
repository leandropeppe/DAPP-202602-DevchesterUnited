package com.devchesterunited.devchesterunitedv2.modelo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Snapshot completo validado antes de cualquier mutacion de persistencia. */
public final class CatalogoSincronizado {
    private static final Map<String, Integer> CANTIDAD_POR_LIGA = Map.of(
            "Premier League", 20, "LaLiga", 20, "Bundesliga", 18, "Serie A", 20, "Ligue 1", 18);
    private final List<Equipo> equipos;
    private final List<Jugador> jugadores;

    public CatalogoSincronizado(List<Equipo> equipos, List<Jugador> jugadores) {
        this.equipos = List.copyOf(Objects.requireNonNull(equipos));
        this.jugadores = List.copyOf(Objects.requireNonNull(jugadores));
        validar();
    }

    private void validar() {
        if (equipos.size() != 96) throw new IllegalArgumentException("El snapshot debe contener 96 equipos.");
        if (jugadores.isEmpty() || jugadores.size() > 10_000) {
            throw new IllegalArgumentException("El snapshot debe contener entre 1 y 10000 jugadores.");
        }
        if (equipos.stream().anyMatch(e -> !e.isActivo() || e.getIdExterno() == null || !CANTIDAD_POR_LIGA.containsKey(e.getLiga()))) {
            throw new IllegalArgumentException("El snapshot contiene una liga no soportada.");
        }
        CANTIDAD_POR_LIGA.forEach((liga, cantidad) -> {
            long encontrados = equipos.stream().filter(e -> liga.equals(e.getLiga())).count();
            if (encontrados != cantidad) throw new IllegalArgumentException("Cantidad de equipos invalida para " + liga + ".");
        });
        unicos(equipos.stream().map(Equipo::getIdExterno).toList(), "equipos");
        if (jugadores.stream().anyMatch(j -> j.getIdExterno() == null)) throw new IllegalArgumentException("El snapshot contiene un jugador sin ID externo.");
        unicos(jugadores.stream().map(Jugador::getIdExterno).toList(), "jugadores");
        Set<UUID> equiposIds = equipos.stream().map(Equipo::getId).collect(Collectors.toSet());
        if (equiposIds.size() != equipos.size() || jugadores.stream().anyMatch(j -> j.getEquipoId() == null || !equiposIds.contains(j.getEquipoId()))) {
            throw new IllegalArgumentException("El snapshot contiene referencias de equipo invalidas.");
        }
        Map<UUID, Equipo> porId = equipos.stream().collect(Collectors.toMap(Equipo::getId, Function.identity()));
        if (jugadores.stream().anyMatch(j -> !porId.get(j.getEquipoId()).isActivo())) {
            throw new IllegalArgumentException("Un jugador no puede pertenecer a un equipo inactivo.");
        }
    }

    private static void unicos(List<?> valores, String tipo) {
        if (Set.copyOf(valores).size() != valores.size()) throw new IllegalArgumentException("Hay IDs externos duplicados de " + tipo + ".");
    }

    public List<Equipo> getEquipos() { return equipos; }
    public List<Jugador> getJugadores() { return jugadores; }
}
