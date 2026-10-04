package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "jugadores")
public class JugadorEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "name", nullable = false, length = 200)
    private String nombre;
    @Column(name = "league", nullable = false, length = 100)
    private String liga;
    @Column(name = "team", nullable = false, length = 100)
    private String equipo;
    @Column(name = "position", length = 30)
    private String posicion;
    @Column(name = "jersey_number")
    private Integer dorsal;
    @Column(name = "team_id")
    private UUID equipoId;
    @Column(name = "is_active", nullable = false)
    private boolean activo;
    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;
    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;
    @Column(name = "nationality", nullable = false, length = 100)
    private String nacionalidad;

    protected JugadorEntity() { }

    public JugadorEntity(UUID id, String nombre, String liga, String equipo,
            String posicion, int dorsal, String nacionalidad) {
        this.id = id;
        this.nombre = nombre;
        this.liga = liga;
        this.equipo = equipo;
        this.posicion = posicion;
        this.dorsal = dorsal;
        this.nacionalidad = nacionalidad;
        this.activo = true;
    }

    public JugadorEntity(UUID id, String nombre, UUID equipoId,
            String posicion, Integer dorsal, String nacionalidad, boolean activo,
            Instant creadoEn, Instant actualizadoEn, String liga, String equipo) {
        this.id=id; this.nombre=nombre;
        this.equipoId=equipoId; this.posicion=posicion; this.dorsal=dorsal; this.nacionalidad=nacionalidad;
        this.activo=activo; this.creadoEn=creadoEn; this.actualizadoEn=actualizadoEn;
        this.liga=liga; this.equipo=equipo;
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getLiga() { return liga; }
    public String getEquipo() { return equipo; }
    public String getPosicion() { return posicion; }
    public int getDorsal() { return dorsal == null ? 0 : dorsal; }
    public String getNacionalidad() { return nacionalidad; }
    public Integer getNumeroCamiseta() { return dorsal; }
    public UUID getEquipoId() { return equipoId; }
    public boolean isActivo() { return activo; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
