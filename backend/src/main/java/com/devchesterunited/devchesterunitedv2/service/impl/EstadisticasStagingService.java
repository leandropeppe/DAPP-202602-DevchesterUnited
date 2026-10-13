package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncStagingEntity;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes technical staging in short transactions before atomic promotion. */
@Service
public class EstadisticasStagingService {
    private final EstadisticasSyncRepository ejecuciones;
    private final EstadisticasSyncStagingRepository staging;
    private final ConsolidadorEstadisticasJugador consolidador;

    public EstadisticasStagingService(EstadisticasSyncRepository ejecuciones,
            EstadisticasSyncStagingRepository staging,
            ConsolidadorEstadisticasJugador consolidador) {
        this.ejecuciones = ejecuciones;
        this.staging = staging;
        this.consolidador = consolidador;
    }

    @Transactional
    public void registrarInicio(EstadisticasSyncEntity ejecucion) {
        ejecuciones.save(ejecucion);
    }

    @Transactional
    public void escribirLote(UUID syncId, String competicion, List<Entrada> entradas) {
        entradas.forEach(entrada -> escribirInterno(syncId, competicion, entrada.jugadorId(),
                entrada.equipoId(), entrada.registro()));
    }

    private void escribirInterno(UUID syncId, String competicion, UUID jugadorId, UUID equipoId,
            RegistroEstadisticasJugador registro) {
        var identidad = registro.identidad();
        var entity = staging.findBySyncIdAndCompeticionAndWhoscoredPlayerId(
                syncId, competicion, identidad.playerId())
                .orElseGet(() -> new EstadisticasSyncStagingEntity(UUID.randomUUID(), syncId,
                        competicion, identidad.playerId(), identidad.nombre()));
        var m = consolidador.conservar(
                new com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador(
                        entity.getMinutosJugados(), entity.getGoles(), entity.getAsistencias(), entity.getTiros(),
                        entity.getPasesClave(), entity.getRegates(), entity.getTackles(), entity.getValoracion(),
                        entity.getTarjetasAmarillas(), entity.getTarjetasRojas()), registro.metricas());
        String posicion = identidad.posicion() != null ? identidad.posicion() : entity.getPosicion();
        entity.aplicar(jugadorId, equipoId, identidad.teamId(), identidad.nombreEquipo(),
                posicion, identidad.nacionalidad(), registro.familia().name(),
                m.minutosJugados(), m.goles(), m.asistencias(), m.tiros(), m.pasesClave(),
                m.regates(), m.tackles(), m.valoracion(), m.tarjetasAmarillas(), m.tarjetasRojas());
        staging.save(entity);
    }

    public record Entrada(UUID jugadorId, UUID equipoId, RegistroEstadisticasJugador registro) { }

    public void validarCompeticionesCompletas(UUID syncId, Set<String> competicionesEsperadas) {
        if (competicionesEsperadas == null || competicionesEsperadas.isEmpty()) {
            throw new IdentidadEstadisticaNoResueltaException(
                    "La ejecucion no contiene competiciones completas.");
        }
        Set<String> competicionesRecibidas = new HashSet<>();
        long total = 0;
        int page = 0;
        org.springframework.data.domain.Page<EstadisticasSyncStagingEntity> lote;
        do {
            lote = staging.findAllBySyncId(syncId, PageRequest.of(page++, 100));
            total += lote.getNumberOfElements();
            for (var row : lote.getContent()) {
                if (row.getJugadorId() == null) {
                    throw new IdentidadEstadisticaNoResueltaException(
                            "La ejecucion contiene identidades sin resolver.");
                }
                competicionesRecibidas.add(row.getCompeticion());
            }
        } while (lote.hasNext());
        if (total == 0 || !competicionesRecibidas.equals(competicionesEsperadas)) {
            throw new IdentidadEstadisticaNoResueltaException(
                    "La ejecucion no completo las competiciones requeridas.");
        }
    }

    @Transactional
    public void cambiarEstado(UUID syncId, EstadoEstadisticasSync estado, Instant ahora) {
        var ejecucion = ejecuciones.findById(syncId)
                .orElseThrow(() -> new IllegalStateException("La ejecucion no existe."));
        ejecucion.cambiarEstado(estado, null, null);
        ejecuciones.save(ejecucion);
    }
}
