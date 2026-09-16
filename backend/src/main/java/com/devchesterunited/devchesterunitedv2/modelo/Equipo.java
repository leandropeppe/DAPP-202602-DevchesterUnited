package com.devchesterunited.devchesterunitedv2.modelo;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Equipo del catalogo. No depende de Spring ni de JPA. */
public final class Equipo {
    private final UUID id;
    private final Long idExterno;
    private final String nombre;
    private final String nombreCorto;
    private final String sigla;
    private final String liga;
    private final URI escudoUrl;
    private final boolean activo;
    private final Long temporadaExternaId;
    private final LocalDate inicioTemporada;
    private final LocalDate finTemporada;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    public Equipo(UUID id, Long idExterno, String nombre, String nombreCorto, String sigla,
            String liga, URI escudoUrl, boolean activo, Long temporadaExternaId,
            LocalDate inicioTemporada, LocalDate finTemporada, Instant creadoEn, Instant actualizadoEn) {
        this.id = Objects.requireNonNull(id, "El identificador del equipo es obligatorio.");
        this.nombre = texto(nombre, "nombre", 100);
        this.nombreCorto = opcional(nombreCorto, 100, "nombre corto");
        this.sigla = opcional(sigla, 10, "sigla");
        this.liga = texto(liga, "liga", 100);
        if (escudoUrl != null && (!"https".equalsIgnoreCase(escudoUrl.getScheme())
                || escudoUrl.getHost() == null)) {
            throw new IllegalArgumentException("La URL del escudo debe usar HTTPS.");
        }
        if (idExterno != null && idExterno <= 0) {
            throw new IllegalArgumentException("El identificador externo del equipo debe ser positivo.");
        }
        if (temporadaExternaId != null && temporadaExternaId <= 0) {
            throw new IllegalArgumentException("La temporada externa debe ser positiva.");
        }
        if ((inicioTemporada == null) != (finTemporada == null)
                || inicioTemporada != null && finTemporada.isBefore(inicioTemporada)) {
            throw new IllegalArgumentException("Las fechas de temporada son incoherentes.");
        }
        this.idExterno = idExterno;
        this.escudoUrl = escudoUrl;
        this.activo = activo;
        this.temporadaExternaId = temporadaExternaId;
        this.inicioTemporada = inicioTemporada;
        this.finTemporada = finTemporada;
        this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creacion es obligatoria.");
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "La fecha de actualizacion es obligatoria.");
        if (actualizadoEn.isBefore(creadoEn)) {
            throw new IllegalArgumentException("La fecha de actualizacion no puede ser anterior a la creacion.");
        }
    }

    public static Equipo importado(UUID id, long idExterno, String nombre, String nombreCorto, String sigla,
            String liga, URI escudoUrl, long temporadaExternaId, LocalDate inicioTemporada,
            LocalDate finTemporada, Instant ahora) {
        return new Equipo(id, idExterno, nombre, nombreCorto, sigla, liga, escudoUrl, true,
                temporadaExternaId, inicioTemporada, finTemporada, ahora, ahora);
    }

    public Equipo desactivar(Instant ahora) {
        return new Equipo(id, idExterno, nombre, nombreCorto, sigla, liga, escudoUrl, false,
                temporadaExternaId, inicioTemporada, finTemporada, creadoEn, Objects.requireNonNull(ahora));
    }

    public Equipo reactivar(Instant ahora) {
        return new Equipo(id, idExterno, nombre, nombreCorto, sigla, liga, escudoUrl, true,
                temporadaExternaId, inicioTemporada, finTemporada, creadoEn, Objects.requireNonNull(ahora));
    }

    private static String texto(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank() || valor.codePointCount(0, valor.length()) > maximo) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio y no puede superar " + maximo + " caracteres.");
        }
        return valor.trim();
    }

    private static String opcional(String valor, int maximo, String campo) {
        if (valor == null) return null;
        if (valor.codePointCount(0, valor.length()) > maximo) {
            throw new IllegalArgumentException("El campo " + campo + " no puede superar " + maximo + " caracteres.");
        }
        return valor.trim();
    }

    public UUID getId() { return id; }
    public Long getIdExterno() { return idExterno; }
    public String getNombre() { return nombre; }
    public String getNombreCorto() { return nombreCorto; }
    public String getSigla() { return sigla; }
    public String getLiga() { return liga; }
    public URI getEscudoUrl() { return escudoUrl; }
    public boolean isActivo() { return activo; }
    public Long getTemporadaExternaId() { return temporadaExternaId; }
    public LocalDate getInicioTemporada() { return inicioTemporada; }
    public LocalDate getFinTemporada() { return finTemporada; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
