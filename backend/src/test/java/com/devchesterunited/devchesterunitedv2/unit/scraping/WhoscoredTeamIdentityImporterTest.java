package com.devchesterunited.devchesterunitedv2.scraping.catalogo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.WhoscoredTeamIdentityImporter;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.WhoscoredTeamIdentityPublicationService;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.WhoscoredTeamIdentityCsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhoscoredTeamIdentityImporterTest {
    @Mock private IdentidadExternaEquipoRepository identities;
    private WhoscoredTeamIdentityImporter importer;
    private List<WhoscoredTeamIdentityCsv.Mapping> mappings;
    private List<EquipoEntity> teams;

    @BeforeEach
    void setUp() {
        importer = new WhoscoredTeamIdentityImporter(new WhoscoredTeamIdentityPublicationService(identities));
        mappings = new ArrayList<>();
        teams = new ArrayList<>();
        for (long i = 1; i <= 96; i++) {
            teams.add(team(true));
            mappings.add(new WhoscoredTeamIdentityCsv.Mapping(i, 10_000 + i));
        }
        for (int i = 0; i < 96; i++) {
            var team = teams.get(i);
            var fd = identity(team, ProveedorIdentidadExterna.FOOTBALL_DATA, 10_001L + i);
            lenient().when(identities.findByProveedorAndExternalId(ProveedorIdentidadExterna.FOOTBALL_DATA,
                    10_001L + i)).thenReturn(Optional.of(fd));
            lenient().when(identities.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, i + 1L))
                    .thenReturn(Optional.empty());
            lenient().when(identities.findByEquipo_IdAndProveedor(team.getId(), ProveedorIdentidadExterna.WHOSCORED))
                    .thenReturn(Optional.empty());
        }
    }

    @Test
    void valid96MappingsInsertan96() {
        var result = importer.publish(mappings);

        assertThat(result).extracting(WhoscoredTeamIdentityImporter.ImportResult::total,
                WhoscoredTeamIdentityImporter.ImportResult::inserted,
                WhoscoredTeamIdentityImporter.ImportResult::alreadyExisting)
                .containsExactly(96, 96, 0);
        verify(identities, times(96)).save(any());
    }

    @Test
    void segundaEjecucionEsIdempotente() {
        for (int i = 0; i < 96; i++) {
            var existing = identity(teams.get(i), ProveedorIdentidadExterna.WHOSCORED, i + 1L);
            when(identities.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, i + 1L))
                    .thenReturn(Optional.of(existing));
        }

        var result = importer.publish(mappings);

        assertThat(result.inserted()).isZero();
        assertThat(result.alreadyExisting()).isEqualTo(96);
        verify(identities, never()).save(any());
    }

    @Test
    void fdInexistenteAbortaSinInsertar() {
        when(identities.findByProveedorAndExternalId(ProveedorIdentidadExterna.FOOTBALL_DATA, 10_001L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> importer.publish(mappings)).hasMessageContaining("Football-Data");
        verify(identities, never()).save(any());
    }

    @Test
    void wsApuntandoAOtroEquipoAborta() {
        when(identities.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 1L))
                .thenReturn(Optional.of(identity(team(true), ProveedorIdentidadExterna.WHOSCORED, 1L)));

        assertThatThrownBy(() -> importer.publish(mappings)).hasMessageContaining("otro equipo");
        verify(identities, never()).save(any());
    }

    @Test
    void equipoConOtroWsAborta() {
        when(identities.findByEquipo_IdAndProveedor(teams.get(0).getId(), ProveedorIdentidadExterna.WHOSCORED))
                .thenReturn(Optional.of(identity(teams.get(0), ProveedorIdentidadExterna.WHOSCORED, 999L)));

        assertThatThrownBy(() -> importer.publish(mappings)).hasMessageContaining("otro WhoScored");
        verify(identities, never()).save(any());
    }

    @Test
    void equipoInactivoAborta() {
        var inactive = team(false);
        when(identities.findByProveedorAndExternalId(ProveedorIdentidadExterna.FOOTBALL_DATA, 10_001L))
                .thenReturn(Optional.of(identity(inactive, ProveedorIdentidadExterna.FOOTBALL_DATA, 10_001L)));

        assertThatThrownBy(() -> importer.publish(mappings)).hasMessageContaining("inactivo");
        verify(identities, never()).save(any());
    }

    @Test
    void duplicadosYIdsInvalidosAbortaAntesDeConsultarBd() {
        var duplicate = new ArrayList<>(mappings);
        duplicate.set(1, new WhoscoredTeamIdentityCsv.Mapping(1, 20_001));
        assertThatThrownBy(() -> importer.publish(duplicate)).hasMessageContaining("WhoScored duplicado");

        var duplicateFootballData = new ArrayList<>(mappings);
        duplicateFootballData.set(1, new WhoscoredTeamIdentityCsv.Mapping(2, 10_001));
        assertThatThrownBy(() -> importer.publish(duplicateFootballData)).hasMessageContaining("Football-Data duplicado");

        var invalid = new ArrayList<>(mappings);
        invalid.set(0, new WhoscoredTeamIdentityCsv.Mapping(0, 10_001));
        assertThatThrownBy(() -> importer.publish(invalid)).hasMessageContaining("mayores que cero");
        verify(identities, never()).save(any());
    }

    @Test
    void falloDePersistenciaSePropagaYNoHayPublicacionParcialDesdeElServicio() {
        doThrow(new IllegalStateException("fallo de persistencia")).when(identities).save(any());

        assertThatThrownBy(() -> importer.publish(mappings)).hasMessageContaining("fallo de persistencia");
    }

    private static EquipoEntity team(boolean active) {
        Instant now = Instant.now();
        return EquipoEntity.builder().id(UUID.randomUUID()).nombre("Equipo").liga("Liga")
                .activo(active).creadoEn(now).actualizadoEn(now).build();
    }

    private static IdentidadExternaEquipoEntity identity(EquipoEntity team,
            ProveedorIdentidadExterna provider, long externalId) {
        return new IdentidadExternaEquipoEntity(UUID.randomUUID(), team, provider, externalId);
    }
}
