package com.devchesterunited.devchesterunitedv2.persistence.equipo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equipos")
public class EquipoEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "name", nullable = false, length = 100) private String nombre;
    @Column(name = "short_name", length = 100) private String nombreCorto;
    @Column(name = "tla", length = 10) private String sigla;
    @Column(name = "league", nullable = false, length = 100) private String liga;
    @Column(name = "crest_url") private String escudoUrl;
    @Column(name = "is_active", nullable = false) private boolean activo;
    @Column(name = "provider_season_id") private Long temporadaExternaId;
    @Column(name = "season_start") private LocalDate inicioTemporada;
    @Column(name = "season_end") private LocalDate finTemporada;
    @Column(name = "created_at", nullable = false) private Instant creadoEn;
    @Column(name = "updated_at", nullable = false) private Instant actualizadoEn;

    protected EquipoEntity() { }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private String nombre;
        private String nombreCorto;
        private String sigla;
        private String liga;
        private String escudoUrl;
        private boolean activo;
        private Long temporadaExternaId;
        private LocalDate inicioTemporada;
        private LocalDate finTemporada;
        private Instant creadoEn;
        private Instant actualizadoEn;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder nombre(String nombre) { this.nombre = nombre; return this; }
        public Builder nombreCorto(String nombreCorto) { this.nombreCorto = nombreCorto; return this; }
        public Builder sigla(String sigla) { this.sigla = sigla; return this; }
        public Builder liga(String liga) { this.liga = liga; return this; }
        public Builder escudoUrl(String escudoUrl) { this.escudoUrl = escudoUrl; return this; }
        public Builder activo(boolean activo) { this.activo = activo; return this; }
        public Builder temporadaExternaId(Long temporadaExternaId) {
            this.temporadaExternaId = temporadaExternaId;
            return this;
        }
        public Builder inicioTemporada(LocalDate inicioTemporada) {
            this.inicioTemporada = inicioTemporada;
            return this;
        }
        public Builder finTemporada(LocalDate finTemporada) {
            this.finTemporada = finTemporada;
            return this;
        }
        public Builder creadoEn(Instant creadoEn) { this.creadoEn = creadoEn; return this; }
        public Builder actualizadoEn(Instant actualizadoEn) {
            this.actualizadoEn = actualizadoEn;
            return this;
        }

        public EquipoEntity build() {
            EquipoEntity equipo = new EquipoEntity();
            equipo.id = id;
            equipo.nombre = nombre;
            equipo.nombreCorto = nombreCorto;
            equipo.sigla = sigla;
            equipo.liga = liga;
            equipo.escudoUrl = escudoUrl;
            equipo.activo = activo;
            equipo.temporadaExternaId = temporadaExternaId;
            equipo.inicioTemporada = inicioTemporada;
            equipo.finTemporada = finTemporada;
            equipo.creadoEn = creadoEn;
            equipo.actualizadoEn = actualizadoEn;
            return equipo;
        }
    }
    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getNombreCorto() { return nombreCorto; }
    public String getSigla() { return sigla; }
    public String getLiga() { return liga; }
    public String getEscudoUrl() { return escudoUrl; }
    public boolean isActivo() { return activo; }
    public Long getTemporadaExternaId() { return temporadaExternaId; }
    public LocalDate getInicioTemporada() { return inicioTemporada; }
    public LocalDate getFinTemporada() { return finTemporada; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
