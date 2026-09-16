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
    private final String apellido;
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
        this.idExterno = null;
        this.equipoId = null;
        this.activo = true;
        this.creadoEn = Instant.EPOCH;
        this.actualizadoEn = Instant.EPOCH;
    }

    private Jugador(UUID id, Long idExterno, String nombre, String apellido, UUID equipoId,
            String posicion, Integer dorsal, String nacionalidad, boolean activo,
            Instant creadoEn, Instant actualizadoEn) {
        if (id == null || idExterno == null || idExterno <= 0 || equipoId == null) {
            throw new JugadorException("El jugador importado requiere identificadores validos.");
        }
        validarTexto(nombre, "nombre");
        if (apellido == null || apellido.codePointCount(0, apellido.length()) > 100) {
            throw new JugadorException("El apellido del jugador no es valido.");
        }
        validarTexto(nacionalidad, "nacionalidad");
        if (posicion == null || !POSICIONES.contains(posicion)) {
            throw new JugadorException("La posicion del jugador no es valida.");
        }
        if (dorsal != null && (dorsal < 1 || dorsal > 99)) {
            throw new JugadorException("El dorsal debe estar entre 1 y 99 cuando esta informado.");
        }
        if (creadoEn == null || actualizadoEn == null || actualizadoEn.isBefore(creadoEn)) {
            throw new JugadorException("Las fechas del jugador no son coherentes.");
        }
        this.id = id; this.idExterno = idExterno; this.nombre = nombre; this.apellido = apellido;
        this.liga = ""; this.equipo = ""; this.equipoId = equipoId; this.posicion = posicion;
        this.dorsal = dorsal == null ? 0 : dorsal; this.nacionalidad = nacionalidad;
        this.activo = activo; this.creadoEn = creadoEn; this.actualizadoEn = actualizadoEn;
    }

    public static Jugador importado(UUID id, long idExterno, String nombre, String apellido,
            UUID equipoId, String posicion, Integer dorsal, String nacionalidad, Instant ahora) {
        return new Jugador(id, idExterno, nombre, apellido == null ? "" : apellido, equipoId,
                posicion, dorsal, nacionalidad, true, ahora, ahora);
    }

    public Jugador desactivar(Instant ahora) {
        return new Jugador(id, idExterno, nombre, apellido, equipoId, posicion, dorsal == 0 ? null : dorsal,
                nacionalidad, false, creadoEn, ahora);
    }

    public Jugador reactivar(Instant ahora) {
        return new Jugador(id, idExterno, nombre, apellido, equipoId, posicion, dorsal == 0 ? null : dorsal,
                nacionalidad, true, creadoEn, ahora);
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
    public Long getIdExterno() { return idExterno; }
    public UUID getEquipoId() { return equipoId; }
    public boolean isActivo() { return activo; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
