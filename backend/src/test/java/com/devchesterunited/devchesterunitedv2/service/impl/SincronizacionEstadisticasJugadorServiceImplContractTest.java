package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.EstadisticasProveedorException;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

class SincronizacionEstadisticasJugadorServiceImplContractTest {
    @Test
    void resuelveCadaTeamIdUnicoUnaSolaVezAntesDeResolverJugadores() {
        var fixture = setup(List.of(CompeticionEstadistica.values()));
        when(fixture.provider.obtenerSnapshots(any())).thenReturn(snapshotsConDosRegistros());
        when(fixture.teams.resolver(any(), any(), any())).thenReturn(
                new IdentidadEquipoResolver.Resultado(UUID.randomUUID(), null, "Equipo", equipo(201L)),
                new IdentidadEquipoResolver.Resultado(UUID.randomUUID(), null, "Equipo", equipo(202L)));
        when(fixture.players.resolver(any(), any(), any())).thenReturn(
                new IdentidadJugadorResolver.Resultado(UUID.randomUUID(), null, UUID.randomUUID(), null));

        fixture.service.sincronizar();

        verify(fixture.teams, times(2)).resolver(any(), any(), any());
        verify(fixture.players, times(2)).resolver(any(), any(), any());
    }

    @Test
    void equipoNoResueltoImpideJugadoresStagingYPromocion() {
        var fixture = setup(List.of(CompeticionEstadistica.values()));
        when(fixture.provider.obtenerSnapshots(any())).thenReturn(snapshotsConDosRegistros());
        var fallo = new IdentidadEstadisticaNoResueltaException(diagnosticoEquipo(201L));
        when(fixture.teams.resolver(any(), any(), any())).thenThrow(fallo);

        assertThatThrownBy(fixture.service::sincronizar)
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);

        verify(fixture.players, never()).resolver(any(), any(), any());
        verify(fixture.staging, never()).escribirLote(any(), any(), any());
        verify(fixture.promotion, never()).promover(any(), any(), any(), any());
    }

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

    @Test
    void omiteFallosIndividualesPeroPublicaLosJugadoresValidos() {
        var fixture = setup(List.of(CompeticionEstadistica.values()));
        var primero = diagnostico(101L, "Jugador A");
        var segundo = diagnostico(102L, "Jugador B");
        when(fixture.teams.resolver(any(), any(), any())).thenReturn(
                new IdentidadEquipoResolver.Resultado(UUID.randomUUID(), null, "Equipo", equipo(201L)),
                new IdentidadEquipoResolver.Resultado(UUID.randomUUID(), null, "Equipo", equipo(202L)));
        when(fixture.players.resolver(any(), any(), any())).thenThrow(
                new IdentidadEstadisticaNoResueltaException(primero),
                new IdentidadEstadisticaNoResueltaException(segundo));
        when(fixture.provider.obtenerSnapshots(any())).thenReturn(snapshotsConDosRegistros());

        var resultado = fixture.service.sincronizar();

        assertThat(resultado.omitidos()).isEqualTo(2);
        assertThat(resultado.diagnosticos()).containsExactly(primero, segundo);
        verify(fixture.staging, times(5)).escribirLote(any(), any(), any());
        verify(fixture.promotion).promover(any(), any(), any(), any());
    }

    private TestSetup setup(List<CompeticionEstadistica> returnedCompetitions) {
        var provider = mock(ProveedorEstadisticasJugador.class);
        var teams = mock(IdentidadEquipoResolver.class);
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
                provider, teams, players, staging, promotion, lock, lifecycle), provider, teams, players, staging, promotion, lifecycle, syncId);
    }

    private List<SnapshotEstadisticasJugador> snapshotsConDosRegistros() {
        var metricas = new MetricasEstadisticasJugador(1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 0, 0);
        var registros = List.of(
                new RegistroEstadisticasJugador(new IdentidadEstadisticaJugador(101L, "Jugador A",
                        null, null, 201L, "Equipo A"), FamiliaEstadistica.GENERAL, metricas),
                new RegistroEstadisticasJugador(new IdentidadEstadisticaJugador(102L, "Jugador B",
                        null, null, 202L, "Equipo B"), FamiliaEstadistica.GENERAL, metricas));
        return List.of(new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE, registros),
                new SnapshotEstadisticasJugador(CompeticionEstadistica.LA_LIGA, List.of()),
                new SnapshotEstadisticasJugador(CompeticionEstadistica.SERIE_A, List.of()),
                new SnapshotEstadisticasJugador(CompeticionEstadistica.BUNDESLIGA, List.of()),
                new SnapshotEstadisticasJugador(CompeticionEstadistica.LIGUE_1, List.of()));
    }

    private IdentidadEstadisticaNoResueltaException.Diagnostico diagnostico(long id, String nombre) {
        return new IdentidadEstadisticaNoResueltaException.Diagnostico(
                IdentidadEstadisticaNoResueltaException.Tipo.JUGADOR, "WHOSCORED", id, nombre,
                "PREMIER_LEAGUE", "Equipo", "Equipo local", null, null,
                IdentidadEstadisticaNoResueltaException.Motivo.SIN_CANDIDATOS);
    }

    private IdentidadEstadisticaNoResueltaException.Diagnostico diagnosticoEquipo(long id) {
        return new IdentidadEstadisticaNoResueltaException.Diagnostico(
                IdentidadEstadisticaNoResueltaException.Tipo.EQUIPO, "WHOSCORED", id, "Equipo",
                "PREMIER_LEAGUE", "Equipo", null, null, null,
                IdentidadEstadisticaNoResueltaException.Motivo.SIN_CANDIDATOS);
    }

    private EquipoEntity equipo(long id) {
        return EquipoEntity.builder().id(UUID.randomUUID()).nombre("Equipo " + id).liga("Premier League")
                .activo(true).creadoEn(Instant.now()).actualizadoEn(Instant.now()).build();
    }

    private record TestSetup(SincronizacionEstadisticasJugadorServiceImpl service,
            ProveedorEstadisticasJugador provider, IdentidadEquipoResolver teams, IdentidadJugadorResolver players, EstadisticasStagingService staging,
            EstadisticasPromocionService promotion, EstadisticasSyncService lifecycle, UUID syncId) { }
}
