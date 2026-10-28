package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.EstadisticaSimulacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.TipoMetricaSimulacionDTO;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Define la superficie editable del simulador; no contiene pesos ni fórmulas de valorización. */
public final class ConfiguradorMetricasSimulacion {
    private static final Map<String, Set<MetricaValorizacion>> EDITABLES = Map.of(
            "Forward", EnumSet.of(MetricaValorizacion.GOALS, MetricaValorizacion.ASSISTS,
                    MetricaValorizacion.RATING, MetricaValorizacion.SHOTS,
                    MetricaValorizacion.KEY_PASSES, MetricaValorizacion.MAN_OF_THE_MATCH),
            "Midfielder", EnumSet.of(MetricaValorizacion.ASSISTS, MetricaValorizacion.RATING,
                    MetricaValorizacion.KEY_PASSES, MetricaValorizacion.PASS_SUCCESS,
                    MetricaValorizacion.GOALS, MetricaValorizacion.MAN_OF_THE_MATCH),
            "Defender", EnumSet.of(MetricaValorizacion.GOALS, MetricaValorizacion.RATING,
                    MetricaValorizacion.TACKLES, MetricaValorizacion.INTERCEPTIONS,
                    MetricaValorizacion.CLEARANCES, MetricaValorizacion.AERIAL_WON),
            "Goalkeeper", EnumSet.of(MetricaValorizacion.RATING, MetricaValorizacion.PASS_SUCCESS,
                    MetricaValorizacion.LONG_PASSES, MetricaValorizacion.MAN_OF_THE_MATCH));

    public List<EstadisticaSimulacionDTO> configurar(String posicion,
            EstrategiaValorizacion estrategia, MetricasEstadisticasJugador actuales) {
        Set<MetricaValorizacion> whitelist = EDITABLES.getOrDefault(posicion, Set.of());
        return java.util.Arrays.stream(MetricaValorizacion.values())
                .map(metrica -> definir(metrica, posicion, whitelist, estrategia, actuales))
                .toList();
    }

    public boolean esEditable(String posicion, EstrategiaValorizacion estrategia,
            MetricasEstadisticasJugador actuales, MetricaValorizacion metrica) {
        if (metrica == null || actuales == null || !EDITABLES.getOrDefault(posicion, Set.of()).contains(metrica)) {
            return false;
        }
        if (!CatalogoMetricaSimulacion.metricas().contains(metrica) || metrica.extraer(actuales) == null) {
            return false;
        }
        // RATING es una dimensión pública del simulador aunque V2 no la use como peso directo.
        return metrica == MetricaValorizacion.RATING
                || estrategia != null && (estrategia.pesosPositivos(posicion).containsKey(metrica)
                || estrategia.pesosPenalizacion(posicion).containsKey(metrica));
    }

    private EstadisticaSimulacionDTO definir(MetricaValorizacion metrica, String posicion,
            Set<MetricaValorizacion> whitelist, EstrategiaValorizacion estrategia,
            MetricasEstadisticasJugador actuales) {
        CatalogoMetricaSimulacion.Definicion definicion = CatalogoMetricaSimulacion.definir(metrica);
        var value = actuales == null ? null : metrica.extraer(actuales);
        return new EstadisticaSimulacionDTO(metrica.name(), definicion.etiqueta(), tipo(definicion.semantica()),
                value, esEditable(posicion, estrategia, actuales, metrica), definicion.minimo(), definicion.maximo());
    }

    private static TipoMetricaSimulacionDTO tipo(CatalogoMetricaSimulacion.Semantica semantica) {
        return switch (semantica) {
            case ACUMULATIVA -> TipoMetricaSimulacionDTO.ACCUMULATIVE;
            case POR_PARTIDO -> TipoMetricaSimulacionDTO.AVERAGE;
            case PORCENTAJE -> TipoMetricaSimulacionDTO.PERCENTAGE;
            case RATING -> TipoMetricaSimulacionDTO.RATING;
        };
    }
}
