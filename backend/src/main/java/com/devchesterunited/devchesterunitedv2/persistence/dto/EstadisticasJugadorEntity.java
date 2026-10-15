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
        public Actualizacion syncId(UUID value) { syncId = value; return this; }
        public Actualizacion ahora(Instant value) { ahora = value; return this; }
    }
}
