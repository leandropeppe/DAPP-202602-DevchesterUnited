package com.devchesterunited.devchesterunitedv2.scraping.catalogo;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.WhoscoredTeamEnrollmentReportService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import java.io.IOException;
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
        var records = List.of(crearRegistro(10, 77, "Man City"), crearRegistro(11, 77, "Man City"), crearRegistro(10, 77, "Man City"));
        var result = WhoscoredTeamEnrollmentReportService.deduplicate(List.of(
                new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE, records)));

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).jugadoresObservados());
    }

    @Test
    void separaElMismoTeamIdEntreCompeticiones() {
        var result = WhoscoredTeamEnrollmentReportService.deduplicate(List.of(
                new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE,
                        List.of(crearRegistro(10, 77, "City"))),
                new SnapshotEstadisticasJugador(CompeticionEstadistica.LA_LIGA,
                        List.of(crearRegistro(20, 77, "City")))));

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
    void generaUnaFilaPorEquipoYDejaCamposManualesVacios(@TempDir Path temp) throws IOException {
        var observed = new SnapshotEstadisticasJugador(CompeticionEstadistica.LA_LIGA,
                List.of(crearRegistro(10, 77, "Man City"), crearRegistro(11, 77, "Man City")));
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
    void siFallaElRankingDeUnEquipoPosteriorNoCreaCsvFinal(@TempDir Path temp) {
        var first = new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE,
                List.of(crearRegistro(10, 77, "City")));
        var second = new SnapshotEstadisticasJugador(CompeticionEstadistica.PREMIER_LEAGUE,
                List.of(crearRegistro(11, 88, "United")));
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

    private static RegistroEstadisticasJugador crearRegistro(long playerId, long teamId, String teamName) {
        return new RegistroEstadisticasJugador(new IdentidadEstadisticaJugador(
                playerId, "Player " + playerId, null, null, teamId, teamName),
                FamiliaEstadistica.GENERAL, new MetricasEstadisticasJugador(null, null, null, null, null, null, null, null, null, null));
    }

    private static EquipoEntity equipo(String name, String shortName, String tla) {
        return EquipoEntity.builder().id(UUID.randomUUID()).nombre(name).nombreCorto(shortName).sigla(tla)
                .liga("Premier League").activo(true).build();
    }
}
