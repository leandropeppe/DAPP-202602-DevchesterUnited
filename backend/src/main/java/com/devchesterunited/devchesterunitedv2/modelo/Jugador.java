package com.devchesterunited.devchesterunitedv2.modelo;

import com.devchesterunited.devchesterunitedv2.modelo.exception.JugadorException;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

/** Perfil inmutable del catálogo; valida sus invariantes al construirse. */
public final class Jugador {
    private static final Set<String> POSICIONES = Set.of(
            "Forward", "Midfielder", "Defender", "Goalkeeper");

    private final UUID id;
    private final String nombre;
    private final String liga;
    private final String equipo;
    private final String posicion;
    private final int dorsal;
    private final String nacionalidad;
    private final Long idExterno;
    private final UUID equipoId;
    private final boolean activo;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    public Jugador(UUID id, String nombre, String liga, String equipo,
            String posicion, int dorsal, String nacionalidad) {
        if (id == null) {
            throw new JugadorException("El identificador del jugador es obligatorio.");
        }
        validarTexto(nombre, "nombre");
        validarTexto(liga, "liga");
        validarTexto(equipo, "equipo");
        validarTexto(nacionalidad, "nacionalidad");
        validarMaximo(liga, "liga", 100);
        validarMaximo(equipo, "equipo", 100);
        validarMaximo(nacionalidad, "nacionalidad", 100);
        if (posicion == null || !POSICIONES.contains(posicion)) {
            throw new JugadorException("La posición debe ser Forward, Midfielder, Defender o Goalkeeper.");
        }
        if (dorsal < 1 || dorsal > 99) {
            throw new JugadorException("El dorsal debe estar entre 1 y 99.");
        }
        this.id = id;
        this.nombre = nombre;
        this.liga = liga;
        this.equipo = equipo;
        this.posicion = posicion;
        this.dorsal = dorsal;
        this.nacionalidad = nacionalidad;
        this.idExterno = null;
        this.equipoId = null;
        this.activo = true;
        this.creadoEn = Instant.EPOCH;
        this.actualizadoEn = Instant.EPOCH;
    }

    private Jugador(UUID id, Long idExterno, String nombre, UUID equipoId,
            String posicion, Integer dorsal, String nacionalidad, boolean activo,
            Instant creadoEn, Instant actualizadoEn) {
        if (id == null || idExterno == null || idExterno <= 0 || equipoId == null) {
            throw new JugadorException("El jugador importado requiere identificadores validos.");
        }
        validarTexto(nombre, "nombre");
        validarTexto(nacionalidad, "nacionalidad");
        validarMaximo(nacionalidad, "nacionalidad", 100);
        if (posicion != null && !POSICIONES.contains(posicion)) {
            throw new JugadorException("La posicion del jugador no es valida.");
        }
        if (dorsal != null && (dorsal < 1 || dorsal > 99)) {
            throw new JugadorException("El dorsal debe estar entre 1 y 99 cuando esta informado.");
        }
        if (creadoEn == null || actualizadoEn == null || actualizadoEn.isBefore(creadoEn)) {
            throw new JugadorException("Las fechas del jugador no son coherentes.");
        }
        this.id = id; this.idExterno = idExterno; this.nombre = nombre;
        this.liga = ""; this.equipo = ""; this.equipoId = equipoId; this.posicion = posicion;
        this.dorsal = dorsal == null ? 0 : dorsal; this.nacionalidad = nacionalidad;
        this.activo = activo; this.creadoEn = creadoEn; this.actualizadoEn = actualizadoEn;
    }

    public static Jugador importado(UUID id, long idExterno, String nombre, UUID equipoId,
            String posicion, Integer dorsal, String nacionalidad, Instant ahora) {
        return new Jugador(id, idExterno, nombre, equipoId,
                posicion, dorsal, nacionalidad, true, ahora, ahora);
    }

    public Jugador desactivar(Instant ahora) {
        return new Jugador(id, idExterno, nombre, equipoId, posicion, dorsal == 0 ? null : dorsal,
                nacionalidad, false, creadoEn, ahora);
    }

    public Jugador reactivar(Instant ahora) {
        return new Jugador(id, idExterno, nombre, equipoId, posicion, dorsal == 0 ? null : dorsal,
                nacionalidad, true, creadoEn, ahora);
    }

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new JugadorException("El campo " + campo + " es obligatorio y no puede estar vacío.");
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
    public String getLiga() { return liga; }
    public String getEquipo() { return equipo; }
    public String getPosicion() { return posicion; }
    public int getDorsal() { return dorsal; }
    public String getNacionalidad() { return nacionalidad; }
    public Long getIdExterno() { return idExterno; }
    public UUID getEquipoId() { return equipoId; }
    public boolean isActivo() { return activo; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
