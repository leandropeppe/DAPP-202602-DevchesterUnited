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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ensambla snapshots normalizados a partir de las respuestas de statisticsfeed. */
final class WhoScoredStatisticsSnapshotAssembler {
    SnapshotEstadisticasJugador ensamblar(CompeticionEstadistica competicion,
            Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> respuestas,
            boolean passingEnabled) {
        Map<Long, JugadorAcumulado> jugadores = new LinkedHashMap<>();
        for (FamiliaEstadistica familia : familiasAProcesar(passingEnabled)) {
            acumularFamilia(jugadores, familia, respuestas.get(familia));
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

    private void acumularFamilia(Map<Long, JugadorAcumulado> jugadores,
            FamiliaEstadistica familia, WhoScoredStatisticsFeedResponseDTO response) {
        response.players().forEach(player -> acumularJugador(jugadores, familia, player));
    }

    private void acumularJugador(Map<Long, JugadorAcumulado> jugadores,
            FamiliaEstadistica familia, WhoScoredStatisticsFeedResponseDTO.PlayerDTO player) {
        long playerId = validarPlayer(player);
        jugadores.computeIfAbsent(playerId, JugadorAcumulado::new).aplicar(player, familia);
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
        private Integer goalOwn;
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
        private BigDecimal totalPassesPerGame;
        private BigDecimal passSuccess;
        private BigDecimal accurateCrossesPerGame;
        private BigDecimal accurateLongPassPerGame;
        private BigDecimal accurateThroughBallPerGame;

        private JugadorAcumulado(long playerId) { this.playerId = playerId; }

        private void aplicar(WhoScoredStatisticsFeedResponseDTO.PlayerDTO player,
                FamiliaEstadistica familia) {
            name = name == null ? normalizar(player.name()) : name;
            teamId = teamId == null ? player.teamId() : teamId;
            teamName = teamName == null ? normalizarOpcional(player.teamName()) : teamName;
            minsPlayed = preferir(minsPlayed, player.minsPlayed());
            goal = preferir(goal, player.goal());
            assistTotal = preferir(assistTotal, player.assistTotal());
            shotsPerGame = preferir(shotsPerGame, player.shotsPerGame());
            keyPassPerGame = preferirPrioridadOfensiva(keyPassPerGame,
                    player.keyPassPerGame(), familia);
            dribbleWonPerGame = preferirPrioridadOfensiva(dribbleWonPerGame,
                    player.dribbleWonPerGame(), familia);
            tacklePerGame = preferirPrioridadDefensiva(tacklePerGame,
                    player.tacklePerGame(), familia);
            rating = preferir(rating, player.rating());
            yellowCard = preferir(yellowCard, player.yellowCard());
            redCard = preferir(redCard, player.redCard());
            apps = preferir(apps, player.apps());
            subOn = preferir(subOn, player.subOn());
            manOfTheMatch = preferir(manOfTheMatch, player.manOfTheMatch());
            aerialWonPerGame = preferir(aerialWonPerGame, player.aerialWonPerGame());
            foulGivenPerGame = preferir(foulGivenPerGame, player.foulGivenPerGame());
            offsideGivenPerGame = preferir(offsideGivenPerGame, player.offsideGivenPerGame());
            dispossessedPerGame = preferir(dispossessedPerGame, player.dispossessedPerGame());
            turnoverPerGame = preferir(turnoverPerGame, player.turnoverPerGame());
            interceptionPerGame = preferir(interceptionPerGame, player.interceptionPerGame());
            foulsPerGame = preferir(foulsPerGame, player.foulsPerGame());
            offsideWonPerGame = preferir(offsideWonPerGame, player.offsideWonPerGame());
            clearancePerGame = preferir(clearancePerGame, player.clearancePerGame());
            wasDribbledPerGame = preferir(wasDribbledPerGame, player.wasDribbledPerGame());
            outfielderBlockPerGame = preferir(outfielderBlockPerGame, player.outfielderBlockPerGame());
            goalOwn = preferir(goalOwn, player.goalOwn());
            totalPassesPerGame = preferir(totalPassesPerGame, player.totalPassesPerGame());
            passSuccess = preferir(passSuccess, player.passSuccess());
            accurateCrossesPerGame = preferir(accurateCrossesPerGame, player.accurateCrossesPerGame());
            accurateLongPassPerGame = preferir(accurateLongPassPerGame, player.accurateLongPassPerGame());
            accurateThroughBallPerGame = preferir(accurateThroughBallPerGame, player.accurateThroughBallPerGame());
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

        private static <T> T preferir(T current, T candidate) {
            return current == null ? candidate : current;
        }

        private static BigDecimal preferirPrioridadOfensiva(BigDecimal current,
                BigDecimal candidate, FamiliaEstadistica familia) {
            if (familia == FamiliaEstadistica.OFENSIVA && candidate != null) {
                return candidate;
            }
            return preferir(current, candidate);
        }

        private static BigDecimal preferirPrioridadDefensiva(BigDecimal current,
                BigDecimal candidate, FamiliaEstadistica familia) {
            if (familia == FamiliaEstadistica.DEFENSIVA && candidate != null) {
                return candidate;
            }
            return preferir(current, candidate);
        }

        private static String normalizar(String value) { return value.trim().replaceAll("\\s+", " "); }

        private static String normalizarOpcional(String value) {
            return value == null || value.isBlank() ? null : normalizar(value);
        }
    }
}
