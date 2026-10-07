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
    @Column(name = "sync_id", nullable = false)
    private UUID syncId;
    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;
    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected EstadisticasJugadorEntity() { }

    public EstadisticasJugadorEntity(UUID id, UUID jugadorId, String posicion,
            Integer minutosJugados, Integer goles, Integer asistencias,
            BigDecimal tiros, BigDecimal pasesClave, BigDecimal regates,
            BigDecimal tackles, BigDecimal valoracion, Integer tarjetasAmarillas,
            Integer tarjetasRojas, UUID syncId, Instant creadoEn, Instant actualizadoEn) {
        this.id = id;
        this.jugadorId = jugadorId;
        this.posicion = posicion;
        this.minutosJugados = minutosJugados;
        this.goles = goles;
        this.asistencias = asistencias;
        this.tiros = tiros;
        this.pasesClave = pasesClave;
        this.regates = regates;
        this.tackles = tackles;
        this.valoracion = valoracion;
        this.tarjetasAmarillas = tarjetasAmarillas;
        this.tarjetasRojas = tarjetasRojas;
        this.syncId = syncId;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
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
    public UUID getSyncId() { return syncId; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    public void actualizar(String nuevaPosicion, Integer minutos, Integer goles, Integer asistencias,
            BigDecimal nuevosTiros, BigDecimal nuevosPasesClave, BigDecimal nuevosRegates,
            BigDecimal nuevosTackles, BigDecimal nuevaValoracion, Integer nuevasAmarillas,
            Integer nuevasRojas, UUID nuevaSync, Instant ahora) {
        this.posicion = nuevaPosicion;
        this.minutosJugados = minutos;
        this.goles = goles;
        this.asistencias = asistencias;
        this.tiros = nuevosTiros;
        this.pasesClave = nuevosPasesClave;
        this.regates = nuevosRegates;
        this.tackles = nuevosTackles;
        this.valoracion = nuevaValoracion;
        this.tarjetasAmarillas = nuevasAmarillas;
        this.tarjetasRojas = nuevasRojas;
        this.syncId = nuevaSync;
        this.actualizadoEn = ahora;
    }
}
