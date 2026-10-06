package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorCatalogo;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
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
    private final JugadorRepository jugadores;
    private final IdentidadExternaJugadorRepository identidades;
    private final UsuarioRepository usuarios;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JugadorMapper jugadorMapper;

    public SincronizacionCatalogoServiceImpl(ProveedorCatalogo adapter, EquipoRepository equipos,
            JugadorRepository jugadores, UsuarioRepository usuarios,
            IdentidadExternaJugadorRepository identidades, JdbcTemplate jdbc, Clock clock,
            JugadorMapper jugadorMapper) {
        this.adapter = adapter;
        this.equipos = equipos;
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
            boolean primerSnapshot = equipos.findAll().stream().noneMatch(e -> e.getIdExterno() != null);
            return publicar(snapshot, primerSnapshot);
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

    private SincronizacionResultado publicar(CatalogoSincronizado snapshot, boolean primerSnapshot) {
        Instant ahora = clock.instant();

        Map<Long, UUID> equiposSnapshot = new HashMap<>();
        Set<UUID> jugadoresSnapshot = new HashSet<>();

        int equiposCreados = guardarEquipos(snapshot, equiposSnapshot, ahora);
        int jugadoresCreados = guardarJugadores(snapshot, equiposSnapshot, jugadoresSnapshot, ahora);

        equipos.flush();
        jugadores.flush();

        inactivarAusentes(
                equiposSnapshot.keySet(),
                jugadoresSnapshot,
                ahora,
                primerSnapshot
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
            Map<Long, UUID> equiposSnapshot,
            Instant ahora) {

        Map<Long, EquipoEntity> existentesEquipos = existentesEquipos(snapshot);
        int equiposCreados = 0;

        for (Equipo equipo : snapshot.equipos()) {
            long externalId = snapshot.getEquipoExternalId(equipo.id());
            EquipoEntity anterior = existentesEquipos.get(externalId);

            UUID id = anterior == null
                    ? equipo.id()
                    : anterior.getId();

            if (anterior == null) {
                equiposCreados++;
            }

            equipos.save(new EquipoEntity(
                    id,
                    externalId,
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

            equiposSnapshot.put(externalId, id);
        }

        return equiposCreados;
    }

    private int guardarJugadores(
            CatalogoSincronizado snapshot,
            Map<Long, UUID> equiposSnapshot,
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
            UUID equipoIdPersistido = equiposSnapshot.get(snapshot.getEquipoExternalId(equipo.id()));
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

    private Map<Long, EquipoEntity> existentesEquipos(CatalogoSincronizado snapshot) {
        Map<Long, EquipoEntity> resultado = new HashMap<>();
        for (EquipoEntity equipo : equipos.findAllByIdExternoIn(snapshot.equipos().stream()
                .map(equipo -> snapshot.getEquipoExternalId(equipo.id())).toList())) {
            resultado.put(equipo.getIdExterno(), equipo);
        }
        return resultado;
    }

    private static Equipo equipo(CatalogoSincronizado snapshot, UUID id) {
        return snapshot.equipos().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private void inactivarAusentes(Set<Long> presentesEquipos,
                                   Set<UUID> presentesJugadores, Instant ahora, boolean primerSnapshot) {
        for (EquipoEntity equipo : equipos.findAll()) {
            if ((equipo.getIdExterno() == null && primerSnapshot)
                    || equipo.getIdExterno() != null && !presentesEquipos.contains(equipo.getIdExterno())) {
                equipos.save(new EquipoEntity(equipo.getId(), equipo.getIdExterno(), equipo.getNombre(), equipo.getNombreCorto(),
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
