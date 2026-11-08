package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.ProveedorCatalogo;
import com.devchesterunited.devchesterunitedv2.service.event.JugadorActivadoEvent;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionCatalogoService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SincronizacionCatalogoServiceImpl implements SincronizacionCatalogoService {
    private final ProveedorCatalogo adapter;
    private final CatalogoEquipoWriter equipoWriter;
    private final CatalogoJugadorWriter jugadorWriter;
    private final CatalogoUsuarioValidator usuarioValidator;
    private final CatalogoSyncLock syncLock;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;

    @org.springframework.beans.factory.annotation.Autowired
    public SincronizacionCatalogoServiceImpl(ProveedorCatalogo adapter,
            CatalogoEquipoWriter equipoWriter, CatalogoJugadorWriter jugadorWriter,
            CatalogoUsuarioValidator usuarioValidator, CatalogoSyncLock syncLock, Clock clock,
            ApplicationEventPublisher eventos) {
        this.adapter = adapter;
        this.equipoWriter = equipoWriter;
        this.jugadorWriter = jugadorWriter;
        this.usuarioValidator = Objects.requireNonNull(usuarioValidator,
                "usuarioValidator es obligatorio");
        this.syncLock = Objects.requireNonNull(syncLock, "syncLock es obligatorio");
        this.clock = clock;
        this.eventos = Objects.requireNonNull(eventos, "eventos es obligatorio");
    }

    /** Constructor compatible con las pruebas unitarias del sincronizador. */
    public SincronizacionCatalogoServiceImpl(ProveedorCatalogo adapter,
            CatalogoEquipoWriter equipoWriter, CatalogoJugadorWriter jugadorWriter,
            CatalogoUsuarioValidator usuarioValidator, CatalogoSyncLock syncLock, Clock clock) {
        this(adapter, equipoWriter, jugadorWriter, usuarioValidator, syncLock, clock, evento -> { });
    }

    @Override
    @Transactional(timeout = 150, isolation = Isolation.READ_COMMITTED)
    public SincronizacionResultado sincronizar() {
        syncLock.adquirir();

        try {
            CatalogoSincronizado snapshot = adapter.sincronizar();
            List<IdentidadExternaEquipoEntity> identidadesFootballData =
                    equipoWriter.obtenerIdentidadesFootballData();
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
        usuarioValidator.validar(usuarioId);
    }

    private SincronizacionResultado publicar(CatalogoSincronizado snapshot, boolean primerSnapshot,
            List<IdentidadExternaEquipoEntity> identidadesFootballData) {
        Instant ahora = clock.instant();

        CatalogoEquipoWriter.Resultado equiposResultado = equipoWriter.escribir(snapshot, ahora);
        CatalogoJugadorWriter.Resultado jugadoresResultado = jugadorWriter.escribir(
                snapshot, equiposResultado.equiposPersistidos(), ahora);

        equipoWriter.flush();
        jugadorWriter.flush();

        inactivarAusentes(
                snapshot.identidadesExternasEquipos().stream()
                        .map(IdentidadExternaEquipo::externalId).collect(Collectors.toSet()),
                jugadoresResultado.jugadoresSnapshot(),
                ahora,
                primerSnapshot,
                identidadesFootballData
        );

        // Se publica dentro de la transacción; el listener de mercado sólo lo
        // procesa AFTER_COMMIT. Si esta transacción hace rollback, no hay emisión.
        jugadoresResultado.jugadoresActivados()
                .forEach(id -> eventos.publishEvent(new JugadorActivadoEvent(id)));

        return new SincronizacionResultado(
                snapshot.equipos().size(),
                snapshot.jugadores().size(),
                equiposResultado.creados(),
                snapshot.equipos().size() - equiposResultado.creados(),
                jugadoresResultado.creados(),
                snapshot.jugadores().size() - jugadoresResultado.creados()
        );
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
                equipoWriter.inactivar(equipo, ahora);
            }
        }
        Set<UUID> jugadoresConIdentidadFootballData = jugadorWriter
                .obtenerJugadoresConIdentidadFootballData();
        for (JugadorEntity jugador : jugadorWriter.obtenerTodos()) {
            if ((!jugadoresConIdentidadFootballData.contains(jugador.getId()) && primerSnapshot)
                    || jugadoresConIdentidadFootballData.contains(jugador.getId())
                    && !presentesJugadores.contains(jugador.getId())) {
                jugadorWriter.inactivar(jugador, ahora);
            }
        }
    }

}
