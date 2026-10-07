package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Orquesta red, staging y promoción sin compartir una transacción con el Adapter. */
@Service
public class SincronizacionEstadisticasJugadorServiceImpl
        implements SincronizacionEstadisticasJugadorService {
    private final ProveedorEstadisticasJugador proveedor;
    private final IdentidadJugadorResolver jugadores;
    private final EstadisticasSyncRepository ejecuciones;
    private final EstadisticasJugadorRepository estadisticas;
    private final EstadisticasStagingService staging;
    private final EstadisticasPromocionService promocion;
    private final EstadisticasSyncLock lock;
    private final Clock clock;
    private final int tamanoLote;

    public SincronizacionEstadisticasJugadorServiceImpl(ProveedorEstadisticasJugador proveedor,
            IdentidadJugadorResolver jugadores, EstadisticasSyncRepository ejecuciones,
            EstadisticasJugadorRepository estadisticas, EstadisticasStagingService staging,
            EstadisticasPromocionService promocion, EstadisticasSyncLock lock, Clock clock,
            @Value("${whoscored.page-size:50}") int tamanoLote) {
        this.proveedor = proveedor;
        this.jugadores = jugadores;
        this.ejecuciones = ejecuciones;
        this.estadisticas = estadisticas;
        this.staging = staging;
        this.promocion = promocion;
        this.lock = lock;
        this.clock = clock;
        this.tamanoLote = tamanoLote;
    }

    @Override
    public void sincronizar() {
        try (var ignored = lock.tryAcquire().orElseThrow(SincronizacionEnCursoException::new)) {
            UUID syncId = UUID.randomUUID();
            staging.registrarInicio(new EstadisticasSyncEntity(syncId, EstadoEstadisticasSync.INICIADA,
                    Instant.now(clock)));
            staging.cambiarEstado(syncId, EstadoEstadisticasSync.RECIBIENDO, Instant.now(clock));
            Map<Long, IdentidadJugadorResolver.NuevaIdentidad> nuevasJugadores = new LinkedHashMap<>();
            Map<Long, IdentidadEquipoResolver.NuevaIdentidad> nuevosEquipos = new LinkedHashMap<>();
            java.util.Set<String> combinacionesCompletas = new java.util.HashSet<>();
            for (CompeticionEstadistica competicion : CompeticionEstadistica.values()) {
                for (FamiliaEstadistica familia : FamiliaEstadistica.values()) {
                    int pagina = 1;
                    Integer totalPaginas = null;
                    do {
                        var page = proveedor.obtenerPagina(competicion, familia, pagina, tamanoLote);
                        if (page.competicion() != competicion || page.familia() != familia
                                || page.paginaActual() != pagina) {
                            throw new IllegalStateException("La fuente devolvió una página inconsistente.");
                        }
                        if (totalPaginas == null) {
                            totalPaginas = page.totalPaginas();
                        } else if (totalPaginas != page.totalPaginas()) {
                            throw new IllegalStateException("La fuente devolviÃ³ metadata de paginaciÃ³n inestable.");
                        }
                        var loteStaging = new ArrayList<EstadisticasStagingService.Entrada>();
                        for (var registro : page.registros()) {
                            var identidad = jugadores.resolver(registro.identidad(), competicion);
                            if (identidad.identidadNueva() != null) {
                                nuevasJugadores.putIfAbsent(registro.identidad().playerId(), identidad.identidadNueva());
                            }
                            if (identidad.equipoNueva() != null) {
                                nuevosEquipos.putIfAbsent(identidad.equipoNueva().externalId(), identidad.equipoNueva());
                            }
                            loteStaging.add(new EstadisticasStagingService.Entrada(
                                    identidad.jugadorId(), identidad.equipoId(), registro));
                        }
                        staging.escribirLote(syncId, competicion.name(), loteStaging);
                        pagina++;
                    } while (pagina <= totalPaginas);
                    combinacionesCompletas.add(competicion.name() + ":" + familia.name());
                }
            }
            staging.cambiarEstado(syncId, EstadoEstadisticasSync.VALIDADA, Instant.now(clock));
            validarStaging(syncId, combinacionesCompletas);
            promocion.promover(syncId, nuevasJugadores.values(), nuevosEquipos.values(), Map.of());
        }
    }

    private void validarStaging(UUID syncId, java.util.Set<String> combinacionesCompletas) {
        staging.validarCompleto(syncId, combinacionesCompletas);
    }
}
