package com.devchesterunited.devchesterunitedv2.persistence.dto;

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
    @Column(name = "external_id") private Long idExterno;
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

    public EquipoEntity(UUID id, Long idExterno, String nombre, String nombreCorto, String sigla,
            String liga, String escudoUrl, boolean activo, Long temporadaExternaId,
            LocalDate inicioTemporada, LocalDate finTemporada, Instant creadoEn, Instant actualizadoEn) {
        this.id=id; this.idExterno=idExterno; this.nombre=nombre; this.nombreCorto=nombreCorto;
        this.sigla=sigla; this.liga=liga; this.escudoUrl=escudoUrl; this.activo=activo;
        this.temporadaExternaId=temporadaExternaId; this.inicioTemporada=inicioTemporada;
        this.finTemporada=finTemporada; this.creadoEn=creadoEn; this.actualizadoEn=actualizadoEn;
    }
    public UUID getId() { return id; }
    public Long getIdExterno() { return idExterno; }
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
