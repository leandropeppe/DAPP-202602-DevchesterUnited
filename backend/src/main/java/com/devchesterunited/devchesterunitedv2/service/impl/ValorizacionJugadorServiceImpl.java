package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ResultadoValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ResultadoElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ResultadoNormalizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.persistence.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EjecucionValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.ResultadoElegibilidadValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CotizacionActualEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EjecucionValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.HistoricoCotizacionEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.ResultadoElegibilidadValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.service.config.EstrategiasIniciales;
import com.devchesterunited.devchesterunitedv2.service.dto.SnapshotEstadistico;
import com.devchesterunited.devchesterunitedv2.service.interfaces.ValorizacionJugadorService;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstrategiaValorizacionKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ValorizacionJugadorServiceImpl implements ValorizacionJugadorService {
    private final SnapshotEstadisticoService snapshotService;
    private final EstrategiasIniciales estrategias;
    private final MotorValorizacion motor;
    private final EjecucionValorizacionRepository ejecucionRepository;
    private final CotizacionActualEstrategiaRepository actualRepository;
    private final HistoricoCotizacionEstrategiaRepository historicoRepository;
    private final ResultadoElegibilidadValorizacionRepository elegibilidadRepository;
    private final EstrategiaValorizacionRepository estrategiaRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ValorizacionJugadorServiceImpl(SnapshotEstadisticoService snapshotService,
            EstrategiasIniciales estrategias, MotorValorizacion motor,
            EjecucionValorizacionRepository ejecucionRepository,
            CotizacionActualEstrategiaRepository actualRepository,
            HistoricoCotizacionEstrategiaRepository historicoRepository,
            ResultadoElegibilidadValorizacionRepository elegibilidadRepository,
            EstrategiaValorizacionRepository estrategiaRepository, Clock clock) {
        this.snapshotService = snapshotService;
        this.estrategias = estrategias;
        this.motor = motor;
        this.ejecucionRepository = ejecucionRepository;
        this.actualRepository = actualRepository;
        this.historicoRepository = historicoRepository;
        this.elegibilidadRepository = elegibilidadRepository;
        this.estrategiaRepository = estrategiaRepository;
        this.clock = clock;
    }

    public ValorizacionJugadorServiceImpl(SnapshotEstadisticoService snapshotService,
            EstrategiasIniciales estrategias, MotorValorizacion motor,
            EjecucionValorizacionRepository ejecucionRepository,
            CotizacionActualEstrategiaRepository actualRepository,
            HistoricoCotizacionEstrategiaRepository historicoRepository,
            ResultadoElegibilidadValorizacionRepository elegibilidadRepository, Clock clock) {
        this(snapshotService, estrategias, motor, ejecucionRepository, actualRepository,
                historicoRepository, elegibilidadRepository, null, clock);
    }

    @Override
    public ResultadoEjecucion ejecutarManual(ConfiguracionElegibilidad configuracionElegibilidad) {
        return ejecutar("MANUAL", configuracionElegibilidad);
    }

    @Override
    public ResultadoEjecucion ejecutarSemanal(ConfiguracionElegibilidad configuracionElegibilidad) {
        return ejecutar("SEMANAL", configuracionElegibilidad);
    }

    private ResultadoEjecucion ejecutar(String tipo, ConfiguracionElegibilidad configuracionElegibilidad) {
        if (configuracionElegibilidad == null) {
            throw new IllegalArgumentException("La configuración de elegibilidad es obligatoria.");
        }
        validarCatalogoPersistido();
        Instant iniciada = Instant.now(clock);
        SnapshotEstadistico snapshot = snapshotService.capturar();
        UUID ejecucionId = UUID.randomUUID();
        ejecucionRepository.save(new EjecucionValorizacionEntity(ejecucionId, tipo,
                snapshot.capturadoEn(), iniciada, "INICIADA"));
        List<ResultadoValorizacion> resultados = new ArrayList<>();
        List<String> fallas = new ArrayList<>();
        boolean[] falloGlobal = {false};
        try {
            for (EstrategiaValorizacion estrategia : estrategias.habilitadas()) {
                try {
                    persistirElegibilidad(estrategia, snapshot.jugadores(), configuracionElegibilidad, ejecucionId, fallas);
                    List<ResultadoValorizacion> calculados = motor.calcular(estrategia,
                            snapshot.jugadores(), configuracionElegibilidad);
                    for (ResultadoValorizacion resultado : calculados) {
                        try {
                            persistir(resultado, estrategia, ejecucionId, snapshot.capturadoEn());
                            resultados.add(resultado);
                        } catch (RuntimeException exception) {
                            fallas.add(falla(resultado.jugadorId(), estrategia, exception));
                        }
                    }
                } catch (RuntimeException exception) {
                    falloGlobal[0] = true;
                fallas.add(estrategia.id() + ":" + estrategia.version() + ":" + exception.getMessage());
                }
            }
        } finally {
            ejecucionRepository.findById(ejecucionId).ifPresent(ejecucion -> {
                ejecucion.finalizar(Instant.now(clock), falloGlobal[0] ? "FALLIDA" : "COMPLETADA");
                ejecucionRepository.save(ejecucion);
            });
        }
        return new ResultadoEjecucion(ejecucionId, snapshot.capturadoEn(), resultados, fallas);
    }

    private void validarCatalogoPersistido() {
        if (estrategiaRepository == null) return;
        List<String> faltantes = estrategias.habilitadas().stream()
                .filter(estrategia -> estrategiaRepository.findById(
                        new EstrategiaValorizacionKey(estrategia.id(), estrategia.version())).isEmpty())
                .map(estrategia -> estrategia.id() + " v" + estrategia.version())
                .toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("No se puede ejecutar la valorizacion: faltan estrategias persistidas: "
                    + String.join(", ", faltantes) + ". Ejecute el bootstrap de valorizacion.");
        }
    }

    private void persistirElegibilidad(EstrategiaValorizacion estrategia, List<com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico> jugadores,
            ConfiguracionElegibilidad configuracion, UUID ejecucionId, List<String> fallas) {
        for (var jugador : jugadores) {
            try {
                ResultadoElegibilidad resultado = motor.evaluarElegibilidad(estrategia, jugador, configuracion);
                elegibilidadRepository.save(new ResultadoElegibilidadValorizacionEntity(ejecucionId, jugador.jugadorId(),
                        estrategia.id(), estrategia.version(), resultado.elegible(),
                        jsonArrayStrings(resultado.causasExclusion()), jsonArrayMetricas(resultado.metricasNegativasAusentes())));
            } catch (RuntimeException exception) {
                fallas.add(falla(jugador.jugadorId(), estrategia, exception));
            }
        }
    }

    private static String falla(UUID jugadorId, EstrategiaValorizacion estrategia, RuntimeException exception) {
        return jugadorId + ":" + estrategia.id() + ":" + estrategia.version() + ":" + exception.getMessage();
    }

    private void persistir(ResultadoValorizacion resultado, EstrategiaValorizacion estrategia,
            UUID ejecucionId, Instant calculadaAt) {
        String trazabilidad = trazabilidadJson(resultado, estrategia);
        actualRepository.save(new CotizacionActualEstrategiaEntity(resultado.jugadorId(), resultado.estrategiaId(),
                resultado.version(), ejecucionId, resultado.score(), resultado.valor(),
                estrategia.valorBase(), estrategia.factorEscala(), calculadaAt));
        historicoRepository.save(new HistoricoCotizacionEstrategiaEntity(UUID.randomUUID(), resultado.jugadorId(),
                resultado.estrategiaId(), resultado.version(), ejecucionId, calculadaAt, resultado.score(),
                resultado.valor(), estrategia.valorBase(), estrategia.factorEscala(), trazabilidad));
    }

    private static String trazabilidadJson(ResultadoValorizacion resultado, EstrategiaValorizacion estrategia) {
        StringBuilder json = new StringBuilder("{\"estrategia\":\"").append(escape(resultado.estrategiaId()))
                .append("\",\"version\":").append(resultado.version())
                .append(",\"provisoria\":").append(estrategia.esProvisoria(resultado.elegibilidad().posicion()))
                .append(",\"elegibilidad\":").append(resultado.elegibilidad().elegible())
                .append(",\"scorePosicionalAntesAjuste\":").append(jsonNumber(resultado.scorePosicionalAntesAjuste()))
                .append(",\"factorMercadoPosicion\":").append(jsonNumber(resultado.factorMercadoPosicion()))
                .append(",\"scoreFinalAjustado\":").append(jsonNumber(resultado.score()))
                .append(",\"posicionNormalizadaFactor\":\"").append(escape(resultado.posicionNormalizadaFactor())).append('"')
                .append(",\"causasExclusion\":").append(jsonArrayStrings(resultado.elegibilidad().causasExclusion()))
                .append(",\"metricasNegativasAusentes\":").append(jsonArrayMetricas(resultado.elegibilidad().metricasNegativasAusentes()))
                .append(",\"normalizaciones\":[");
        for (int i = 0; i < resultado.normalizaciones().size(); i++) {
            ResultadoNormalizacion n = resultado.normalizaciones().get(i);
            if (i > 0) json.append(',');
            json.append("{\"metrica\":\"").append(n.metrica().name()).append("\",\"valorBruto\":")
                    .append(jsonNumber(n.valorBruto())).append(",\"minimo\":").append(jsonNumber(n.minimo()))
                    .append(",\"maximo\":").append(jsonNumber(n.maximo())).append(",\"valorNormalizado\":")
                    .append(jsonNumber(n.valorNormalizado())).append(",\"alcance\":\"").append(escape(n.alcance()))
                    .append("\",\"estado\":\"").append(n.estado()).append("\",\"metodo\":\"")
                    .append(escape(n.metodo())).append("\"}");
        }
        return json.append("],\"penalizacionesAplicadas\":").append(jsonArrayMetricas(resultado.penalizacionesAplicadas())).append('}').toString();
    }

    private static String jsonArrayStrings(List<String> values) {
        return values.stream().map(value -> "\"" + escape(value) + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String jsonArrayMetricas(List<MetricaValorizacion> values) {
        return values.stream().map(Enum::name).map(value -> "\"" + value + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String jsonNumber(java.math.BigDecimal value) { return value == null ? "null" : value.toPlainString(); }
    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
