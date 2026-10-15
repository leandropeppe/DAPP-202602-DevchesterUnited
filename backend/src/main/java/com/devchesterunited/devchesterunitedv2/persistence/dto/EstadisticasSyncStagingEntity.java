package com.devchesterunited.devchesterunitedv2.persistence.dto;

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
        if (application.minutos != null) this.minutosJugados = application.minutos;
        if (application.goles != null) this.goles = application.goles;
        if (application.asistencias != null) this.asistencias = application.asistencias;
        if (application.tiros != null) this.tiros = application.tiros;
        if (application.pasesClave != null) this.pasesClave = application.pasesClave;
        if (application.regates != null) this.regates = application.regates;
        if (application.tackles != null) this.tackles = application.tackles;
        if (application.valoracion != null) this.valoracion = application.valoracion;
        if (application.amarillas != null) this.tarjetasAmarillas = application.amarillas;
        if (application.rojas != null) this.tarjetasRojas = application.rojas;
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
    }
}
