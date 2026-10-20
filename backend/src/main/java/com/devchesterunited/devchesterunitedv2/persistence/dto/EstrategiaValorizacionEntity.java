package com.devchesterunited.devchesterunitedv2.persistence.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "estrategias_valorizacion")
@IdClass(EstrategiaValorizacionKey.class)
public class EstrategiaValorizacionEntity {
    @Id
    @Column(length = 40)
    private String id;
    @Id
    private int version;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(nullable = false, length = 20)
    private String tipo;
    @Column(name = "habilitada_para_calculo", nullable = false)
    private boolean habilitadaParaCalculo;
    @Column(name = "es_provisoria", nullable = false)
    private boolean esProvisoria;
    @Column(name = "valor_base", nullable = false)
    private BigDecimal valorBase;
    @Column(name = "factor_escala", nullable = false)
    private BigDecimal factorEscala;
    @Column(name = "configuracion_json", nullable = false)
    private String configuracionJson;

    protected EstrategiaValorizacionEntity() { }

    public EstrategiaValorizacionEntity(String id, int version, String nombre, String tipo,
            boolean habilitadaParaCalculo, boolean esProvisoria, BigDecimal valorBase,
            BigDecimal factorEscala, String configuracionJson) {
        this.id = id;
        this.version = version;
        this.nombre = nombre;
        this.tipo = tipo;
        this.habilitadaParaCalculo = habilitadaParaCalculo;
        this.esProvisoria = esProvisoria;
        this.valorBase = valorBase;
        this.factorEscala = factorEscala;
        this.configuracionJson = configuracionJson;
    }

    public String getId() { return id; }
    public int getVersion() { return version; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public boolean isHabilitadaParaCalculo() { return habilitadaParaCalculo; }
    public boolean isEsProvisoria() { return esProvisoria; }
    public BigDecimal getValorBase() { return valorBase; }
    public BigDecimal getFactorEscala() { return factorEscala; }
    public String getConfiguracionJson() { return configuracionJson; }
}
