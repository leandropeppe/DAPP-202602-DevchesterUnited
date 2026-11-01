package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity;

import jakarta.persistence.*;

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

    private EstrategiaValorizacionEntity(Builder builder) {
        this.id = builder.id;
        this.version = builder.version;
        this.nombre = builder.nombre;
        this.tipo = builder.tipo;
        this.habilitadaParaCalculo = builder.habilitadaParaCalculo;
        this.esProvisoria = builder.esProvisoria;
        this.valorBase = builder.valorBase;
        this.factorEscala = builder.factorEscala;
        this.configuracionJson = builder.configuracionJson;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private int version;
        private String nombre;
        private String tipo;
        private boolean habilitadaParaCalculo;
        private boolean esProvisoria;
        private BigDecimal valorBase;
        private BigDecimal factorEscala;
        private String configuracionJson;

        public Builder id(String id) { this.id = id; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder nombre(String nombre) { this.nombre = nombre; return this; }
        public Builder tipo(String tipo) { this.tipo = tipo; return this; }
        public Builder habilitadaParaCalculo(boolean value) {
            this.habilitadaParaCalculo = value;
            return this;
        }
        public Builder esProvisoria(boolean value) {
            this.esProvisoria = value;
            return this;
        }
        public Builder valorBase(BigDecimal valorBase) { this.valorBase = valorBase; return this; }
        public Builder factorEscala(BigDecimal factorEscala) {
            this.factorEscala = factorEscala;
            return this;
        }
        public Builder configuracionJson(String configuracionJson) {
            this.configuracionJson = configuracionJson;
            return this;
        }
        public EstrategiaValorizacionEntity build() {
            return new EstrategiaValorizacionEntity(this);
        }
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
