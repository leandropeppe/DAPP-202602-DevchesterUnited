package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_counterparties")
public class ContraparteMercadoEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "counterparty_type", nullable = false, length = 20)
    private String tipoContraparte;

    @Column(name = "display_name", nullable = false, length = 100)
    private String nombreVisible;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    protected ContraparteMercadoEntity() { }

    public ContraparteMercadoEntity(UUID id, String tipoContraparte,
            String nombreVisible, Instant creadoEn) {
        this.id = id;
        this.tipoContraparte = tipoContraparte;
        this.nombreVisible = nombreVisible;
        this.creadoEn = creadoEn;
    }

    public UUID getId() { return id; }
    public String getTipoContraparte() { return tipoContraparte; }
    public String getNombreVisible() { return nombreVisible; }
    public Instant getCreadoEn() { return creadoEn; }
}
