package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorCatalogo;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.mapper.JugadorMapper;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionCatalogoService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SincronizacionCatalogoServiceImpl implements SincronizacionCatalogoService {
    private static final long LOCK_KEY_1 = 114644L;
    private static final long LOCK_KEY_2 = 1L;
    private final ProveedorCatalogo adapter;
    private final EquipoRepository equipos;
    private final IdentidadExternaEquipoRepository identidadesEquipos;
    private final JugadorRepository jugadores;
    private final IdentidadExternaJugadorRepository identidades;
    private final UsuarioRepository usuarios;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JugadorMapper jugadorMapper;

    public SincronizacionCatalogoServiceImpl(ProveedorCatalogo adapter, EquipoRepository equipos,
            IdentidadExternaEquipoRepository identidadesEquipos,
            JugadorRepository jugadores, UsuarioRepository usuarios,
            IdentidadExternaJugadorRepository identidades, JdbcTemplate jdbc, Clock clock,
            JugadorMapper jugadorMapper) {
        this.adapter = adapter;
        this.equipos = equipos;
        this.identidadesEquipos = Objects.requireNonNull(identidadesEquipos,
                "identidadesEquipos es obligatoria");
        this.jugadores = jugadores;
        this.usuarios = usuarios;
        this.identidades = Objects.requireNonNull(identidades, "identidades es obligatoria");
        this.jdbc = jdbc;
        this.clock = clock;
        this.jugadorMapper = Objects.requireNonNull(jugadorMapper, "jugadorMapper es obligatorio");
    }

    @Override
    @Transactional(timeout = 150, isolation = Isolation.READ_COMMITTED)
    public SincronizacionResultado sincronizar() {
        Boolean adquirido = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?::integer, ?::integer)", Boolean.class,
                LOCK_KEY_1, LOCK_KEY_2);
        if (!Boolean.TRUE.equals(adquirido)) throw new SincronizacionEnCursoException();

        try {
            CatalogoSincronizado snapshot = adapter.sincronizar();
            List<IdentidadExternaEquipoEntity> identidadesFootballData =
                    identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.FOOTBALL_DATA);
            boolean primerSnapshot = identidadesFootballData.isEmpty();
            return publicar(snapshot, primerSnapshot, identidadesFootballData);
        } catch (com.devchesterunited.devchesterunitedv2.adapter.FootballDataException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new SincronizacionPersistenciaException(exception);
        }
    }

    @Override
    public void validarUsuario(UUID usuarioId) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) {
            throw new BadCredentialsException("No autorizado.");
        }
    }

    private SincronizacionResultado publicar(CatalogoSincronizado snapshot, boolean primerSnapshot,
            List<IdentidadExternaEquipoEntity> identidadesFootballData) {
        Instant ahora = clock.instant();

        Map<UUID, UUID> equiposSnapshot = new HashMap<>();
        Set<UUID> jugadoresSnapshot = new HashSet<>();

        int equiposCreados = guardarEquipos(snapshot, equiposSnapshot, ahora);
        int jugadoresCreados = guardarJugadores(snapshot, equiposSnapshot, jugadoresSnapshot, ahora);

        equipos.flush();
        jugadores.flush();

        inactivarAusentes(
                snapshot.identidadesExternasEquipos().stream()
                        .map(identidad -> identidad.externalId()).collect(Collectors.toSet()),
                jugadoresSnapshot,
                ahora,
                primerSnapshot,
                identidadesFootballData
        );

        return new SincronizacionResultado(
                snapshot.equipos().size(),
                snapshot.jugadores().size(),
                equiposCreados,
                snapshot.equipos().size() - equiposCreados,
                jugadoresCreados,
                snapshot.jugadores().size() - jugadoresCreados
        );
    }

    private int guardarEquipos(
            CatalogoSincronizado snapshot,
            Map<UUID, UUID> equiposSnapshot,
            Instant ahora) {

        Map<Long, IdentidadExternaEquipoEntity> identidadesExistentes = identidadesExistentes(snapshot);
        int equiposCreados = 0;

        for (Equipo equipo : snapshot.equipos()) {
            long externalId = snapshot.getIdentidadExternaEquipo(equipo.id()).externalId();
            IdentidadExternaEquipoEntity identidadExistente = identidadesExistentes.get(externalId);
            EquipoEntity anterior = identidadExistente == null ? null : identidadExistente.getEquipo();
            UUID id = anterior == null ? equipo.id() : anterior.getId();

            if (anterior == null) {
                equiposCreados++;
            }

            EquipoEntity persistido = equipos.save(new EquipoEntity(
                    id,
                    equipo.nombre(),
                    anterior == null ? null : anterior.getNombreCorto(),
                    anterior == null ? null : anterior.getSigla(),
                    equipo.liga(),
                    anterior == null ? null : anterior.getEscudoUrl(),
                    true,
                    equipo.temporada().temporadaExternaId(),
                    equipo.temporada().inicio(),
                    equipo.temporada().fin(),
                    anterior == null ? ahora : anterior.getCreadoEn(),
                    ahora
            ));

            equiposSnapshot.put(equipo.id(), persistido.getId());

            if (identidadExistente == null) {
                identidadesEquipos.save(new IdentidadExternaEquipoEntity(
                        UUID.randomUUID(), persistido,
                        ProveedorIdentidadExterna.FOOTBALL_DATA, externalId));
            }
        }

        return equiposCreados;
    }

    private int guardarJugadores(
            CatalogoSincronizado snapshot,
            Map<UUID, UUID> equiposSnapshot,
            Set<UUID> jugadoresSnapshot,
            Instant ahora) {

        List<Long> externalIds = snapshot.jugadores().stream()
                .map(jugador -> snapshot.getExternalId(jugador.getId()))
                .toList();
        Map<Long, IdentidadExternaJugadorEntity> identidadesPorExternalId = new HashMap<>();
        for (IdentidadExternaJugadorEntity identidad : identidades
                .findAllByProveedorAndExternalIdIn(ProveedorIdentidadExterna.FOOTBALL_DATA, externalIds)) {
            identidadesPorExternalId.put(identidad.getExternalId(), identidad);
        }

        int jugadoresCreados = 0;

        for (Jugador jugador : snapshot.jugadores()) {
            long externalId = snapshot.getExternalId(jugador.getId());
            IdentidadExternaJugadorEntity identidadExistente = identidadesPorExternalId.get(externalId);
            JugadorEntity anterior = identidadExistente == null ? null : identidadExistente.getJugador();

            if (anterior == null) {
                jugadoresCreados++;
            }

            Equipo equipo = equipo(snapshot, jugador.getEquipoId());
            UUID equipoIdPersistido = equiposSnapshot.get(equipo.id());
            if (equipoIdPersistido == null) {
                throw new IllegalStateException("No se pudo resolver el equipo local del jugador.");
            }
            JugadorEntity persistido = jugadores.save(
                    jugadorMapper.toEntity(jugador, equipoIdPersistido, anterior, ahora));

            guardarIdentidadFootballData(persistido, externalId, identidadExistente);

            jugadoresSnapshot.add(persistido.getId());
        }

        return jugadoresCreados;
    }

    private void guardarIdentidadFootballData(JugadorEntity jugador, long externalId,
            IdentidadExternaJugadorEntity identidadExistente) {
        if (identidadExistente == null) {
            identidades.save(new IdentidadExternaJugadorEntity(
                    UUID.randomUUID(), jugador, ProveedorIdentidadExterna.FOOTBALL_DATA, externalId));
        }
    }

    private Map<Long, IdentidadExternaEquipoEntity> identidadesExistentes(
            CatalogoSincronizado snapshot) {
        Map<Long, IdentidadExternaEquipoEntity> resultado = new HashMap<>();
        List<Long> externalIds = snapshot.identidadesExternasEquipos().stream()
                .map(identidad -> identidad.externalId()).toList();
        for (IdentidadExternaEquipoEntity identidad : identidadesEquipos
                .findAllByProveedorAndExternalIdIn(
                        ProveedorIdentidadExterna.FOOTBALL_DATA, externalIds)) {
            if (identidad.getEquipo() == null || identidad.getEquipo().getId() == null) {
                throw new IllegalStateException("La identidad externa de equipo no tiene equipo local valido.");
            }
            resultado.put(identidad.getExternalId(), identidad);
        }
        return resultado;
    }

    private static Equipo equipo(CatalogoSincronizado snapshot, UUID id) {
        return snapshot.equipos().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private void inactivarAusentes(Set<Long> presentesEquipos,
                                   Set<UUID> presentesJugadores, Instant ahora, boolean primerSnapshot,
                                   List<IdentidadExternaEquipoEntity> identidadesFootballData) {
        for (IdentidadExternaEquipoEntity identidad : identidadesFootballData) {
            if (!presentesEquipos.contains(identidad.getExternalId())) {
                EquipoEntity equipo = identidad.getEquipo();
                if (!equipo.isActivo()) {
                    continue;
                }
                equipos.save(new EquipoEntity(equipo.getId(), equipo.getNombre(), equipo.getNombreCorto(),
                        equipo.getSigla(), equipo.getLiga(), equipo.getEscudoUrl(), false, equipo.getTemporadaExternaId(),
                        equipo.getInicioTemporada(), equipo.getFinTemporada(), equipo.getCreadoEn(), ahora));
            }
        }
        Set<UUID> jugadoresConIdentidadFootballData = identidades
                .findAllByProveedor(ProveedorIdentidadExterna.FOOTBALL_DATA)
                .stream()
                .map(identidad -> identidad.getJugador().getId())
                .collect(Collectors.toSet());
        for (JugadorEntity jugador : jugadores.findAll()) {
            if ((!jugadoresConIdentidadFootballData.contains(jugador.getId()) && primerSnapshot)
                    || jugadoresConIdentidadFootballData.contains(jugador.getId())
                    && !presentesJugadores.contains(jugador.getId())) {
                jugadores.save(jugadorMapper.toInactiveEntity(jugador, ahora));
            }
        }
    }

}
