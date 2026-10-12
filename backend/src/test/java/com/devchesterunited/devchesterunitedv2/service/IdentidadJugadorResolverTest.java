package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadJugadorResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentidadJugadorResolverTest {
    @Mock JugadorRepository jugadores;
    @Mock IdentidadExternaJugadorRepository identidades;

    @Test
    void recibeEquipoResueltoYBuscaCandidatosSoloEnEseEquipo() {
        var equipo = equipo(UUID.randomUUID(), "Equipo A");
        var jugador = new JugadorEntity(UUID.randomUUID(), "Juan Perez", equipo.getId(),
                "Forward", 9, "Argentina", true);
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(jugadores.findAllByEquipoIdAndActivoTrue(equipo.getId())).thenReturn(List.of(jugador));

        var resultado = resolver().resolver(hint(), equipo, CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(resultado.jugadorId()).isEqualTo(jugador.getId());
        verify(jugadores).findAllByEquipoIdAndActivoTrue(equipo.getId());
    }

    @Test
    void nuncaResuelveEquipo() {
        var equipo = equipo(UUID.randomUUID(), "Equipo A");
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(jugadores.findAllByEquipoIdAndActivoTrue(equipo.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> resolver().resolver(hint(), equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
        verify(jugadores, never()).findAllByEquipoIdAndActivoTrue(UUID.randomUUID());
    }

    @Test
    void identidadPersistidaDeOtroEquipoNoSaleDelEquipoRecibido() {
        var equipo = equipo(UUID.randomUUID(), "Equipo A");
        var otro = new JugadorEntity(UUID.randomUUID(), "Juan Perez", UUID.randomUUID(),
                "Forward", 9, "Argentina", true);
        when(identidades.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 44L))
                .thenReturn(Optional.of(new com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaJugadorEntity(
                        UUID.randomUUID(), otro, ProveedorIdentidadExterna.WHOSCORED, 44L)));

        assertThatThrownBy(() -> resolver().resolver(hint(), equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
        verify(jugadores, never()).findAllByEquipoIdAndActivoTrue(equipo.getId());
    }

    @ParameterizedTest
    @CsvSource({
        "Tarik Muharemovic, Tarik Muharemović",
        "Josko Gvardiol, Joško Gvardiol",
        "Ronald Araujo, Ronald Araújo",
        "Amar Dedic, Amar Dedić",
        "Kiernan Dewsbury-Hall, Kiernan Dewsbury Hall",
        "Dan Ballard, Daniel Ballard",
        "Josh King, Joshua King",
        "Tim Weah, Timothy Weah",
        "Andy Robertson, Andrew Robertson",
        "Marcus Thuram, Marcus Thuram-Ulien",
        "Victor Lindelöf, Victor Nilsson-Lindelöf",
        "Ryan Giles, Ryan John Giles",
        "Íñigo Ruiz de Galarreta, Ruiz de Galarreta"
    })
    void resuelveEquivalenciasNominalesDeterministas(String externo, String local) {
        var equipo = equipo(UUID.randomUUID(), "Equipo A");
        var jugador = new JugadorEntity(UUID.randomUUID(), local, equipo.getId(), "Forward", 9, "Argentina", true);
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(jugadores.findAllByEquipoIdAndActivoTrue(equipo.getId())).thenReturn(List.of(jugador));

        assertThat(resolver().resolver(new IdentidadEstadisticaJugador(44, externo, null, null, 7L, "Equipo A"),
                equipo, CompeticionEstadistica.PREMIER_LEAGUE).jugadorId()).isEqualTo(jugador.getId());
    }

    @Test
    void compartirSoloAbdulNoAlcanza() {
        var equipo = equipo(UUID.randomUUID(), "Equipo A");
        var primero = new JugadorEntity(UUID.randomUUID(), "Abdul Issahaku", equipo.getId(), "Forward", 9, "Ghana", true);
        var segundo = new JugadorEntity(UUID.randomUUID(), "Abdul Fofana", equipo.getId(), "Forward", 10, "Ghana", true);
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(jugadores.findAllByEquipoIdAndActivoTrue(equipo.getId())).thenReturn(List.of(primero, segundo));

        assertThatThrownBy(() -> resolver().resolver(new IdentidadEstadisticaJugador(44, "Abdul Fatawu", null,
                "Ghana", 7L, "Equipo A"), equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
    }

    @Test
    void persistidaGanaAntesQueCualquierMatchingNominal() {
        var equipo = equipo(UUID.randomUUID(), "Equipo A");
        var jugador = new JugadorEntity(UUID.randomUUID(), "Otro Nombre", equipo.getId(), "Forward", 9, "Argentina", true);
        when(identidades.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 44L))
                .thenReturn(Optional.of(new com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaJugadorEntity(
                        UUID.randomUUID(), jugador, ProveedorIdentidadExterna.WHOSCORED, 44L)));

        assertThat(resolver().resolver(hint(), equipo, CompeticionEstadistica.PREMIER_LEAGUE).jugadorId())
                .isEqualTo(jugador.getId());
        verify(jugadores, never()).findAllByEquipoIdAndActivoTrue(equipo.getId());
    }

    @Test
    void fuzzyConScoreYMargenSuficientesEsUltimoFallback() {
        var equipo = equipo(UUID.randomUUID(), "Equipo A");
        var jugador = new JugadorEntity(UUID.randomUUID(), "Lionel Messi", equipo.getId(), "Forward", 10, "Argentina", true);
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(jugadores.findAllByEquipoIdAndActivoTrue(equipo.getId())).thenReturn(List.of(jugador));

        assertThat(resolver().resolver(new IdentidadEstadisticaJugador(44, "Lionel Mesi", null, null, 7L, "Equipo A"),
                equipo, CompeticionEstadistica.PREMIER_LEAGUE).jugadorId()).isEqualTo(jugador.getId());
    }

    @Test
    void fuzzyConScoreInsuficienteOMargenInsuficienteNoFuerza() {
        var equipo = equipo(UUID.randomUUID(), "Equipo A");
        var mejor = new JugadorEntity(UUID.randomUUID(), "Lionel Messi", equipo.getId(), "Forward", 10, "Argentina", true);
        var cercano = new JugadorEntity(UUID.randomUUID(), "Lionel Mesi", equipo.getId(), "Forward", 11, "Argentina", true);
        when(identidades.findByProveedorAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(jugadores.findAllByEquipoIdAndActivoTrue(equipo.getId())).thenReturn(List.of(mejor, cercano));

        assertThatThrownBy(() -> resolver().resolver(new IdentidadEstadisticaJugador(44, "Lionel Mesii", null, null,
                7L, "Equipo A"), equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
        assertThatThrownBy(() -> resolver().resolver(new IdentidadEstadisticaJugador(45, "Lionel X", null, null,
                7L, "Equipo A"), equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
    }

    private IdentidadJugadorResolver resolver() { return new IdentidadJugadorResolver(jugadores, identidades); }
    private static IdentidadEstadisticaJugador hint() {
        return new IdentidadEstadisticaJugador(44, "Juan Perez", "Forward", "Argentina", 7L, "Equipo A");
    }
    private static EquipoEntity equipo(UUID id, String nombre) {
        Instant now = Instant.now();
        return EquipoEntity.builder().id(id).nombre(nombre).liga("Premier League")
                .activo(true).creadoEn(now).actualizadoEn(now).build();
    }
}
