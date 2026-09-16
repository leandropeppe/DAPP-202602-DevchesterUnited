package com.devchesterunited.devchesterunitedv2.modelo;

import com.devchesterunited.devchesterunitedv2.modelo.exception.JugadorException;
import java.util.Set;
import java.util.UUID;

/** Perfil inmutable del catálogo; valida sus invariantes al construirse. */
public final class Jugador {
    private static final Set<String> POSICIONES = Set.of(
            "Forward", "Midfielder", "Defender", "Goalkeeper");

    private final UUID id;
    private final String nombre;
    private final String apellido;
    private final String liga;
    private final String equipo;
    private final String posicion;
    private final int dorsal;
    private final String nacionalidad;

    public Jugador(UUID id, String nombre, String apellido, String liga, String equipo,
            String posicion, int dorsal, String nacionalidad) {
        if (id == null) {
            throw new JugadorException("El identificador del jugador es obligatorio.");
        }
        validarTexto(nombre, "nombre");
        validarTexto(apellido, "apellido");
        validarTexto(liga, "liga");
        validarTexto(equipo, "equipo");
        validarTexto(nacionalidad, "nacionalidad");
        if (posicion == null || !POSICIONES.contains(posicion)) {
            throw new JugadorException("La posición debe ser Forward, Midfielder, Defender o Goalkeeper.");
        }
        if (dorsal < 1 || dorsal > 99) {
            throw new JugadorException("El dorsal debe estar entre 1 y 99.");
        }
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.liga = liga;
        this.equipo = equipo;
        this.posicion = posicion;
        this.dorsal = dorsal;
        this.nacionalidad = nacionalidad;
    }

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new JugadorException("El campo " + campo + " es obligatorio y no puede estar vacío.");
        }
        if (valor.codePointCount(0, valor.length()) > 100) {
            throw new JugadorException("El campo " + campo + " no puede superar los 100 caracteres.");
        }
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
