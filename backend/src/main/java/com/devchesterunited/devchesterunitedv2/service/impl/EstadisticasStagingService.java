package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncStagingEntity;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;

/** Escribe páginas en transacciones cortas; no publica identidades ni estadísticas. */
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
    public void registrarInicio(EstadisticasSyncEntity ejecucion) { ejecuciones.save(ejecucion); }

    @Transactional
    public void escribir(UUID syncId, String competicion, UUID jugadorId, UUID equipoId,
            RegistroEstadisticasJugador registro) {
        escribirInterno(syncId, competicion, jugadorId, equipoId, registro);
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

    public java.util.List<EstadisticasSyncStagingEntity> obtener(UUID syncId) {
        return staging.findAllBySyncId(syncId);
    }

    public void validarCompleto(UUID syncId) {
        validarCompleto(syncId, Set.of());
    }

    public void validarCompleto(UUID syncId, Set<String> combinacionesCompletas) {
        long total = 0;
        int page = 0;
        org.springframework.data.domain.Page<EstadisticasSyncStagingEntity> lote;
        do {
            lote = staging.findAllBySyncId(syncId, PageRequest.of(page++, 100));
            total += lote.getNumberOfElements();
            if (lote.getContent().stream().anyMatch(row -> row.getJugadorId() == null
                    || !row.isFamiliaGeneralRecibida() || !row.isFamiliaOfensivaRecibida()
                    || !row.isFamiliaDefensivaRecibida())) {
                throw new IdentidadEstadisticaNoResueltaException(
                        "La ejecución no contiene las tres familias completas.");
            }
        } while (lote.hasNext());
        if (total == 0 && combinacionesCompletas.isEmpty()) {
            throw new IdentidadEstadisticaNoResueltaException("La ejecución no contiene registros.");
        }
        if (!combinacionesCompletas.isEmpty()) {
            long esperadas = com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica.values().length
                    * com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica.values().length;
            if (combinacionesCompletas.size() != esperadas) {
                throw new IdentidadEstadisticaNoResueltaException(
                        "La ejecucion no completo las competiciones y familias requeridas.");
            }
        }
    }

    @Transactional
    public void cambiarEstado(UUID syncId, EstadoEstadisticasSync estado, Instant ahora) {
        var ejecucion = ejecuciones.findById(syncId)
                .orElseThrow(() -> new IllegalStateException("La ejecución no existe."));
        ejecucion.cambiarEstado(estado, null, null);
        ejecuciones.save(ejecucion);
    }
}
