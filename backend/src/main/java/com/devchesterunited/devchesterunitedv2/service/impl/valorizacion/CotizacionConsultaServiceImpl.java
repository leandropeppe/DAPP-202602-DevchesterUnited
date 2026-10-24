package com.devchesterunited.devchesterunitedv2.service.impl.valorizacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.CotizacionActualEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.HistoricoCotizacionEstrategiaRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.CotizacionActualEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.HistoricoCotizacionEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.valorizacion.ValuacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.valorizacion.ValuacionHistoricoDTO;
import com.devchesterunited.devchesterunitedv2.exception.ConsultaValorizacionInvalidaException;
import com.devchesterunited.devchesterunitedv2.exception.CotizacionNoEncontradaException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.CotizacionConsultaService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.EstrategiaVisibleService;
import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.EstrategiasIniciales;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CotizacionConsultaServiceImpl implements CotizacionConsultaService {
    private final CotizacionActualEstrategiaRepository actuales;
    private final HistoricoCotizacionEstrategiaRepository historicos;
    private final EstrategiaValorizacionRepository estrategias;
    private final EstrategiaVisibleService visible;
    private final EstrategiasIniciales iniciales;
    private final ObjectMapper objectMapper;

    public CotizacionConsultaServiceImpl(CotizacionActualEstrategiaRepository actuales,
            HistoricoCotizacionEstrategiaRepository historicos,
            EstrategiaValorizacionRepository estrategias, EstrategiaVisibleService visible,
            EstrategiasIniciales iniciales, ObjectMapper objectMapper) {
        this.actuales = actuales;
        this.historicos = historicos;
        this.estrategias = estrategias;
        this.visible = visible;
        this.iniciales = iniciales;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ValuacionDTO consultarActual(UUID jugadorId, String estrategiaId, Integer estrategiaVersion) {
        Seleccion seleccion = seleccionar(estrategiaId, estrategiaVersion);
        CotizacionActualEstrategiaEntity actual = actuales
                .findByJugadorIdAndEstrategiaIdAndEstrategiaVersion(jugadorId, seleccion.id(), seleccion.version())
                .orElseThrow(CotizacionNoEncontradaException::new);
        String trace = historicos.findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc(
                jugadorId, seleccion.id(), seleccion.version()).stream().findFirst()
                .map(HistoricoCotizacionEstrategiaEntity::getTrazabilidadJson).orElse("{}");
        return aDTO(actual, trace, visible(seleccion));
    }

    @Override
    @Transactional(readOnly = true)
    public ValuacionHistoricoDTO consultarHistorico(UUID jugadorId, String estrategiaId,
            Integer estrategiaVersion, int page, int limit) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ConsultaValorizacionInvalidaException("La paginación no es válida.");
        }
        Seleccion seleccion = seleccionar(estrategiaId, estrategiaVersion);
        List<HistoricoCotizacionEstrategiaEntity> serie = historicos
                .findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc(
                        jugadorId, seleccion.id(), seleccion.version());
        if (serie.isEmpty()) throw new CotizacionNoEncontradaException();
        int desde = Math.min((page - 1) * limit, serie.size());
        int hasta = Math.min(desde + limit, serie.size());
        List<ValuacionDTO> items = serie.subList(desde, hasta).stream()
                .map(item -> aDTO(item, visible(seleccion))).toList();
        return new ValuacionHistoricoDTO(items, page, limit, serie.size());
    }

    private Seleccion seleccionar(String estrategiaId, Integer estrategiaVersion) {
        if ((estrategiaId == null) != (estrategiaVersion == null)) {
            throw new ConsultaValorizacionInvalidaException("strategyId y strategyVersion deben enviarse juntos.");
        }
        Seleccion seleccion = estrategiaId == null
                ? new Seleccion(visible.obtener().estrategiaId(), visible.obtener().version())
                : new Seleccion(estrategiaId, estrategiaVersion);
        boolean configurada = iniciales.habilitadas().stream().anyMatch(estrategia ->
                estrategia.id().equals(seleccion.id()) && estrategia.version() == seleccion.version());
        boolean persistida = estrategias.findByIdAndVersion(seleccion.id(), seleccion.version())
                .filter(e -> e.isHabilitadaParaCalculo()).isPresent();
        if (!configurada && !persistida) {
            throw new CotizacionNoEncontradaException();
        }
        return seleccion;
    }

    private boolean visible(Seleccion seleccion) {
        EstrategiaVisibleService.SeleccionVisible actual = visible.obtener();
        return actual.estrategiaId().equals(seleccion.id()) && actual.version() == seleccion.version();
    }

    private ValuacionDTO aDTO(CotizacionActualEstrategiaEntity actual, String trace, boolean esVisible) {
        return new ValuacionDTO(publicId(actual.getJugadorId()), actual.getScore(), actual.getCotizacion(),
                actual.getCalculadaAt(), actual.getEstrategiaId(), actual.getEstrategiaVersion(), esVisible,
                tipo(actual.getEstrategiaId()), null, parseTrace(trace));
    }

    private ValuacionDTO aDTO(HistoricoCotizacionEstrategiaEntity historico, boolean esVisible) {
        return new ValuacionDTO(publicId(historico.getJugadorId()), historico.getScore(), historico.getCotizacion(),
                historico.getCalculadaAt(), historico.getEstrategiaId(), historico.getEstrategiaVersion(), esVisible,
                tipo(historico.getEstrategiaId()), null, parseTrace(historico.getTrazabilidadJson()));
    }

    private JsonNode parseTrace(String value) {
        try { return objectMapper.readTree(value == null ? "{}" : value); }
        catch (Exception ignored) { return JsonNodeFactory.instance.objectNode(); }
    }

    private static String publicId(UUID id) { return "ply_" + id; }
    private static String tipo(String id) { return "POSITIONAL".equals(id) ? "POSICIONAL" : "GENERAL"; }
    private record Seleccion(String id, int version) { }
}
