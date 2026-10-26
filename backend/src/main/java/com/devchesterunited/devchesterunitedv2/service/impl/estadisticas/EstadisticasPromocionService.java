package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncStagingEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.jugador.IdentidadEquipoResolver;
import com.devchesterunited.devchesterunitedv2.service.impl.jugador.IdentidadJugadorResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Promoción atómica de la fotografía y de las identidades nuevas.
 *
 * Consume únicamente staging validado y confirma estadísticas, identidades
 * nuevas y estado {@code PUBLICADA} en la misma transacción; un fallo revierte
 * toda la publicación y conserva el snapshot anterior.
 */
@Service
public class EstadisticasPromocionService {
    private final EstadisticasJugadorRepository estadisticas;
    private final EstadisticasSyncRepository ejecuciones;
    private final IdentidadExternaJugadorRepository jugadores;
    private final IdentidadExternaEquipoRepository equipos;
    private final EstadisticasSyncStagingRepository staging;
    private final Clock clock;

    public EstadisticasPromocionService(EstadisticasJugadorRepository estadisticas,
            EstadisticasSyncRepository ejecuciones, IdentidadExternaJugadorRepository jugadores,
            IdentidadExternaEquipoRepository equipos, EstadisticasSyncStagingRepository staging, Clock clock) {
        this.estadisticas = estadisticas;
        this.ejecuciones = ejecuciones;
        this.jugadores = jugadores;
        this.equipos = equipos;
        this.staging = staging;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public void promover(UUID syncId, Collection<IdentidadJugadorResolver.NuevaIdentidad> nuevasJugadores,
            Collection<IdentidadEquipoResolver.NuevaIdentidad> nuevosEquipos,
            Map<UUID, EstadisticasJugadorEntity> ignored) {
        Instant now = Instant.now(clock);
        EstadisticasSyncEntity ejecucion = ejecuciones.findById(syncId)
                .orElseThrow(() -> new IllegalStateException("La ejecución no existe."));
        if (ejecucion.getEstado() != EstadoEstadisticasSync.VALIDADA) {
            throw new IllegalStateException("La ejecución no está validada para promoción.");
        }
        nuevosEquipos.forEach(identity -> equipos.findByProveedorAndExternalId(
                ProveedorIdentidadExterna.WHOSCORED, identity.externalId()).orElseGet(() ->
                equipos.save(new com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity(
                        UUID.randomUUID(), identity.equipo(), ProveedorIdentidadExterna.WHOSCORED,
                        identity.externalId()))));
        nuevasJugadores.forEach(identity -> jugadores.findByProveedorAndExternalId(
                ProveedorIdentidadExterna.WHOSCORED, identity.externalId()).orElseGet(() ->
                jugadores.save(new com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity(
                        UUID.randomUUID(), identity.jugador(), ProveedorIdentidadExterna.WHOSCORED,
                        identity.externalId()))));
        int page = 0;
        Page<EstadisticasSyncStagingEntity> lote;
        do {
            lote = staging.findAllBySyncId(syncId, PageRequest.of(page++, 100));
            for (EstadisticasSyncStagingEntity row : lote.getContent()) {
                EstadisticasJugadorEntity entity = estadisticas.findByJugadorId(row.getJugadorId())
                        .orElseGet(() -> new EstadisticasJugadorEntity(
                                EstadisticasJugadorEntity.Creacion.builder()
                                        .id(UUID.randomUUID()).jugadorId(row.getJugadorId())
                                        .posicion(row.getPosicion()).minutosJugados(row.getMinutosJugados())
                                        .goles(row.getGoles()).asistencias(row.getAsistencias())
                                        .tiros(row.getTiros()).pasesClave(row.getPasesClave())
                                        .regates(row.getRegates()).tackles(row.getTackles())
                                        .valoracion(row.getValoracion())
                                        .tarjetasAmarillas(row.getTarjetasAmarillas())
                                        .tarjetasRojas(row.getTarjetasRojas())
                                        .apps(row.getApps()).subOn(row.getSubOn()).manOfTheMatch(row.getManOfTheMatch())
                                        .aerialWonPerGame(row.getAerialWonPerGame())
                                        .foulGivenPerGame(row.getFoulGivenPerGame()).offsideGivenPerGame(row.getOffsideGivenPerGame())
                                        .dispossessedPerGame(row.getDispossessedPerGame()).turnoverPerGame(row.getTurnoverPerGame())
                                        .interceptionPerGame(row.getInterceptionPerGame()).foulsPerGame(row.getFoulsPerGame())
                                        .offsideWonPerGame(row.getOffsideWonPerGame()).clearancePerGame(row.getClearancePerGame())
                                        .wasDribbledPerGame(row.getWasDribbledPerGame()).outfielderBlockPerGame(row.getOutfielderBlockPerGame())
                                        .goalOwn(row.getGoalOwn()).totalPassesPerGame(row.getTotalPassesPerGame())
                                        .passSuccess(row.getPassSuccess()).accurateCrossesPerGame(row.getAccurateCrossesPerGame())
                                        .accurateLongPassPerGame(row.getAccurateLongPassPerGame()).accurateThroughBallPerGame(row.getAccurateThroughBallPerGame())
                                        .syncId(syncId).creadoEn(now).actualizadoEn(now)));
                entity.actualizar(EstadisticasJugadorEntity.Actualizacion.builder()
                        .posicion(row.getPosicion()).minutos(row.getMinutosJugados())
                        .goles(row.getGoles()).asistencias(row.getAsistencias())
                        .tiros(row.getTiros()).pasesClave(row.getPasesClave())
                        .regates(row.getRegates()).tackles(row.getTackles())
                        .valoracion(row.getValoracion()).amarillas(row.getTarjetasAmarillas())
                        .rojas(row.getTarjetasRojas()).apps(row.getApps()).subOn(row.getSubOn())
                        .manOfTheMatch(row.getManOfTheMatch()).aerialWonPerGame(row.getAerialWonPerGame())
                        .foulGivenPerGame(row.getFoulGivenPerGame()).offsideGivenPerGame(row.getOffsideGivenPerGame())
                        .dispossessedPerGame(row.getDispossessedPerGame()).turnoverPerGame(row.getTurnoverPerGame())
                        .interceptionPerGame(row.getInterceptionPerGame()).foulsPerGame(row.getFoulsPerGame())
                        .offsideWonPerGame(row.getOffsideWonPerGame()).clearancePerGame(row.getClearancePerGame())
                        .wasDribbledPerGame(row.getWasDribbledPerGame()).outfielderBlockPerGame(row.getOutfielderBlockPerGame())
                        .goalOwn(row.getGoalOwn()).totalPassesPerGame(row.getTotalPassesPerGame())
                        .passSuccess(row.getPassSuccess()).accurateCrossesPerGame(row.getAccurateCrossesPerGame())
                        .accurateLongPassPerGame(row.getAccurateLongPassPerGame()).accurateThroughBallPerGame(row.getAccurateThroughBallPerGame())
                        .syncId(syncId).ahora(now));
                estadisticas.save(entity);
            }
        } while (lote.hasNext());
        ejecucion.cambiarEstado(EstadoEstadisticasSync.PUBLICADA, now, null);
        ejecuciones.save(ejecucion);
    }
}
