package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jugador_estadisticas", uniqueConstraints = {
        @UniqueConstraint(name = "ux_jugador_estadisticas_jugador", columnNames = "jugador_id")
})
public class EstadisticasJugadorEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "jugador_id", nullable = false)
    private UUID jugadorId;
    @Column(length = 30)
    private String posicion;
    @Column(name = "minutos_jugados")
    private Integer minutosJugados;
    private Integer goles;
    private Integer asistencias;
    private BigDecimal tiros;
    @Column(name = "pases_clave")
    private BigDecimal pasesClave;
    private BigDecimal regates;
    private BigDecimal tackles;
    private BigDecimal valoracion;
    @Column(name = "tarjetas_amarillas")
    private Integer tarjetasAmarillas;
    @Column(name = "tarjetas_rojas")
    private Integer tarjetasRojas;
    private Integer apps;
    @Column(name = "sub_on") private Integer subOn;
    @Column(name = "man_of_the_match") private Integer manOfTheMatch;
    @Column(name = "aerial_won_per_game") private BigDecimal aerialWonPerGame;
    @Column(name = "foul_given_per_game") private BigDecimal foulGivenPerGame;
    @Column(name = "offside_given_per_game") private BigDecimal offsideGivenPerGame;
    @Column(name = "dispossessed_per_game") private BigDecimal dispossessedPerGame;
    @Column(name = "turnover_per_game") private BigDecimal turnoverPerGame;
    @Column(name = "interception_per_game") private BigDecimal interceptionPerGame;
    @Column(name = "fouls_per_game") private BigDecimal foulsPerGame;
    @Column(name = "offside_won_per_game") private BigDecimal offsideWonPerGame;
    @Column(name = "clearance_per_game") private BigDecimal clearancePerGame;
    @Column(name = "was_dribbled_per_game") private BigDecimal wasDribbledPerGame;
    @Column(name = "outfielder_block_per_game") private BigDecimal outfielderBlockPerGame;
    @Column(name = "goal_own") private Integer goalOwn;
    @Column(name = "total_passes_per_game") private BigDecimal totalPassesPerGame;
    @Column(name = "pass_success") private BigDecimal passSuccess;
    @Column(name = "accurate_crosses_per_game") private BigDecimal accurateCrossesPerGame;
    @Column(name = "accurate_long_pass_per_game") private BigDecimal accurateLongPassPerGame;
    @Column(name = "accurate_through_ball_per_game") private BigDecimal accurateThroughBallPerGame;
    @Column(name = "sync_id", nullable = false)
    private UUID syncId;
    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;
    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected EstadisticasJugadorEntity() { }

    public EstadisticasJugadorEntity(Creacion creation) {
        this.id = creation.id;
        this.jugadorId = creation.jugadorId;
        this.posicion = creation.posicion;
        this.minutosJugados = creation.minutosJugados;
        this.goles = creation.goles;
        this.asistencias = creation.asistencias;
        this.tiros = creation.tiros;
        this.pasesClave = creation.pasesClave;
        this.regates = creation.regates;
        this.tackles = creation.tackles;
        this.valoracion = creation.valoracion;
        this.tarjetasAmarillas = creation.tarjetasAmarillas;
        this.tarjetasRojas = creation.tarjetasRojas;
        this.apps = creation.apps;
        this.subOn = creation.subOn;
        this.manOfTheMatch = creation.manOfTheMatch;
        this.aerialWonPerGame = creation.aerialWonPerGame;
        this.foulGivenPerGame = creation.foulGivenPerGame;
        this.offsideGivenPerGame = creation.offsideGivenPerGame;
        this.dispossessedPerGame = creation.dispossessedPerGame;
        this.turnoverPerGame = creation.turnoverPerGame;
        this.interceptionPerGame = creation.interceptionPerGame;
        this.foulsPerGame = creation.foulsPerGame;
        this.offsideWonPerGame = creation.offsideWonPerGame;
        this.clearancePerGame = creation.clearancePerGame;
        this.wasDribbledPerGame = creation.wasDribbledPerGame;
        this.outfielderBlockPerGame = creation.outfielderBlockPerGame;
        this.goalOwn = creation.goalOwn;
        this.totalPassesPerGame = creation.totalPassesPerGame;
        this.passSuccess = creation.passSuccess;
        this.accurateCrossesPerGame = creation.accurateCrossesPerGame;
        this.accurateLongPassPerGame = creation.accurateLongPassPerGame;
        this.accurateThroughBallPerGame = creation.accurateThroughBallPerGame;
        this.syncId = creation.syncId;
        this.creadoEn = creation.creadoEn;
        this.actualizadoEn = creation.actualizadoEn;
    }

    public static final class Creacion {
        private UUID id;
        private UUID jugadorId;
        private String posicion;
        private Integer minutosJugados;
        private Integer goles;
        private Integer asistencias;
        private BigDecimal tiros;
        private BigDecimal pasesClave;
        private BigDecimal regates;
        private BigDecimal tackles;
        private BigDecimal valoracion;
        private Integer tarjetasAmarillas;
        private Integer tarjetasRojas;
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
        private UUID syncId;
        private Instant creadoEn;
        private Instant actualizadoEn;

        private Creacion() { }

        public static Creacion builder() { return new Creacion(); }
        public Creacion id(UUID value) { id = value; return this; }
        public Creacion jugadorId(UUID value) { jugadorId = value; return this; }
        public Creacion posicion(String value) { posicion = value; return this; }
        public Creacion minutosJugados(Integer value) { minutosJugados = value; return this; }
        public Creacion goles(Integer value) { goles = value; return this; }
        public Creacion asistencias(Integer value) { asistencias = value; return this; }
        public Creacion tiros(BigDecimal value) { tiros = value; return this; }
        public Creacion pasesClave(BigDecimal value) { pasesClave = value; return this; }
        public Creacion regates(BigDecimal value) { regates = value; return this; }
        public Creacion tackles(BigDecimal value) { tackles = value; return this; }
        public Creacion valoracion(BigDecimal value) { valoracion = value; return this; }
        public Creacion tarjetasAmarillas(Integer value) { tarjetasAmarillas = value; return this; }
        public Creacion tarjetasRojas(Integer value) { tarjetasRojas = value; return this; }
        public Creacion apps(Integer value) { apps = value; return this; }
        public Creacion subOn(Integer value) { subOn = value; return this; }
        public Creacion manOfTheMatch(Integer value) { manOfTheMatch = value; return this; }
        public Creacion aerialWonPerGame(BigDecimal value) { aerialWonPerGame = value; return this; }
        public Creacion foulGivenPerGame(BigDecimal value) { foulGivenPerGame = value; return this; }
        public Creacion offsideGivenPerGame(BigDecimal value) { offsideGivenPerGame = value; return this; }
        public Creacion dispossessedPerGame(BigDecimal value) { dispossessedPerGame = value; return this; }
        public Creacion turnoverPerGame(BigDecimal value) { turnoverPerGame = value; return this; }
        public Creacion interceptionPerGame(BigDecimal value) { interceptionPerGame = value; return this; }
        public Creacion foulsPerGame(BigDecimal value) { foulsPerGame = value; return this; }
        public Creacion offsideWonPerGame(BigDecimal value) { offsideWonPerGame = value; return this; }
        public Creacion clearancePerGame(BigDecimal value) { clearancePerGame = value; return this; }
        public Creacion wasDribbledPerGame(BigDecimal value) { wasDribbledPerGame = value; return this; }
        public Creacion outfielderBlockPerGame(BigDecimal value) { outfielderBlockPerGame = value; return this; }
        public Creacion goalOwn(Integer value) { goalOwn = value; return this; }
        public Creacion totalPassesPerGame(BigDecimal value) { totalPassesPerGame = value; return this; }
        public Creacion passSuccess(BigDecimal value) { passSuccess = value; return this; }
        public Creacion accurateCrossesPerGame(BigDecimal value) { accurateCrossesPerGame = value; return this; }
        public Creacion accurateLongPassPerGame(BigDecimal value) { accurateLongPassPerGame = value; return this; }
        public Creacion accurateThroughBallPerGame(BigDecimal value) { accurateThroughBallPerGame = value; return this; }
        public Creacion syncId(UUID value) { syncId = value; return this; }
        public Creacion creadoEn(Instant value) { creadoEn = value; return this; }
        public Creacion actualizadoEn(Instant value) { actualizadoEn = value; return this; }
    }

    public UUID getId() { return id; }
    public UUID getJugadorId() { return jugadorId; }
    public String getPosicion() { return posicion; }
    public Integer getMinutosJugados() { return minutosJugados; }
    public Integer getGoles() { return goles; }
    public Integer getAsistencias() { return asistencias; }
    public BigDecimal getTiros() { return tiros; }
    public BigDecimal getPasesClave() { return pasesClave; }
    public BigDecimal getRegates() { return regates; }
    public BigDecimal getTackles() { return tackles; }
    public BigDecimal getValoracion() { return valoracion; }
    public Integer getTarjetasAmarillas() { return tarjetasAmarillas; }
    public Integer getTarjetasRojas() { return tarjetasRojas; }
    public Integer getApps() { return apps; } public Integer getSubOn() { return subOn; }
    public Integer getManOfTheMatch() { return manOfTheMatch; } public BigDecimal getAerialWonPerGame() { return aerialWonPerGame; }
    public BigDecimal getFoulGivenPerGame() { return foulGivenPerGame; } public BigDecimal getOffsideGivenPerGame() { return offsideGivenPerGame; }
    public BigDecimal getDispossessedPerGame() { return dispossessedPerGame; } public BigDecimal getTurnoverPerGame() { return turnoverPerGame; }
    public BigDecimal getInterceptionPerGame() { return interceptionPerGame; } public BigDecimal getFoulsPerGame() { return foulsPerGame; }
    public BigDecimal getOffsideWonPerGame() { return offsideWonPerGame; } public BigDecimal getClearancePerGame() { return clearancePerGame; }
    public BigDecimal getWasDribbledPerGame() { return wasDribbledPerGame; } public BigDecimal getOutfielderBlockPerGame() { return outfielderBlockPerGame; }
    public Integer getGoalOwn() { return goalOwn; } public BigDecimal getTotalPassesPerGame() { return totalPassesPerGame; }
    public BigDecimal getPassSuccess() { return passSuccess; } public BigDecimal getAccurateCrossesPerGame() { return accurateCrossesPerGame; }
    public BigDecimal getAccurateLongPassPerGame() { return accurateLongPassPerGame; } public BigDecimal getAccurateThroughBallPerGame() { return accurateThroughBallPerGame; }
    public UUID getSyncId() { return syncId; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    public void actualizar(Actualizacion update) {
        this.posicion = update.posicion;
        this.minutosJugados = update.minutos;
        this.goles = update.goles;
        this.asistencias = update.asistencias;
        this.tiros = update.tiros;
        this.pasesClave = update.pasesClave;
        this.regates = update.regates;
        this.tackles = update.tackles;
        this.valoracion = update.valoracion;
        this.tarjetasAmarillas = update.amarillas;
        this.tarjetasRojas = update.rojas;
        this.apps = update.apps;
        this.subOn = update.subOn;
        this.manOfTheMatch = update.manOfTheMatch;
        this.aerialWonPerGame = update.aerialWonPerGame;
        this.foulGivenPerGame = update.foulGivenPerGame;
        this.offsideGivenPerGame = update.offsideGivenPerGame;
        this.dispossessedPerGame = update.dispossessedPerGame;
        this.turnoverPerGame = update.turnoverPerGame;
        this.interceptionPerGame = update.interceptionPerGame;
        this.foulsPerGame = update.foulsPerGame;
        this.offsideWonPerGame = update.offsideWonPerGame;
        this.clearancePerGame = update.clearancePerGame;
        this.wasDribbledPerGame = update.wasDribbledPerGame;
        this.outfielderBlockPerGame = update.outfielderBlockPerGame;
        this.goalOwn = update.goalOwn;
        this.totalPassesPerGame = update.totalPassesPerGame;
        this.passSuccess = update.passSuccess;
        this.accurateCrossesPerGame = update.accurateCrossesPerGame;
        this.accurateLongPassPerGame = update.accurateLongPassPerGame;
        this.accurateThroughBallPerGame = update.accurateThroughBallPerGame;
        this.syncId = update.syncId;
        this.actualizadoEn = update.ahora;
    }

    public static final class Actualizacion {
        private String posicion;
        private Integer minutos;
        private Integer goles;
        private Integer asistencias;
        private BigDecimal tiros;
        private BigDecimal pasesClave;
        private BigDecimal regates;
        private BigDecimal tackles;
        private BigDecimal valoracion;
        private Integer amarillas;
        private Integer rojas;
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
        private UUID syncId;
        private Instant ahora;

        private Actualizacion() { }

        public static Actualizacion builder() { return new Actualizacion(); }
        public Actualizacion posicion(String value) { posicion = value; return this; }
        public Actualizacion minutos(Integer value) { minutos = value; return this; }
        public Actualizacion goles(Integer value) { goles = value; return this; }
        public Actualizacion asistencias(Integer value) { asistencias = value; return this; }
        public Actualizacion tiros(BigDecimal value) { tiros = value; return this; }
        public Actualizacion pasesClave(BigDecimal value) { pasesClave = value; return this; }
        public Actualizacion regates(BigDecimal value) { regates = value; return this; }
        public Actualizacion tackles(BigDecimal value) { tackles = value; return this; }
        public Actualizacion valoracion(BigDecimal value) { valoracion = value; return this; }
        public Actualizacion amarillas(Integer value) { amarillas = value; return this; }
        public Actualizacion rojas(Integer value) { rojas = value; return this; }
        public Actualizacion apps(Integer value) { apps = value; return this; }
        public Actualizacion subOn(Integer value) { subOn = value; return this; }
        public Actualizacion manOfTheMatch(Integer value) { manOfTheMatch = value; return this; }
        public Actualizacion aerialWonPerGame(BigDecimal value) { aerialWonPerGame = value; return this; }
        public Actualizacion foulGivenPerGame(BigDecimal value) { foulGivenPerGame = value; return this; }
        public Actualizacion offsideGivenPerGame(BigDecimal value) { offsideGivenPerGame = value; return this; }
        public Actualizacion dispossessedPerGame(BigDecimal value) { dispossessedPerGame = value; return this; }
        public Actualizacion turnoverPerGame(BigDecimal value) { turnoverPerGame = value; return this; }
        public Actualizacion interceptionPerGame(BigDecimal value) { interceptionPerGame = value; return this; }
        public Actualizacion foulsPerGame(BigDecimal value) { foulsPerGame = value; return this; }
        public Actualizacion offsideWonPerGame(BigDecimal value) { offsideWonPerGame = value; return this; }
        public Actualizacion clearancePerGame(BigDecimal value) { clearancePerGame = value; return this; }
        public Actualizacion wasDribbledPerGame(BigDecimal value) { wasDribbledPerGame = value; return this; }
        public Actualizacion outfielderBlockPerGame(BigDecimal value) { outfielderBlockPerGame = value; return this; }
        public Actualizacion goalOwn(Integer value) { goalOwn = value; return this; }
        public Actualizacion totalPassesPerGame(BigDecimal value) { totalPassesPerGame = value; return this; }
        public Actualizacion passSuccess(BigDecimal value) { passSuccess = value; return this; }
        public Actualizacion accurateCrossesPerGame(BigDecimal value) { accurateCrossesPerGame = value; return this; }
        public Actualizacion accurateLongPassPerGame(BigDecimal value) { accurateLongPassPerGame = value; return this; }
        public Actualizacion accurateThroughBallPerGame(BigDecimal value) { accurateThroughBallPerGame = value; return this; }
        public Actualizacion syncId(UUID value) { syncId = value; return this; }
        public Actualizacion ahora(Instant value) { ahora = value; return this; }
    }
}
