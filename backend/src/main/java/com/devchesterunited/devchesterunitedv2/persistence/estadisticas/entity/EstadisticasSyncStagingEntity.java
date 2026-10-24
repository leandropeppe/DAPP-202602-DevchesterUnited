package com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "estadisticas_sync_staging")
public class EstadisticasSyncStagingEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "sync_id", nullable = false)
    private UUID syncId;
    @Column(nullable = false, length = 40)
    private String competicion;
    @Column(name = "whoscored_player_id", nullable = false)
    private Long whoscoredPlayerId;
    @Column(name = "whoscored_team_id")
    private Long whoscoredTeamId;
    @Column(name = "jugador_id")
    private UUID jugadorId;
    @Column(name = "equipo_id")
    private UUID equipoId;
    @Column(name = "nombre_jugador", nullable = false, length = 200)
    private String nombreJugador;
    @Column(length = 30)
    private String posicion;
    @Column(length = 100)
    private String nacionalidad;
    @Column(name = "nombre_equipo", length = 100)
    private String nombreEquipo;
    @Column(name = "familia_general_recibida", nullable = false)
    private boolean familiaGeneralRecibida;
    @Column(name = "familia_ofensiva_recibida", nullable = false)
    private boolean familiaOfensivaRecibida;
    @Column(name = "familia_defensiva_recibida", nullable = false)
    private boolean familiaDefensivaRecibida;
    @Column(name = "familia_pases_recibida", nullable = false)
    private boolean familiaPasesRecibida;
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

    protected EstadisticasSyncStagingEntity() { }

    public EstadisticasSyncStagingEntity(UUID id, UUID syncId, String competicion,
            Long whoscoredPlayerId, String nombreJugador) {
        this.id = id;
        this.syncId = syncId;
        this.competicion = competicion;
        this.whoscoredPlayerId = whoscoredPlayerId;
        this.nombreJugador = nombreJugador;
    }

    public UUID getId() { return id; }
    public UUID getSyncId() { return syncId; }
    public String getCompeticion() { return competicion; }
    public Long getWhoscoredPlayerId() { return whoscoredPlayerId; }
    public Long getWhoscoredTeamId() { return whoscoredTeamId; }
    public UUID getJugadorId() { return jugadorId; }
    public UUID getEquipoId() { return equipoId; }
    public String getNombreJugador() { return nombreJugador; }
    public String getPosicion() { return posicion; }
    public String getNacionalidad() { return nacionalidad; }
    public String getNombreEquipo() { return nombreEquipo; }
    public boolean isFamiliaGeneralRecibida() { return familiaGeneralRecibida; }
    public boolean isFamiliaOfensivaRecibida() { return familiaOfensivaRecibida; }
    public boolean isFamiliaDefensivaRecibida() { return familiaDefensivaRecibida; }
    public boolean isFamiliaPasesRecibida() { return familiaPasesRecibida; }
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
    public Integer getApps() { return apps; }
    public Integer getSubOn() { return subOn; }
    public Integer getManOfTheMatch() { return manOfTheMatch; }
    public BigDecimal getAerialWonPerGame() { return aerialWonPerGame; }
    public BigDecimal getFoulGivenPerGame() { return foulGivenPerGame; }
    public BigDecimal getOffsideGivenPerGame() { return offsideGivenPerGame; }
    public BigDecimal getDispossessedPerGame() { return dispossessedPerGame; }
    public BigDecimal getTurnoverPerGame() { return turnoverPerGame; }
    public BigDecimal getInterceptionPerGame() { return interceptionPerGame; }
    public BigDecimal getFoulsPerGame() { return foulsPerGame; }
    public BigDecimal getOffsideWonPerGame() { return offsideWonPerGame; }
    public BigDecimal getClearancePerGame() { return clearancePerGame; }
    public BigDecimal getWasDribbledPerGame() { return wasDribbledPerGame; }
    public BigDecimal getOutfielderBlockPerGame() { return outfielderBlockPerGame; }
    public Integer getGoalOwn() { return goalOwn; }
    public BigDecimal getTotalPassesPerGame() { return totalPassesPerGame; }
    public BigDecimal getPassSuccess() { return passSuccess; }
    public BigDecimal getAccurateCrossesPerGame() { return accurateCrossesPerGame; }
    public BigDecimal getAccurateLongPassPerGame() { return accurateLongPassPerGame; }
    public BigDecimal getAccurateThroughBallPerGame() { return accurateThroughBallPerGame; }

    public void aplicar(Aplicacion application) {
        this.jugadorId = application.jugadorId;
        this.equipoId = application.equipoId;
        this.whoscoredTeamId = application.teamId;
        this.nombreEquipo = application.nombreEquipo;
        this.posicion = application.posicion;
        this.nacionalidad = application.nacionalidad;
        if ("GENERAL".equals(application.familia)) this.familiaGeneralRecibida = true;
        if ("OFENSIVA".equals(application.familia)) this.familiaOfensivaRecibida = true;
        if ("DEFENSIVA".equals(application.familia)) this.familiaDefensivaRecibida = true;
        if ("PASES".equals(application.familia)) this.familiaPasesRecibida = true;
        this.minutosJugados = application.minutos;
        this.goles = application.goles;
        this.asistencias = application.asistencias;
        this.tiros = application.tiros;
        this.pasesClave = application.pasesClave;
        this.regates = application.regates;
        this.tackles = application.tackles;
        this.valoracion = application.valoracion;
        this.tarjetasAmarillas = application.amarillas;
        this.tarjetasRojas = application.rojas;
        this.apps = application.apps;
        this.subOn = application.subOn;
        this.manOfTheMatch = application.manOfTheMatch;
        this.aerialWonPerGame = application.aerialWonPerGame;
        this.foulGivenPerGame = application.foulGivenPerGame;
        this.offsideGivenPerGame = application.offsideGivenPerGame;
        this.dispossessedPerGame = application.dispossessedPerGame;
        this.turnoverPerGame = application.turnoverPerGame;
        this.interceptionPerGame = application.interceptionPerGame;
        this.foulsPerGame = application.foulsPerGame;
        this.offsideWonPerGame = application.offsideWonPerGame;
        this.clearancePerGame = application.clearancePerGame;
        this.wasDribbledPerGame = application.wasDribbledPerGame;
        this.outfielderBlockPerGame = application.outfielderBlockPerGame;
        this.goalOwn = application.goalOwn;
        this.totalPassesPerGame = application.totalPassesPerGame;
        this.passSuccess = application.passSuccess;
        this.accurateCrossesPerGame = application.accurateCrossesPerGame;
        this.accurateLongPassPerGame = application.accurateLongPassPerGame;
        this.accurateThroughBallPerGame = application.accurateThroughBallPerGame;
    }

    public static final class Aplicacion {
        private UUID jugadorId;
        private UUID equipoId;
        private Long teamId;
        private String nombreEquipo;
        private String posicion;
        private String nacionalidad;
        private String familia;
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

        private Aplicacion() { }

        public static Aplicacion builder() { return new Aplicacion(); }
        public Aplicacion jugadorId(UUID value) { jugadorId = value; return this; }
        public Aplicacion equipoId(UUID value) { equipoId = value; return this; }
        public Aplicacion teamId(Long value) { teamId = value; return this; }
        public Aplicacion nombreEquipo(String value) { nombreEquipo = value; return this; }
        public Aplicacion posicion(String value) { posicion = value; return this; }
        public Aplicacion nacionalidad(String value) { nacionalidad = value; return this; }
        public Aplicacion familia(String value) { familia = value; return this; }
        public Aplicacion minutos(Integer value) { minutos = value; return this; }
        public Aplicacion goles(Integer value) { goles = value; return this; }
        public Aplicacion asistencias(Integer value) { asistencias = value; return this; }
        public Aplicacion tiros(BigDecimal value) { tiros = value; return this; }
        public Aplicacion pasesClave(BigDecimal value) { pasesClave = value; return this; }
        public Aplicacion regates(BigDecimal value) { regates = value; return this; }
        public Aplicacion tackles(BigDecimal value) { tackles = value; return this; }
        public Aplicacion valoracion(BigDecimal value) { valoracion = value; return this; }
        public Aplicacion amarillas(Integer value) { amarillas = value; return this; }
        public Aplicacion rojas(Integer value) { rojas = value; return this; }
        public Aplicacion apps(Integer value) { apps = value; return this; }
        public Aplicacion subOn(Integer value) { subOn = value; return this; }
        public Aplicacion manOfTheMatch(Integer value) { manOfTheMatch = value; return this; }
        public Aplicacion aerialWonPerGame(BigDecimal value) { aerialWonPerGame = value; return this; }
        public Aplicacion foulGivenPerGame(BigDecimal value) { foulGivenPerGame = value; return this; }
        public Aplicacion offsideGivenPerGame(BigDecimal value) { offsideGivenPerGame = value; return this; }
        public Aplicacion dispossessedPerGame(BigDecimal value) { dispossessedPerGame = value; return this; }
        public Aplicacion turnoverPerGame(BigDecimal value) { turnoverPerGame = value; return this; }
        public Aplicacion interceptionPerGame(BigDecimal value) { interceptionPerGame = value; return this; }
        public Aplicacion foulsPerGame(BigDecimal value) { foulsPerGame = value; return this; }
        public Aplicacion offsideWonPerGame(BigDecimal value) { offsideWonPerGame = value; return this; }
        public Aplicacion clearancePerGame(BigDecimal value) { clearancePerGame = value; return this; }
        public Aplicacion wasDribbledPerGame(BigDecimal value) { wasDribbledPerGame = value; return this; }
        public Aplicacion outfielderBlockPerGame(BigDecimal value) { outfielderBlockPerGame = value; return this; }
        public Aplicacion goalOwn(Integer value) { goalOwn = value; return this; }
        public Aplicacion totalPassesPerGame(BigDecimal value) { totalPassesPerGame = value; return this; }
        public Aplicacion passSuccess(BigDecimal value) { passSuccess = value; return this; }
        public Aplicacion accurateCrossesPerGame(BigDecimal value) { accurateCrossesPerGame = value; return this; }
        public Aplicacion accurateLongPassPerGame(BigDecimal value) { accurateLongPassPerGame = value; return this; }
        public Aplicacion accurateThroughBallPerGame(BigDecimal value) { accurateThroughBallPerGame = value; return this; }
    }
}
