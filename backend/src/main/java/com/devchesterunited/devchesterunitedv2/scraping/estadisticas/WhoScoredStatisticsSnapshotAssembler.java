package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ensambla snapshots y consolida las filas de un jugador transferido. */
final class WhoScoredStatisticsSnapshotAssembler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhoScoredStatisticsSnapshotAssembler.class);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    SnapshotEstadisticasJugador ensamblar(CompeticionEstadistica competicion,
            Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> respuestas,
            boolean passingEnabled) {
        Map<Long, JugadorAcumulado> jugadores = new LinkedHashMap<>();
        for (FamiliaEstadistica familia : familiasAProcesar(passingEnabled)) {
            WhoScoredStatisticsFeedResponseDTO response = respuestas.get(familia);
            if (response == null || response.players() == null) {
                throw new WhoScoredStatisticsException("Falta la familia estadistica " + familia + ".");
            }
            Map<Long, List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO>> porJugador =
                    response.players().stream().collect(Collectors.groupingBy(
                            this::validarPlayer, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
            porJugador.forEach((playerId, filas) ->
                    jugadores.computeIfAbsent(playerId, JugadorAcumulado::new).aplicar(familia, filas));
        }
        return new SnapshotEstadisticasJugador(competicion,
                jugadores.values().stream().map(JugadorAcumulado::registro).toList());
    }

    private List<FamiliaEstadistica> familiasAProcesar(boolean passingEnabled) {
        return passingEnabled
                ? List.of(FamiliaEstadistica.GENERAL, FamiliaEstadistica.OFENSIVA,
                        FamiliaEstadistica.DEFENSIVA, FamiliaEstadistica.PASES)
                : List.of(FamiliaEstadistica.GENERAL, FamiliaEstadistica.OFENSIVA,
                        FamiliaEstadistica.DEFENSIVA);
    }

    private long validarPlayer(WhoScoredStatisticsFeedResponseDTO.PlayerDTO player) {
        if (player == null || player.playerId() == null || player.playerId() <= 0
                || player.name() == null || player.name().isBlank()) {
            throw new WhoScoredStatisticsException("WhoScored no proporciono identidad suficiente del jugador.");
        }
        return player.playerId();
    }

    private static final class JugadorAcumulado {
        private final long playerId;
        private String name;
        private Long teamId;
        private String teamName;
        private final Set<Long> equiposVistos = new java.util.LinkedHashSet<>();
        private final Set<String> nombresEquiposVistos = new java.util.LinkedHashSet<>();
        private Long seasonId;
        private String seasonName;
        private Integer minsPlayed;
        private Integer goal;
        private Integer assistTotal;
        private BigDecimal shotsPerGame;
        private BigDecimal keyPassPerGame;
        private BigDecimal dribbleWonPerGame;
        private BigDecimal tacklePerGame;
        private BigDecimal rating;
        private Integer yellowCard;
        private Integer redCard;
        private Integer apps;
        private Integer subOn;
        private Integer manOfTheMatch;
        private BigDecimal aerialWonPerGame;
        private BigDecimal foulGivenPerGame;
        private BigDecimal offsideGivenPerGame;
        private BigDecimal dispossessedPerGame;
        private BigDecimal turnoverPerGame;
        private BigDecimal interceptionPerGame;
        private BigDecimal foulsPerGame;
        private BigDecimal offsideWonPerGame;
        private BigDecimal clearancePerGame;
        private BigDecimal wasDribbledPerGame;
        private BigDecimal outfielderBlockPerGame;
        private Integer goalOwn;
        private BigDecimal totalPassesPerGame;
        private BigDecimal passSuccess;
        private BigDecimal accurateCrossesPerGame;
        private BigDecimal accurateLongPassPerGame;
        private BigDecimal accurateThroughBallPerGame;

        private JugadorAcumulado(long playerId) { this.playerId = playerId; }

        private void aplicar(FamiliaEstadistica familia,
                List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas) {
            validarIdentidad(filas);
            switch (familia) {
                case GENERAL -> aplicarGeneral(filas);
                case OFENSIVA -> aplicarOfensiva(filas);
                case DEFENSIVA -> aplicarDefensiva(filas);
                case PASES -> aplicarPases(filas);
            }
            if (filas.size() > 1 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("WhoScored consolidation playerId={}, teams={}, family={}, policy={}",
                        playerId, equiposVistos, familia, politica(familia));
            }
        }

        private void validarIdentidad(List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas) {
            String nombre = normalizar(filas.get(0).name());

            if (filas.stream().anyMatch(fila -> !Objects.equals(nombre, normalizar(fila.name())))) {
                throw new WhoScoredStatisticsException(
                        "WhoScored devolvio identidades incompatibles para playerId=" + playerId + ".");
            }

            String nombreOriginal = filas.get(0).name().trim();

            if (name != null && !Objects.equals(normalizar(name), normalizar(nombreOriginal))) {
                throw new WhoScoredStatisticsException(
                        "WhoScored devolvio identidades incompatibles para playerId=" + playerId + ".");
            }

            name = name == null ? nombreOriginal : name;

            Set<Long> equipos = filas.stream()
                    .map(WhoScoredStatisticsFeedResponseDTO.PlayerDTO::teamId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<String> nombresEquipos = filas.stream()
                    .map(WhoScoredStatisticsFeedResponseDTO.PlayerDTO::teamName)
                    .filter(Objects::nonNull)
                    .map(JugadorAcumulado::normalizar)
                    .collect(Collectors.toSet());

            validarTemporadas(filas);

            equiposVistos.addAll(equipos);
            nombresEquiposVistos.addAll(nombresEquipos);

            teamId = equiposVistos.size() == 1
                    ? equiposVistos.iterator().next()
                    : null;

            teamName = nombresEquiposVistos.size() == 1
                    ? filas.get(0).teamName()
                    : null;
        }

        private void validarTemporadas(List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas) {
            Set<Long> temporadas = filas.stream()
                    .map(WhoScoredStatisticsFeedResponseDTO.PlayerDTO::seasonId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<String> nombresTemporadas = filas.stream()
                    .map(WhoScoredStatisticsFeedResponseDTO.PlayerDTO::seasonName)
                    .filter(Objects::nonNull)
                    .map(JugadorAcumulado::normalizar)
                    .collect(Collectors.toSet());

            if (temporadas.size() > 1 || nombresTemporadas.size() > 1) {
                throw new WhoScoredStatisticsException(
                        "WhoScored devolvio temporadas incompatibles para playerId=" + playerId + ".");
            }

            Long nuevaTemporada = temporadas.isEmpty()
                    ? null
                    : temporadas.iterator().next();

            String nuevoNombreTemporada = nombresTemporadas.isEmpty()
                    ? null
                    : nombresTemporadas.iterator().next();

            if ((seasonId != null && nuevaTemporada != null && !seasonId.equals(nuevaTemporada))
                    || (seasonName != null && nuevoNombreTemporada != null
                    && !seasonName.equals(nuevoNombreTemporada))) {
                throw new WhoScoredStatisticsException(
                        "WhoScored devolvio temporadas incompatibles para playerId=" + playerId + ".");
            }

            if (seasonId == null) {
                seasonId = nuevaTemporada;
            }

            if (seasonName == null) {
                seasonName = nuevoNombreTemporada;
            }
        }

        private void aplicarGeneral(List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas) {
            minsPlayed = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::minsPlayed, "minsPlayed");
            apps = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::apps, "apps");
            subOn = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::subOn, "subOn");
            manOfTheMatch = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::manOfTheMatch, "manOfTheMatch");
            yellowCard = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::yellowCard, "yellowCard");
            redCard = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::redCard, "redCard");
            goal = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::goal, "goal");
            assistTotal = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::assistTotal, "assistTotal");
            aerialWonPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::aerialWonPerGame);
            rating = promedioPonderado(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::rating);
        }

        private void aplicarOfensiva(List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas) {
            if (goal == null) goal = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::goal, "goal");
            if (assistTotal == null) {
                assistTotal = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::assistTotal, "assistTotal");
            }
            shotsPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::shotsPerGame);
            keyPassPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::keyPassPerGame);
            dribbleWonPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::dribbleWonPerGame);
            foulGivenPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::foulGivenPerGame);
            offsideGivenPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::offsideGivenPerGame);
            dispossessedPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::dispossessedPerGame);
            turnoverPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::turnoverPerGame);
        }

        private void aplicarDefensiva(List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas) {
            tacklePerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::tacklePerGame);
            interceptionPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::interceptionPerGame);
            foulsPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::foulsPerGame);
            offsideWonPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::offsideWonPerGame);
            clearancePerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::clearancePerGame);
            wasDribbledPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::wasDribbledPerGame);
            outfielderBlockPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::outfielderBlockPerGame);
            goalOwn = sumar(filas, WhoScoredStatisticsFeedResponseDTO.PlayerDTO::goalOwn, "goalOwn");
        }

        private void aplicarPases(List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas) {
            totalPassesPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::totalPassesPerGame);
            passSuccess = promedioPorVolumenDePases(filas);
            accurateCrossesPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::accurateCrossesPerGame);
            accurateLongPassPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::accurateLongPassPerGame);
            accurateThroughBallPerGame = promedioPonderado(filas,
                    WhoScoredStatisticsFeedResponseDTO.PlayerDTO::accurateThroughBallPerGame);
        }

        private RegistroEstadisticasJugador registro() {
            return new RegistroEstadisticasJugador(
                    new IdentidadEstadisticaJugador(playerId, name, null, null, teamId, teamName),
                    FamiliaEstadistica.GENERAL,
                    new MetricasEstadisticasJugador(
                            new MetricasGenerales(apps, subOn, minsPlayed, manOfTheMatch,
                                    aerialWonPerGame, rating, yellowCard, redCard),
                            new MetricasOfensivas(goal, assistTotal, shotsPerGame, keyPassPerGame,
                                    dribbleWonPerGame, foulGivenPerGame, offsideGivenPerGame,
                                    dispossessedPerGame, turnoverPerGame),
                            new MetricasDefensivas(tacklePerGame, interceptionPerGame, foulsPerGame,
                                    offsideWonPerGame, clearancePerGame, wasDribbledPerGame,
                                    outfielderBlockPerGame, goalOwn),
                            new MetricasPases(totalPassesPerGame, passSuccess, accurateCrossesPerGame,
                                    accurateLongPassPerGame, accurateThroughBallPerGame)));
        }

        private static Integer sumar(List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas,
                Function<WhoScoredStatisticsFeedResponseDTO.PlayerDTO, Integer> getter, String nombre) {
            if (filas.stream().map(getter).anyMatch(value -> value != null && value < 0)) {
                throw new IllegalArgumentException("La métrica " + nombre + " no puede ser negativa.");
            }
            if (filas.stream().anyMatch(fila -> !enteroValido(getter.apply(fila)))) return null;
            return filas.stream().map(getter).reduce(0, Integer::sum);
        }

        private static BigDecimal promedioPonderado(
                List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas,
                Function<WhoScoredStatisticsFeedResponseDTO.PlayerDTO, BigDecimal> getter) {
            BigDecimal numerador = BigDecimal.ZERO;
            int denominador = 0;
            for (var fila : filas) {
                BigDecimal valor = getter.apply(fila);
                Integer apps = fila.apps();
                if (decimalValido(valor) && valor.signum() < 0) {
                    throw new IllegalArgumentException("La métrica ponderada no puede ser negativa.");
                }
                if (!decimalValido(valor) || !enteroPositivo(apps)) continue;
                numerador = numerador.add(valor.multiply(BigDecimal.valueOf(apps)));
                denominador += apps;
            }
            if (denominador == 0 && filas.size() == 1) {
                BigDecimal unico = getter.apply(filas.get(0));
                return decimalValido(unico) ? unico : null;
            }
            return denominador == 0 ? null
                    : numerador.divide(BigDecimal.valueOf(denominador), 8, RoundingMode.HALF_UP);
        }

        private static BigDecimal promedioPorVolumenDePases(
                List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> filas) {
            BigDecimal numerador = BigDecimal.ZERO;
            BigDecimal denominador = BigDecimal.ZERO;
            for (var fila : filas) {
                BigDecimal porcentaje = fila.passSuccess();
                BigDecimal pasesPorPartido = fila.totalPassesPerGame();
                if (decimalValido(porcentaje)
                        && (porcentaje.signum() < 0 || porcentaje.compareTo(CIEN) > 0)) {
                    throw new IllegalArgumentException("La métrica passSuccess debe estar entre 0 y 100.");
                }
                if (decimalValido(pasesPorPartido) && pasesPorPartido.signum() < 0) {
                    throw new IllegalArgumentException("La métrica totalPassesPerGame no puede ser negativa.");
                }
                if (!decimalValido(porcentaje) || porcentaje.signum() < 0
                        || porcentaje.compareTo(CIEN) > 0 || !decimalValido(pasesPorPartido)
                        || pasesPorPartido.signum() < 0 || !enteroPositivo(fila.apps())) continue;
                BigDecimal peso = pasesPorPartido.multiply(BigDecimal.valueOf(fila.apps()));
                numerador = numerador.add(porcentaje.multiply(peso));
                denominador = denominador.add(peso);
            }
            if (denominador.signum() == 0 && filas.size() == 1
                    && decimalValido(filas.get(0).passSuccess())
                    && filas.get(0).passSuccess().signum() >= 0
                    && filas.get(0).passSuccess().compareTo(CIEN) <= 0) {
                return filas.get(0).passSuccess();
            }
            return denominador.signum() == 0 ? null
                    : numerador.divide(denominador, 8, RoundingMode.HALF_UP);
        }

        private static boolean enteroValido(Integer value) { return value != null && value >= 0; }
        private static boolean enteroPositivo(Integer value) { return value != null && value > 0; }
        private static boolean decimalValido(BigDecimal value) {
            return value != null && value.scale() < 1000;
        }
        private static String normalizar(String value) {
            return value == null ? null : value.trim().replaceAll("\\s+", " ");
        }

        private static String politica(FamiliaEstadistica familia) {
            return switch (familia) {
                case GENERAL -> "exact-counts;apps-weighted-rating-and-per-game";
                case PASES -> "apps-weighted-per-game;pass-volume-weighted-percentage";
                case OFENSIVA, DEFENSIVA -> "apps-weighted-per-game";
            };
        }
    }
}
