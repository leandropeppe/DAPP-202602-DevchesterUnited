package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.SincronizacionResultado;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class CatalogoSyncCoordinator {
    private final CatalogoEquipoWriter equipoWriter;
    private final CatalogoJugadorWriter jugadorWriter;
    private final CatalogoAusenciaProcessor ausenciaProcessor;
    private final CatalogoJugadorActivationPublisher activationPublisher;
    private final Clock clock;

    public CatalogoSyncCoordinator(CatalogoEquipoWriter equipoWriter,
            CatalogoJugadorWriter jugadorWriter, CatalogoAusenciaProcessor ausenciaProcessor,
            CatalogoJugadorActivationPublisher activationPublisher, Clock clock) {
        this.equipoWriter = equipoWriter;
        this.jugadorWriter = jugadorWriter;
        this.ausenciaProcessor = ausenciaProcessor;
        this.activationPublisher = activationPublisher;
        this.clock = clock;
    }

    public SincronizacionResultado procesar(CatalogoSincronizado snapshot) {
        List<IdentidadExternaEquipoEntity> identidadesFootballData =
                equipoWriter.obtenerIdentidadesFootballData();
        boolean primerSnapshot = identidadesFootballData.isEmpty();
        Instant ahora = clock.instant();

        CatalogoEquipoWriter.Resultado equiposResultado = equipoWriter.escribir(snapshot, ahora);
        CatalogoJugadorWriter.Resultado jugadoresResultado = jugadorWriter.escribir(
                snapshot, equiposResultado.equiposPersistidos(), ahora);

        equipoWriter.flush();
        jugadorWriter.flush();

        ausenciaProcessor.procesar(snapshot, jugadoresResultado.jugadoresSnapshot(), ahora,
                primerSnapshot, identidadesFootballData);
        activationPublisher.publicar(jugadoresResultado.jugadoresActivados());

        return new SincronizacionResultado(
                snapshot.equipos().size(),
                snapshot.jugadores().size(),
                equiposResultado.creados(),
                snapshot.equipos().size() - equiposResultado.creados(),
                jugadoresResultado.creados(),
                snapshot.jugadores().size() - jugadoresResultado.creados()
        );
    }
}
