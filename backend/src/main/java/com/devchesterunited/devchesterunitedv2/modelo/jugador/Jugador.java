package com.devchesterunited.devchesterunitedv2.modelo.jugador;

import com.devchesterunited.devchesterunitedv2.exception.JugadorException;

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
    private final EstadoJugador estado;

    /** Crea un jugador interno nuevo, activo y con timestamps iniciales iguales. */
    public Jugador(UUID id, String nombre, UUID equipoId, String posicion,
            Integer dorsal, String nacionalidad, Instant ahora) {
        this(id, nombre, equipoId, posicion, dorsal, nacionalidad, EstadoJugador.nuevo(ahora));
    }

    private Jugador(UUID id, String nombre, UUID equipoId, String posicion,
            Integer dorsal, String nacionalidad, EstadoJugador estado) {
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
        if (estado == null) throw new JugadorException("El estado del jugador es obligatorio.");
        this.id = id;
        this.nombre = nombre;
        this.equipoId = equipoId;
        this.posicion = posicion;
        this.dorsal = dorsal;
        this.nacionalidad = nacionalidad;
        this.estado = estado;
    }

    /** Reconstruye un jugador con el estado conservado en persistencia. */
    public static Jugador desdePersistencia(UUID id, String nombre, UUID equipoId,
            String posicion, Integer dorsal, String nacionalidad, EstadoJugador estado) {
        return new Jugador(id, nombre, equipoId, posicion, dorsal, nacionalidad, estado);
    }

    public Jugador desactivar(Instant ahora) {
        return transicionar(false, ahora);
    }

    public Jugador reactivar(Instant ahora) {
        return transicionar(true, ahora);
    }

    private Jugador transicionar(boolean nuevoEstado, Instant ahora) {
        EstadoJugador nuevoEstadoJugador = nuevoEstado
                ? estado.reactivar(ahora)
                : estado.desactivar(ahora);
        return new Jugador(id, nombre, equipoId, posicion, dorsal, nacionalidad,
                nuevoEstadoJugador);
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
    public EstadoJugador getEstado() { return estado; }
    public boolean isActivo() { return estado.isActivo(); }
    public Instant getCreadoEn() { return estado.getCreadoEn(); }
    public Instant getActualizadoEn() { return estado.getActualizadoEn(); }
}
