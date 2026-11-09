package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;



import com.devchesterunited.devchesterunitedv2.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.TemporadaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.mapper.JugadorMapper;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataException;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.ProveedorCatalogo;
import com.devchesterunited.devchesterunitedv2.service.event.JugadorActivadoEvent;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoSyncLock;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoEquipoWriter;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoJugadorWriter;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoAusenciaProcessor;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoJugadorActivationPublisher;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoSyncCoordinator;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoUsuarioValidator;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.SincronizacionCatalogoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

@ExtendWith(MockitoExtension.class)
class SincronizacionCatalogoServiceTest {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");
    @Mock ProveedorCatalogo adapter;
    @Mock EquipoRepository equipos;
    @Mock IdentidadExternaEquipoRepository identidadesEquipos;
    @Mock JugadorRepository jugadores;
    @Mock IdentidadExternaJugadorRepository identidades;
    @Mock CatalogoUsuarioValidator usuarioValidator;
    @Mock CatalogoSyncLock syncLock;
    @Mock org.springframework.context.ApplicationEventPublisher eventos;
    private SincronizacionCatalogoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        CatalogoEquipoWriter equipoWriter = new CatalogoEquipoWriter(equipos, identidadesEquipos);
        CatalogoJugadorWriter jugadorWriter = new CatalogoJugadorWriter(
                jugadores, identidades, new JugadorMapper());
        servicio = new SincronizacionCatalogoServiceImpl(adapter,
                new CatalogoSyncCoordinator(
                        equipoWriter,
                        jugadorWriter,
                        new CatalogoAusenciaProcessor(equipoWriter, jugadorWriter),
                        new CatalogoJugadorActivationPublisher(eventos),
                        Clock.fixed(AHORA, java.time.ZoneOffset.UTC)),
                usuarioValidator, syncLock);
        lenient().when(identidadesEquipos.findAllByProveedorAndExternalIdIn(any(), anyList())).thenReturn(List.of());
        lenient().when(identidadesEquipos.findAllByProveedor(any())).thenReturn(List.of());
        lenient().when(equipos.findAll()).thenReturn(List.of());
        lenient().when(jugadores.findAll()).thenReturn(List.of());
        lenient().when(identidades.findAllByProveedorAndExternalIdIn(any(), anyList())).thenReturn(List.of());
        lenient().when(identidades.findAllByProveedor(any())).thenReturn(List.of());
        lenient().when(equipos.save(any(EquipoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jugadores.save(any(JugadorEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(identidades.findByJugador_IdAndProveedor(any(), any())).thenReturn(java.util.Optional.empty());
        lenient().when(identidades.save(any(IdentidadExternaJugadorEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void publicaSnapshotValidoConConteosDeCreacion() {
        when(adapter.sincronizar()).thenReturn(snapshot());

        var resultado = servicio.sincronizar();

        assertThat(resultado.equiposProcesados()).isEqualTo(96);
        assertThat(resultado.jugadoresProcesados()).isEqualTo(96);
        assertThat(resultado.equiposCreados()).isEqualTo(96);
        assertThat(resultado.jugadoresCreados()).isEqualTo(96);
        verify(equipos).flush();
        verify(jugadores).flush();
        InOrder orden = inOrder(equipos, jugadores);
        orden.verify(equipos).flush();
        orden.verify(jugadores).flush();
        verify(identidades, times(96)).save(any(IdentidadExternaJugadorEntity.class));
        verify(identidadesEquipos, times(96)).save(any(IdentidadExternaEquipoEntity.class));
    }

    @Test
    void publicaLosIdentificadoresDeJugadoresActivados() {
        when(adapter.sincronizar()).thenReturn(snapshot());

        servicio.sincronizar();

        verify(eventos, times(96)).publishEvent(any(JugadorActivadoEvent.class));
    }

    @Test
    void resuelveJugadorExistenteDesdeIdentidadYConservaUuidInterno() {
        CatalogoSincronizado snapshot = snapshot();
        UUID uuidPersistido = UUID.fromString("70000000-0000-0000-0000-000000000001");
        Equipo equipo = snapshot.equipos().get(0);
        UUID uuidEquipoPersistido = UUID.fromString("71000000-0000-0000-0000-000000000001");
        EquipoEntity equipoPersistido = EquipoEntity.builder().id(uuidEquipoPersistido).nombre("Equipo previo")
                .nombreCorto(null).sigla(null).liga(equipo.liga()).escudoUrl(null).activo(false)
                .temporadaExternaId(2026L).inicioTemporada(null).finTemporada(null)
                .creadoEn(AHORA.minusSeconds(120)).actualizadoEn(AHORA.minusSeconds(60)).build();
        IdentidadExternaEquipoEntity identidadEquipo = new IdentidadExternaEquipoEntity(
                UUID.fromString("72000000-0000-0000-0000-000000000001"), equipoPersistido,
                ProveedorIdentidadExterna.FOOTBALL_DATA, 1L);
        JugadorEntity jugadorPersistido = new JugadorEntity(uuidPersistido, "Jugador previo",
                equipo.id(), "Forward", 9, "Argentina", false);
        jugadorPersistido.conservarAuditoria(AHORA.minusSeconds(60), AHORA.minusSeconds(30));
        IdentidadExternaJugadorEntity identidad = new IdentidadExternaJugadorEntity(
                UUID.fromString("80000000-0000-0000-0000-000000000001"), jugadorPersistido,
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.FOOTBALL_DATA,
                10001L);
        when(identidades.findAllByProveedorAndExternalIdIn(any(), anyList())).thenReturn(List.of(identidad));
        when(identidadesEquipos.findAllByProveedorAndExternalIdIn(any(), anyList()))
                .thenReturn(List.of(identidadEquipo));
        when(identidades.findAllByProveedor(any())).thenReturn(List.of(identidad));
        when(adapter.sincronizar()).thenReturn(snapshot);

        servicio.sincronizar();

        verify(jugadores).save(argThat(j -> j.getId().equals(uuidPersistido)));
        verify(jugadores).save(argThat(j -> j.getEquipoId().equals(uuidEquipoPersistido)));
        verify(equipos).save(argThat(e -> e.getId().equals(uuidEquipoPersistido)));
        verify(identidadesEquipos, never()).save(argThat(i -> i.getExternalId().equals(1L)));
        verify(identidades, never()).save(argThat(i -> i.getExternalId().equals(10001L)));
    }

    @Test
    void noReasignaIdentidadExternaExistenteAOtroEquipo() {
        CatalogoSincronizado snapshot = snapshot();
        Equipo equipo = snapshot.equipos().get(0);
        UUID uuidEquipoPersistido = UUID.fromString("73000000-0000-0000-0000-000000000001");
        EquipoEntity equipoPersistido = EquipoEntity.builder().id(uuidEquipoPersistido).nombre("Equipo persistido")
                .nombreCorto(null).sigla(null).liga(equipo.liga()).escudoUrl(null).activo(true)
                .temporadaExternaId(2026L).inicioTemporada(null).finTemporada(null)
                .creadoEn(AHORA).actualizadoEn(AHORA).build();
        IdentidadExternaEquipoEntity identidadEquipo = new IdentidadExternaEquipoEntity(
                UUID.fromString("74000000-0000-0000-0000-000000000001"), equipoPersistido,
                ProveedorIdentidadExterna.FOOTBALL_DATA, 1L);
        when(identidadesEquipos.findAllByProveedorAndExternalIdIn(any(), anyList()))
                .thenReturn(List.of(identidadEquipo));
        when(adapter.sincronizar()).thenReturn(snapshot);

        servicio.sincronizar();

        verify(equipos).save(argThat(e -> e.getId().equals(uuidEquipoPersistido)));
        verify(identidadesEquipos, never()).save(argThat(i -> i.getExternalId().equals(1L)));
    }

    @Test
    void inactivaEquipoConIdentidadFootballDataAusenteDelSnapshot() {
        CatalogoSincronizado snapshot = snapshot();
        UUID uuidEquipoAusente = UUID.fromString("75000000-0000-0000-0000-000000000001");
        EquipoEntity equipoAusente = EquipoEntity.builder().id(uuidEquipoAusente).nombre("Equipo ausente")
                .nombreCorto(null).sigla(null).liga("Premier League").escudoUrl(null).activo(true)
                .temporadaExternaId(2026L).inicioTemporada(null).finTemporada(null)
                .creadoEn(AHORA).actualizadoEn(AHORA).build();
        IdentidadExternaEquipoEntity identidadAusente = new IdentidadExternaEquipoEntity(
                UUID.fromString("76000000-0000-0000-0000-000000000001"), equipoAusente,
                ProveedorIdentidadExterna.FOOTBALL_DATA, 999999L);
        when(identidadesEquipos.findAllByProveedor(any())).thenReturn(List.of(identidadAusente));
        when(adapter.sincronizar()).thenReturn(snapshot);

        servicio.sincronizar();

        verify(equipos).save(argThat(e -> e.getId().equals(uuidEquipoAusente) && !e.isActivo()));
    }

    @Test
    void conservaActivoEquipoSinIdentidadFootballDataAunqueNoEsteEnSnapshot() {
        CatalogoSincronizado snapshot = snapshot();
        UUID uuidEquipoLegacy = UUID.fromString("77000000-0000-0000-0000-000000000001");
        when(adapter.sincronizar()).thenReturn(snapshot);

        servicio.sincronizar();

        verify(equipos, never()).save(argThat(e -> e.getId().equals(uuidEquipoLegacy)
                && !e.isActivo()));
    }

    @Test
    void lockOcupadoRechazaAntesDeConsultarAdapter() {
        doThrow(new SincronizacionEnCursoException()).when(syncLock).adquirir();
        assertThatThrownBy(servicio::sincronizar).isInstanceOf(SincronizacionEnCursoException.class);
        verifyNoInteractions(adapter);
    }

    @Test
    void validarUsuarioDelegaEnElValidadorExtraido() {
        UUID usuarioId = UUID.randomUUID();

        servicio.validarUsuario(usuarioId);

        verify(usuarioValidator).validar(usuarioId);
    }

    @Test
    void sincronizarConservaElLimiteTransaccionalOriginal() throws NoSuchMethodException {
        Transactional transactional = SincronizacionCatalogoServiceImpl.class
                .getMethod("sincronizar").getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.timeout()).isEqualTo(150);
        assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void falloDelProveedorSePropagaParaRollbackTransaccional() {
        when(adapter.sincronizar()).thenThrow(new FootballDataException("fallo sintetico"));
        assertThatThrownBy(servicio::sincronizar).isInstanceOf(FootballDataException.class);
        verify(equipos, never()).save(any(EquipoEntity.class));
        verify(jugadores, never()).save(any(JugadorEntity.class));
    }

    private static CatalogoSincronizado snapshot() {
        var equipos = new ArrayList<Equipo>();
        var jugadores = new ArrayList<Jugador>();
        var ids = new HashMap<UUID, Long>();
        var identidadesEquipos = new ArrayList<IdentidadExternaEquipo>();
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        long externo = 1;
        for (int i = 0; i < ligas.size(); i++) for (int j = 0; j < cantidades.get(i); j++) {
            UUID id = UUID.randomUUID();
            equipos.add(Equipo.importado(id, "Equipo " + externo, ligas.get(i),
                    new TemporadaEquipo(2026L, null, null), AHORA));
            identidadesEquipos.add(new IdentidadExternaEquipo(
                    id, ProveedorIdentidadExterna.FOOTBALL_DATA, externo));
            Jugador jugador = new Jugador(UUID.randomUUID(), "Jugador Apellido", id,
                    "Forward", null, "Argentina", AHORA);
            jugadores.add(jugador);
            ids.put(jugador.getId(), 10000 + externo);
            externo++;
        }
        return new CatalogoSincronizado(equipos, jugadores, ids, identidadesEquipos);
    }
}
