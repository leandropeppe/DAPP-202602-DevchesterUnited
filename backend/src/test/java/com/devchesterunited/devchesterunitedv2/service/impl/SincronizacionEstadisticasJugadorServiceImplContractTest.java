package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.service.exception.EstadisticasProveedorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SincronizacionEstadisticasJugadorServiceImplContractTest {
    @Test
    void solicitaExactamenteLasCincoCompeticionesAntesDePublicar() {
        var fixture = setup(List.of(CompeticionEstadistica.values()));

        fixture.service.sincronizar();

        verify(fixture.provider).obtenerSnapshots(List.of(CompeticionEstadistica.values()));
        verify(fixture.lifecycle).marcarValidada(fixture.syncId);
        verify(fixture.promotion).promover(any(), any(), any(), any());
    }

    @Test
    void competicionFaltanteImpideStagingValidadoYPromocion() {
        var requested = new ArrayList<>(List.of(CompeticionEstadistica.values()));
        requested.remove(CompeticionEstadistica.LIGUE_1);
        var fixture = setup(requested);

        assertThatThrownBy(fixture.service::sincronizar)
                .isInstanceOf(EstadisticasProveedorException.class);

        verify(fixture.staging, never()).escribirLote(any(), any(), any());
        verify(fixture.lifecycle, never()).marcarValidada(any());
        verify(fixture.promotion, never()).promover(any(), any(), any(), any());
    }

    @Test
    void competicionDuplicadaImpideResultadoParcial() {
        var requested = new ArrayList<>(List.of(CompeticionEstadistica.values()));
        requested.set(4, CompeticionEstadistica.PREMIER_LEAGUE);
        var fixture = setup(requested);

        assertThatThrownBy(fixture.service::sincronizar)
                .isInstanceOf(EstadisticasProveedorException.class);

        verify(fixture.staging, never()).escribirLote(any(), any(), any());
        verify(fixture.lifecycle, never()).marcarValidada(any());
        verify(fixture.promotion, never()).promover(any(), any(), any(), any());
    }

    private TestSetup setup(List<CompeticionEstadistica> returnedCompetitions) {
        var provider = mock(ProveedorEstadisticasJugador.class);
        var players = mock(IdentidadJugadorResolver.class);
        var staging = mock(EstadisticasStagingService.class);
        var promotion = mock(EstadisticasPromocionService.class);
        var lock = mock(EstadisticasSyncLock.class);
        var handle = mock(EstadisticasSyncLock.Handle.class);
        var lifecycle = mock(EstadisticasSyncService.class);
        UUID syncId = UUID.randomUUID();
        when(lock.tryAcquire()).thenReturn(Optional.of(handle));
        when(lifecycle.iniciar()).thenReturn(syncId);
        when(provider.obtenerSnapshots(any())).thenReturn(returnedCompetitions.stream()
                .map(competition -> new SnapshotEstadisticasJugador(competition, List.of()))
                .toList());
        return new TestSetup(new SincronizacionEstadisticasJugadorServiceImpl(
                provider, players, staging, promotion, lock, lifecycle), provider, staging, promotion, lifecycle, syncId);
    }

    private record TestSetup(SincronizacionEstadisticasJugadorServiceImpl service,
            ProveedorEstadisticasJugador provider, EstadisticasStagingService staging,
            EstadisticasPromocionService promotion, EstadisticasSyncService lifecycle, UUID syncId) { }
}
