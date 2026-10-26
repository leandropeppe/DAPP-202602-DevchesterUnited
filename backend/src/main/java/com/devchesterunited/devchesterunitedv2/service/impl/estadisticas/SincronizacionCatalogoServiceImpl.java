package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.mapper.JugadorMapper;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.ProveedorCatalogo;
import com.devchesterunited.devchesterunitedv2.service.event.JugadorActivadoEvent;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionCatalogoService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
    private final ApplicationEventPublisher eventos;

    @org.springframework.beans.factory.annotation.Autowired
    public SincronizacionCatalogoServiceImpl(ProveedorCatalogo adapter, EquipoRepository equipos,
            IdentidadExternaEquipoRepository identidadesEquipos,
            JugadorRepository jugadores, UsuarioRepository usuarios,
            IdentidadExternaJugadorRepository identidades, JdbcTemplate jdbc, Clock clock,
            JugadorMapper jugadorMapper, ApplicationEventPublisher eventos) {
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
        this.eventos = Objects.requireNonNull(eventos, "eventos es obligatorio");
    }

    /** Constructor compatible con las pruebas unitarias del sincronizador. */
    public SincronizacionCatalogoServiceImpl(ProveedorCatalogo adapter, EquipoRepository equipos,
            IdentidadExternaEquipoRepository identidadesEquipos,
            JugadorRepository jugadores, UsuarioRepository usuarios,
            IdentidadExternaJugadorRepository identidades, JdbcTemplate jdbc, Clock clock,
            JugadorMapper jugadorMapper) {
        this(adapter, equipos, identidadesEquipos, jugadores, usuarios, identidades, jdbc, clock,
                jugadorMapper, evento -> { });
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
        } catch (com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataException exception) {
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
        List<UUID> jugadoresActivados = new java.util.ArrayList<>();

        int equiposCreados = guardarEquipos(snapshot, equiposSnapshot, ahora);
        int jugadoresCreados = guardarJugadores(snapshot, equiposSnapshot, jugadoresSnapshot,
                jugadoresActivados, ahora);

        equipos.flush();
        jugadores.flush();

        inactivarAusentes(
                snapshot.identidadesExternasEquipos().stream()
                        .map(IdentidadExternaEquipo::externalId).collect(Collectors.toSet()),
                jugadoresSnapshot,
                ahora,
                primerSnapshot,
                identidadesFootballData
        );

        // Se publica dentro de la transacción; el listener de mercado sólo lo
        // procesa AFTER_COMMIT. Si esta transacción hace rollback, no hay emisión.
        jugadoresActivados.forEach(id -> eventos.publishEvent(new JugadorActivadoEvent(id)));

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
            if (guardarEquipo(equipo, snapshot, identidadesExistentes, equiposSnapshot, ahora)) {
                equiposCreados++;
            }
        }

        return equiposCreados;
    }

    private boolean guardarEquipo(
            Equipo equipo,
            CatalogoSincronizado snapshot,
            Map<Long, IdentidadExternaEquipoEntity> identidadesExistentes,
            Map<UUID, UUID> equiposSnapshot,
            Instant ahora) {
        long externalId = snapshot.getIdentidadExternaEquipo(equipo.id()).externalId();
        IdentidadExternaEquipoEntity identidadExistente = identidadesExistentes.get(externalId);
        EquipoEntity anterior = identidadExistente == null ? null : identidadExistente.getEquipo();
        UUID id = anterior == null ? equipo.id() : anterior.getId();
        boolean equipoNuevo = anterior == null;

        EquipoEntity persistido = equipos.save(EquipoEntity.builder()
                .id(id)
                .nombre(equipo.nombre())
                .nombreCorto(anterior == null ? null : anterior.getNombreCorto())
                .sigla(anterior == null ? null : anterior.getSigla())
                .liga(equipo.liga())
                .escudoUrl(anterior == null ? null : anterior.getEscudoUrl())
                .activo(true)
                .temporadaExternaId(equipo.temporada().temporadaExternaId())
                .inicioTemporada(equipo.temporada().inicio())
                .finTemporada(equipo.temporada().fin())
                .creadoEn(anterior == null ? ahora : anterior.getCreadoEn())
                .actualizadoEn(ahora)
                .build());

        equiposSnapshot.put(equipo.id(), persistido.getId());

        if (identidadExistente == null) {
            identidadesEquipos.save(new IdentidadExternaEquipoEntity(
                    UUID.randomUUID(), persistido,
                    ProveedorIdentidadExterna.FOOTBALL_DATA, externalId));
        }

        return equipoNuevo;
    }

    private int guardarJugadores(
            CatalogoSincronizado snapshot,
            Map<UUID, UUID> equiposSnapshot,
            Set<UUID> jugadoresSnapshot,
            List<UUID> jugadoresActivados,
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

            boolean activacion = anterior == null || !anterior.isActivo();

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
            if (activacion && persistido.isActivo()) {
                jugadoresActivados.add(persistido.getId());
            }
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
                .map(IdentidadExternaEquipo::externalId).toList();
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
                equipos.save(EquipoEntity.builder()
                        .id(equipo.getId())
                        .nombre(equipo.getNombre())
                        .nombreCorto(equipo.getNombreCorto())
                        .sigla(equipo.getSigla())
                        .liga(equipo.getLiga())
                        .escudoUrl(equipo.getEscudoUrl())
                        .activo(false)
                        .temporadaExternaId(equipo.getTemporadaExternaId())
                        .inicioTemporada(equipo.getInicioTemporada())
                        .finTemporada(equipo.getFinTemporada())
                        .creadoEn(equipo.getCreadoEn())
                        .actualizadoEn(ahora)
                        .build());
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
