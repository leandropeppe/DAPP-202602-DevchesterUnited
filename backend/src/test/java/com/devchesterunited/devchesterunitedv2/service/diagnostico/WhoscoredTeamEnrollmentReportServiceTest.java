package com.devchesterunited.devchesterunitedv2.service.diagnostico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhoscoredTeamEnrollmentReportServiceTest {
    @Mock ProveedorEstadisticasJugador proveedor;
    @Mock EquipoRepository equipos;
    @Mock IdentidadExternaEquipoRepository identidades;

    @Test
    void deduplicaPorTeamIdYCuentaJugadoresDistintos() {
        var records = List.of(record(10, 77, "Man City"), record(11, 77, "Man City"), record(10, 77, "Man City"));
        var result = WhoscoredTeamEnrollmentReportService.deduplicate(List.of(
                new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE, records)));

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).jugadoresObservados());
    }

    @Test
    void separaElMismoTeamIdEntreCompeticiones() {
        var result = WhoscoredTeamEnrollmentReportService.deduplicate(List.of(
                new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE,
                        List.of(record(10, 77, "City"))),
                new SnapshotEstadisticasJugador(CompeticionEstadistica.LA_LIGA,
                        List.of(record(20, 77, "City")))));

        assertEquals(2, result.size());
        assertEquals(CompeticionEstadistica.PREMIER_LEAGUE, result.get(0).competicion());
        assertEquals(CompeticionEstadistica.LA_LIGA, result.get(1).competicion());
    }

    @Test
    void mapeaLaLigaAlNombreDeLigaLocal() {
        assertEquals("LaLiga", WhoscoredTeamEnrollmentReportService.ligaDe(CompeticionEstadistica.LA_LIGA));
    }

    @Test
    void rankeaNombreCortoYNombreSimilarSinAprobar() {
        var primero = equipo("Manchester City FC", "Man City", "MCI");
        var segundo = equipo("Manchester United FC", "Man United", "MUN");
        var observed = new WhoscoredTeamEnrollmentReportService.ObservedTeam(
                CompeticionEstadistica.PREMIER_LEAGUE, 77, "Man City", 10);

        var candidates = WhoscoredTeamEnrollmentReportService.rankCandidates(observed,
                List.of(segundo, primero), Map.of(primero.getId(), 999L));

        assertEquals(primero.getId(), candidates.get(0).uuid());
        assertTrue(candidates.get(0).motivo().contains("nombre_corto_exacto"));
    }

    @Test
    void generaUnaFilaPorEquipoYDejaCamposManualesVacios(@TempDir Path temp) throws Exception {
        var observed = new SnapshotEstadisticasJugador(CompeticionEstadistica.LA_LIGA,
                List.of(record(10, 77, "Man City"), record(11, 77, "Man City")));
        when(proveedor.obtenerSnapshots(WhoscoredTeamEnrollmentReportService.COMPETICIONES)).thenReturn(List.of(observed));
        when(equipos.findAllByActivoTrueAndLigaIgnoreCase(anyString())).thenReturn(List.of());
        when(equipos.findAllByActivoTrueAndLigaIgnoreCase("LaLiga")).thenReturn(List.of(equipo("Manchester City FC", "Man City", "MCI")));
        when(identidades.findAllByProveedor(ProveedorIdentidadExterna.FOOTBALL_DATA)).thenReturn(List.of());
        when(identidades.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).thenReturn(List.of());

        Path output = temp.resolve("report.csv");
        var result = new WhoscoredTeamEnrollmentReportService(proveedor, equipos, identidades).generar(output);
        List<String> lines = Files.readAllLines(output);

        assertEquals(1, result.totalTeams());
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("decision_manual"));
        assertTrue(lines.get(1).endsWith(",\"\",\"\""));
        verify(identidades, never()).save(any());
        verify(identidades, never()).saveAll(any());
        verify(equipos, never()).save(any());
        verify(equipos, never()).saveAll(any());
    }

    @Test
    void siFallaElRankingDeUnEquipoPosteriorNoCreaCsvFinal(@TempDir Path temp) throws Exception {
        var first = new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE,
                List.of(record(10, 77, "City")));
        var second = new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE,
                List.of(record(11, 88, "United")));
        when(proveedor.obtenerSnapshots(WhoscoredTeamEnrollmentReportService.COMPETICIONES)).thenReturn(List.of(first, second));
        when(equipos.findAllByActivoTrueAndLigaIgnoreCase(anyString())).thenReturn(List.of());
        when(identidades.findAllByProveedor(any(ProveedorIdentidadExterna.class))).thenReturn(List.of());

        Path output = temp.resolve("report.csv");
        var service = new WhoscoredTeamEnrollmentReportService(proveedor, equipos, identidades) {
            @Override
            protected List<Candidate> rankCandidatesForReport(ObservedTeam observed, List<EquipoEntity> localTeams,
                    Map<UUID, Long> footballDataIds) {
                if (observed.teamId() == 88) throw new IllegalStateException("fallo de ranking simulado");
                return super.rankCandidatesForReport(observed, localTeams, footballDataIds);
            }
        };

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.generar(output));
        assertFalse(Files.exists(output));
    }

    private static RegistroEstadisticasJugador record(long playerId, long teamId, String teamName) {
        return new RegistroEstadisticasJugador(new IdentidadEstadisticaJugador(
                playerId, "Player " + playerId, null, null, teamId, teamName),
                FamiliaEstadistica.GENERAL, new MetricasEstadisticasJugador(null, null, null, null, null, null, null, null, null, null));
    }

    private static EquipoEntity equipo(String name, String shortName, String tla) {
        return EquipoEntity.builder().id(UUID.randomUUID()).nombre(name).nombreCorto(shortName).sigla(tla)
                .liga("Premier League").activo(true).build();
    }
}
