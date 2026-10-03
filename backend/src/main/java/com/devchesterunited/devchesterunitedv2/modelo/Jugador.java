package com.devchesterunited.devchesterunitedv2.modelo;

import com.devchesterunited.devchesterunitedv2.modelo.exception.JugadorException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Estado interno de un jugador dentro de Devchester United. */
public final class Jugador {
    private static final Set<String> POSICIONES = Set.of("Forward", "Midfielder", "Defender", "Goalkeeper");
    private static final String CAMPO_NACIONALIDAD = "nacionalidad";

    private final UUID id;
    private final String nombre;
    private final UUID equipoId;
    private final String posicion;
    private final Integer dorsal;
    private final String nacionalidad;
    private final boolean activo;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    /** Crea un jugador interno nuevo, activo y con timestamps iniciales iguales. */
    public Jugador(UUID id, String nombre, UUID equipoId, String posicion,
            Integer dorsal, String nacionalidad, Instant ahora) {
        this(id, nombre, equipoId, posicion, dorsal, nacionalidad, true, ahora, ahora);
    }

    private Jugador(UUID id, String nombre, UUID equipoId, String posicion,
            Integer dorsal, String nacionalidad, boolean activo,
            Instant creadoEn, Instant actualizadoEn) {
        if (id == null || equipoId == null) {
            throw new JugadorException("El jugador requiere identificadores validos.");
        }
        validarTexto(nombre, "nombre");
        validarTexto(nacionalidad, CAMPO_NACIONALIDAD);
        validarMaximo(nacionalidad, CAMPO_NACIONALIDAD, 100);
        if (posicion != null && !POSICIONES.contains(posicion)) {
            throw new JugadorException("La posicion del jugador no es valida.");
        }
        if (dorsal != null && (dorsal < 1 || dorsal > 99)) {
            throw new JugadorException("El dorsal debe estar entre 1 y 99 cuando esta informado.");
        }
        if (creadoEn == null || actualizadoEn == null || actualizadoEn.isBefore(creadoEn)) {
            throw new JugadorException("Las fechas del jugador no son coherentes.");
        }
        this.id = id;
        this.nombre = nombre;
        this.equipoId = equipoId;
        this.posicion = posicion;
        this.dorsal = dorsal;
        this.nacionalidad = nacionalidad;
        this.activo = activo;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public Jugador desactivar(Instant ahora) {
        return transicionar(false, ahora);
    }

    public Jugador reactivar(Instant ahora) {
        return transicionar(true, ahora);
    }

    private Jugador transicionar(boolean nuevoEstado, Instant ahora) {
        return new Jugador(id, nombre, equipoId, posicion, dorsal, nacionalidad,
                nuevoEstado, creadoEn, ahora);
    }

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new JugadorException("El campo " + campo + " es obligatorio y no puede estar vacio.");
        }
        if (valor.codePointCount(0, valor.length()) > 200) {
            throw new JugadorException("El campo " + campo + " no puede superar los 200 caracteres.");
        }
    }

    private static void validarMaximo(String valor, String campo, int maximo) {
        if (valor.codePointCount(0, valor.length()) > maximo) {
            throw new JugadorException("El campo " + campo + " no puede superar los " + maximo + " caracteres.");
        }
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public UUID getEquipoId() { return equipoId; }
    public String getPosicion() { return posicion; }
    public Integer getDorsal() { return dorsal; }
    public String getNacionalidad() { return nacionalidad; }
    public boolean isActivo() { return activo; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
