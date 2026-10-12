package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadEquipoResolver;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentidadEquipoResolverTest {
    @Mock IdentidadExternaEquipoRepository identidades;

    @Test
    void resuelveSoloIdentidadPersistidaWhoscored() {
        var equipo = equipo("Club local", true);
        when(identidades.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 63L))
                .thenReturn(Optional.of(new IdentidadExternaEquipoEntity(UUID.randomUUID(), equipo,
                        ProveedorIdentidadExterna.WHOSCORED, 63L)));

        var resultado = resolver().resolver(63L, "Nombre diferente", CompeticionEstadistica.LA_LIGA);

        assertThat(resultado.equipo()).isSameAs(equipo);
        assertThat(resultado.identidadNueva()).isNull();
    }

    @Test
    void identidadAusenteFallaSinConsultarEquiposNiHacerMatchingNominal() {
        when(identidades.findByProveedorAndExternalId(any(), eq(228L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolver(228L, "Olympique Lyonnais", CompeticionEstadistica.LIGUE_1))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class)
                .satisfies(error -> assertThat(((IdentidadEstadisticaNoResueltaException) error)
                        .diagnosticos().get(0).motivo())
                        .isEqualTo(IdentidadEstadisticaNoResueltaException.Motivo.SIN_CANDIDATOS));
        verify(identidades).findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 228L);
    }

    @Test
    void identidadQueApuntaAEquipoInactivoFalla() {
        var equipo = equipo("Inactivo", false);
        when(identidades.findByProveedorAndExternalId(any(), eq(309L))).thenReturn(Optional.of(
                new IdentidadExternaEquipoEntity(UUID.randomUUID(), equipo,
                        ProveedorIdentidadExterna.WHOSCORED, 309L)));

        assertThatThrownBy(() -> resolver().resolver(309L, "Lens", CompeticionEstadistica.LIGUE_1))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class)
                .satisfies(error -> assertThat(((IdentidadEstadisticaNoResueltaException) error)
                        .diagnosticos().get(0).motivo())
                        .isEqualTo(IdentidadEstadisticaNoResueltaException.Motivo.EQUIPO_INACTIVO));
    }

    private IdentidadEquipoResolver resolver() { return new IdentidadEquipoResolver(identidades); }

    private static EquipoEntity equipo(String nombre, boolean activo) {
        Instant now = Instant.now();
        return EquipoEntity.builder().id(UUID.randomUUID()).nombre(nombre).liga("Ligue 1")
                .activo(activo).creadoEn(now).actualizadoEn(now).build();
    }
}
