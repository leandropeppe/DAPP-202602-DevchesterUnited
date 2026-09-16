package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "jugadores")
public class JugadorEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "first_name", nullable = false, length = 100)
    private String nombre;
    @Column(name = "last_name", nullable = false, length = 100)
    private String apellido;
    @Column(name = "league", nullable = false, length = 100)
    private String liga;
    @Column(name = "team", nullable = false, length = 100)
    private String equipo;
    @Column(name = "position", nullable = false, length = 30)
    private String posicion;
    @Column(name = "jersey_number", nullable = false)
    private int dorsal;
    @Column(name = "nationality", nullable = false, length = 100)
    private String nacionalidad;

    protected JugadorEntity() { }

    public JugadorEntity(UUID id, String nombre, String apellido, String liga, String equipo,
            String posicion, int dorsal, String nacionalidad) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.liga = liga;
        this.equipo = equipo;
        this.posicion = posicion;
        this.dorsal = dorsal;
        this.nacionalidad = nacionalidad;
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getLiga() { return liga; }
    public String getEquipo() { return equipo; }
    public String getPosicion() { return posicion; }
    public int getDorsal() { return dorsal; }
    public String getNacionalidad() { return nacionalidad; }
}
