package com.devchesterunited.devchesterunitedv2.modelo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Equipo del catalogo. No depende de Spring ni de JPA. */
public record Equipo(
        UUID id,
        String nombre,
        String liga,
        EstadoEquipo estado,
        TemporadaEquipo temporada) {

    public Equipo {
        Objects.requireNonNull(id, "El identificador del equipo es obligatorio.");
        nombre = texto(nombre, "nombre", 100);
        liga = texto(liga, "liga", 100);
        Objects.requireNonNull(estado, "El estado del equipo es obligatorio.");
        Objects.requireNonNull(temporada, "La temporada del equipo es obligatoria.");
    }

    public static Equipo importado(UUID id, String nombre, String liga,
            TemporadaEquipo temporada, Instant ahora) {
        return new Equipo(id, nombre, liga, EstadoEquipo.nuevo(ahora), temporada);
    }

    public Equipo desactivar(Instant ahora) {
        return new Equipo(id, nombre, liga, estado.desactivar(ahora), temporada);
    }

    public Equipo reactivar(Instant ahora) {
        return new Equipo(id, nombre, liga, estado.reactivar(ahora), temporada);
    }

    public boolean isActivo() {
        return estado.activo();
    }

    private static String texto(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()
                || valor.codePointCount(0, valor.length()) > maximo) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " es obligatorio y no puede superar " + maximo + " caracteres.");
        }
        return valor.trim();
    }
}
