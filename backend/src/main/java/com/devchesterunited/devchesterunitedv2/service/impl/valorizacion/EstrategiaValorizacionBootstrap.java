package com.devchesterunited.devchesterunitedv2.service.impl.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiasIniciales;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionKey;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaVisibleEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaVisibleRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/** Registra las definiciones de dominio después de que Flyway cree sus tablas. */
public class EstrategiaValorizacionBootstrap {
    private static final String TIPO_GENERAL = "GENERAL";

    private final EstrategiaValorizacionRepository estrategias;
    private final EstrategiaVisibleRepository visibles;
    private final EstrategiasIniciales iniciales;
    private final Clock clock;

    public EstrategiaValorizacionBootstrap(EstrategiaValorizacionRepository estrategias,
            EstrategiaVisibleRepository visibles, EstrategiasIniciales iniciales, Clock clock) {
        this.estrategias = estrategias;
        this.visibles = visibles;
        this.iniciales = iniciales;
        this.clock = clock;
    }

    @Transactional
    public void sincronizar() {
        for (EstrategiaValorizacion estrategia : iniciales.habilitadas()) {
            EstrategiaValorizacionKey key = new EstrategiaValorizacionKey(estrategia.id(), estrategia.version());
            if (estrategias.findById(key).isEmpty()) {
                estrategias.save(EstrategiaValorizacionEntity.builder().id(estrategia.id())
                        .version(estrategia.version()).nombre(nombre(estrategia))
                        .tipo(estrategia.tipo().name()).habilitadaParaCalculo(true)
                        .esProvisoria(estrategia.esProvisoria()).valorBase(estrategia.valorBase())
                        .factorEscala(estrategia.factorEscala())
                        .configuracionJson(configuracionJson(estrategia)).build());
            }
        }
        EstrategiaVisibleEntity visible = visibles.buscar().orElse(null);
        boolean debeActualizarDefault = visible == null
                || ("POSITIONAL".equals(visible.getEstrategiaId()) && visible.getEstrategiaVersion() == 1);
        if (debeActualizarDefault) {
            visibles.save(new EstrategiaVisibleEntity(iniciales.estrategiaVisibleId(),
                    iniciales.estrategiaVisibleVersion(), Instant.now(clock)));
        }
    }

    private static String nombre(EstrategiaValorizacion estrategia) {
        return estrategia.id().equals(TIPO_GENERAL) ? "General v" + estrategia.version()
                : "Positional v" + estrategia.version();
    }

    private static String configuracionJson(EstrategiaValorizacion estrategia) {
        String[] posiciones = estrategia.tipo().name().equals(TIPO_GENERAL)
                ? new String[] {TIPO_GENERAL} : new String[] {"DL", "MC", "DF", "PO"};
        StringBuilder json = new StringBuilder("{\"id\":\"").append(estrategia.id())
                .append("\",\"version\":").append(estrategia.version())
                .append(",\"esProvisoria\":").append(estrategia.esProvisoria())
                .append(",\"valorBase\":").append(estrategia.valorBase().toPlainString())
                .append(",\"factorEscala\":").append(estrategia.factorEscala().toPlainString())
                .append(",\"factoresMercado\":").append(factoresMercadoJson(estrategia))
                .append(",\"configuraciones\":{");
        for (int i = 0; i < posiciones.length; i++) {
            if (i > 0) json.append(',');
            String posicion = posiciones[i];
            json.append('"').append(posicion).append("\":{\"pesos\":")
                    .append(mapaJson(estrategia.pesosPositivos(posicion)))
                    .append(",\"penalizaciones\":")
                    .append(mapaJson(estrategia.pesosPenalizacion(posicion)))
                    .append(",\"provisoria\":").append(estrategia.esProvisoria(posicion)).append('}');
        }
        return json.append("}}").toString();
    }

    private static String factoresMercadoJson(EstrategiaValorizacion estrategia) {
        if (estrategia.tipo().name().equals(TIPO_GENERAL)) return "{}";
        String[] posiciones = {"DL", "MC", "DF", "PO"};
        return java.util.Arrays.stream(posiciones)
                .map(posicion -> "\"" + posicion + "\":" + estrategia.factorMercadoPosicion(posicion).toPlainString())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String mapaJson(Map<MetricaValorizacion, java.math.BigDecimal> mapa) {
        return mapa.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + entry.getKey().name() + "\":" + entry.getValue().toPlainString())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
