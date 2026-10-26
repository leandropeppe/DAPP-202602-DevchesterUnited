package com.devchesterunited.devchesterunitedv2.persistence.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
public class UsuarioEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "username", nullable = false, unique = true, length = 255)
    private String nombreUsuario;
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String correo;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String hashContrasena;
    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;
    @Column(name = "updated_at", nullable = false)
    private Instant actualizadoEn;

    protected UsuarioEntity() { }

    public UsuarioEntity(UUID id, String nombreUsuario, String correo, String hashContrasena,
            Instant creadoEn, Instant actualizadoEn) {
        this.id = id;
        this.nombreUsuario = nombreUsuario;
        this.correo = correo;
        this.hashContrasena = hashContrasena;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public UUID getId() { return id; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getCorreo() { return correo; }
    public String getHashContrasena() { return hashContrasena; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
