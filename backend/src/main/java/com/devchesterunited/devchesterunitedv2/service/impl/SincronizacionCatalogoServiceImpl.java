package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.impl.FootballDataAdapter;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionCatalogoService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final FootballDataAdapter adapter;
    private final EquipoRepository equipos;
    private final JugadorRepository jugadores;
    private final UsuarioRepository usuarios;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SincronizacionCatalogoServiceImpl(FootballDataAdapter adapter, EquipoRepository equipos,
            JugadorRepository jugadores, UsuarioRepository usuarios, JdbcTemplate jdbc, Clock clock) {
        this.adapter = adapter;
        this.equipos = equipos;
        this.jugadores = jugadores;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
        this.clock = clock;
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
        Map<Long, Jugador> jugadoresSnapshot = new HashMap<>();
        Map<Long, EquipoEntity> existentesEquipos = existentesEquipos(snapshot);
        int equiposCreados = 0;
        for (Equipo equipo : snapshot.getEquipos()) {
            EquipoEntity anterior = existentesEquipos.get(equipo.getIdExterno());
            UUID id = anterior == null ? equipo.getId() : anterior.getId();
            if (anterior == null) equiposCreados++;
            else equiposSnapshot.put(equipo.getIdExterno(), id);
            equipos.save(new EquipoEntity(id, equipo.getIdExterno(), equipo.getNombre(), equipo.getNombreCorto(),
                    equipo.getSigla(), equipo.getLiga(), equipo.getEscudoUrl() == null ? null : equipo.getEscudoUrl().toString(),
                    true, equipo.getTemporadaExternaId(), equipo.getInicioTemporada(), equipo.getFinTemporada(),
                    anterior == null ? ahora : anterior.getCreadoEn(), ahora));
            equiposSnapshot.put(equipo.getIdExterno(), id);
        }

        Map<Long, JugadorEntity> existentesJugadores = new HashMap<>();
        for (JugadorEntity jugador : jugadores.findAllByIdExternoIn(snapshot.getJugadores().stream()
                .map(Jugador::getIdExterno).toList())) existentesJugadores.put(jugador.getIdExterno(), jugador);
        int jugadoresCreados = 0;
        for (Jugador jugador : snapshot.getJugadores()) {
            JugadorEntity anterior = existentesJugadores.get(jugador.getIdExterno());
            UUID id = anterior == null ? jugador.getId() : anterior.getId();
            if (anterior == null) jugadoresCreados++;
            jugadores.save(new JugadorEntity(id, jugador.getIdExterno(), jugador.getNombre(),
                    equiposSnapshot.get(jugador.getEquipoId() == null ? null : equipoExterno(snapshot, jugador.getEquipoId())),
                    jugador.getPosicion(), jugador.getDorsal() == 0 ? null : jugador.getDorsal(), jugador.getNacionalidad(),
                    true, anterior == null ? ahora : anterior.getCreadoEn(), ahora,
                    equipoLiga(snapshot, jugador.getEquipoId()), equipoNombre(snapshot, jugador.getEquipoId())));
            jugadoresSnapshot.put(jugador.getIdExterno(), jugador);
        }
        equipos.flush();
        jugadores.flush();
        inactivarAusentes(snapshot, equiposSnapshot.keySet(), jugadoresSnapshot.keySet(), ahora, primerSnapshot);
        return new SincronizacionResultado(snapshot.getEquipos().size(), snapshot.getJugadores().size(), equiposCreados,
                snapshot.getEquipos().size() - equiposCreados, jugadoresCreados,
                snapshot.getJugadores().size() - jugadoresCreados);
    }

    private Map<Long, EquipoEntity> existentesEquipos(CatalogoSincronizado snapshot) {
        Map<Long, EquipoEntity> resultado = new HashMap<>();
        for (EquipoEntity equipo : equipos.findAllByIdExternoIn(snapshot.getEquipos().stream().map(Equipo::getIdExterno).toList())) {
            resultado.put(equipo.getIdExterno(), equipo);
        }
        return resultado;
    }

    private void inactivarAusentes(CatalogoSincronizado snapshot, Set<Long> presentesEquipos,
            Set<Long> presentesJugadores, Instant ahora, boolean primerSnapshot) {
        for (EquipoEntity equipo : equipos.findAll()) {
            if ((equipo.getIdExterno() == null && primerSnapshot)
                    || equipo.getIdExterno() != null && !presentesEquipos.contains(equipo.getIdExterno())) {
                equipos.save(new EquipoEntity(equipo.getId(), equipo.getIdExterno(), equipo.getNombre(), equipo.getNombreCorto(),
                        equipo.getSigla(), equipo.getLiga(), equipo.getEscudoUrl(), false, equipo.getTemporadaExternaId(),
                        equipo.getInicioTemporada(), equipo.getFinTemporada(), equipo.getCreadoEn(), ahora));
            }
        }
        for (JugadorEntity jugador : jugadores.findAll()) {
            if ((jugador.getIdExterno() == null && primerSnapshot)
                    || jugador.getIdExterno() != null && !presentesJugadores.contains(jugador.getIdExterno())) {
                jugadores.save(new JugadorEntity(jugador.getId(), jugador.getIdExterno(), jugador.getNombre(),
                        jugador.getEquipoId(), jugador.getPosicion(), jugador.getNumeroCamiseta(), jugador.getNacionalidad(),
                        false, jugador.getCreadoEn(), ahora, jugador.getLiga(), jugador.getEquipo()));
            }
        }
    }

    private static long equipoExterno(CatalogoSincronizado snapshot, UUID equipoId) {
        return snapshot.getEquipos().stream().filter(e -> e.getId().equals(equipoId)).findFirst()
                .map(Equipo::getIdExterno).orElseThrow();
    }

    private static String equipoLiga(CatalogoSincronizado snapshot, UUID id) {
        return snapshot.getEquipos().stream().filter(e -> e.getId().equals(id)).findFirst().orElseThrow().getLiga();
    }

    private static String equipoNombre(CatalogoSincronizado snapshot, UUID id) {
        return snapshot.getEquipos().stream().filter(e -> e.getId().equals(id)).findFirst().orElseThrow().getNombre();
    }
}
