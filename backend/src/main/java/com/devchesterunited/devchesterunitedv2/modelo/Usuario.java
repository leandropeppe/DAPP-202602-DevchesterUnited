package com.devchesterunited.devchesterunitedv2.modelo;

import com.devchesterunited.devchesterunitedv2.modelo.exception.UsuarioException;
import java.time.Instant;
import java.util.UUID;

public final class Usuario {
    private final UUID id;
    private final String nombreUsuario;
    private final String correo;
    private final String hashContrasena;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    public Usuario(UUID id, String nombreUsuario, String correo, String hashContrasena,
            Instant creadoEn, Instant actualizadoEn) {
        if (id == null) throw new UsuarioException("El identificador del usuario es obligatorio.");
        exigirTexto(nombreUsuario, "El nombre de usuario es obligatorio.");
        exigirTexto(correo, "El correo es obligatorio.");
        exigirTexto(hashContrasena, "El hash de la contraseña es obligatorio.");
        if (creadoEn == null || actualizadoEn == null) {
            throw new UsuarioException("Las fechas de creación y actualización son obligatorias.");
        }
        if (actualizadoEn.isBefore(creadoEn)) {
            throw new UsuarioException("La actualización no puede ser anterior a la creación.");
        }
        this.id = id;
        this.nombreUsuario = nombreUsuario;
        this.correo = correo;
        this.hashContrasena = hashContrasena;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    private static void exigirTexto(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) throw new UsuarioException(mensaje);
    }

    public UUID getId() { return id; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getCorreo() { return correo; }
    public String getHashContrasena() { return hashContrasena; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
