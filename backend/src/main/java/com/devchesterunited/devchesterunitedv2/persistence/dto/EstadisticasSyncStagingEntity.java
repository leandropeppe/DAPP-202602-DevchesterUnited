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

    public void aplicar(UUID jugadorId, UUID equipoId, Long teamId, String nombreEquipo,
            String posicion, String nacionalidad, String familia,
            Integer minutos, Integer goles, Integer asistencias, BigDecimal tiros,
            BigDecimal pasesClave, BigDecimal regates, BigDecimal tackles, BigDecimal valoracion,
            Integer amarillas, Integer rojas) {
        this.jugadorId = jugadorId;
        this.equipoId = equipoId;
        this.whoscoredTeamId = teamId;
        this.nombreEquipo = nombreEquipo;
        this.posicion = posicion;
        this.nacionalidad = nacionalidad;
        if ("GENERAL".equals(familia)) this.familiaGeneralRecibida = true;
        if ("OFENSIVA".equals(familia)) this.familiaOfensivaRecibida = true;
        if ("DEFENSIVA".equals(familia)) this.familiaDefensivaRecibida = true;
        if (minutos != null) this.minutosJugados = minutos;
        if (goles != null) this.goles = goles;
        if (asistencias != null) this.asistencias = asistencias;
        if (tiros != null) this.tiros = tiros;
        if (pasesClave != null) this.pasesClave = pasesClave;
        if (regates != null) this.regates = regates;
        if (tackles != null) this.tackles = tackles;
        if (valoracion != null) this.valoracion = valoracion;
        if (amarillas != null) this.tarjetasAmarillas = amarillas;
        if (rojas != null) this.tarjetasRojas = rojas;
    }
}
