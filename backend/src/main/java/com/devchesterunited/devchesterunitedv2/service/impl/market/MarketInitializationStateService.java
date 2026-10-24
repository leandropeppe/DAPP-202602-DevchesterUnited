package com.devchesterunited.devchesterunitedv2.service.impl.market;
import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.InicializacionMercadoResultado;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gestiona únicamente las transiciones persistentes del estado del mercado. */
@Service
public class MarketInitializationStateService {
    private final EstadoMercadoRepository estados;
    private final Clock clock;

    public MarketInitializationStateService(EstadoMercadoRepository estados, Clock clock) {
        this.estados = estados;
        this.clock = clock;
    }

    /**
     * Inicia una ejecución o reanuda una ejecución que ya estaba INITIALIZING.
     * La transición queda confirmada en una transacción propia antes de ejecutar etapas.
     * Si ya está INITIALIZING, este incremento solo devuelve el snapshot bloqueado;
     * la exclusión del propietario de la ejecución y la reanudación de etapas
     * pertenecen al coordinador de T024.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InicializacionMercadoResultado comenzarOReanudar() {
        Instant momento = clock.instant();
        estados.ensureSingleton(momento);
        EstadoMercadoEntity estado = estadoBloqueado();

        if ("UNINITIALIZED".equals(estado.getStatus())
                || "FAILED".equals(estado.getStatus())) {
            estado.pasarAInitializing(momento);
            estados.save(estado);
        }

        return resultado(estado);
    }

    /**
     * Persiste FAILED en una transacción independiente. Si el epoch ya cambió,
     * la ejecución antigua no puede sobrescribir el estado más reciente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<InicializacionMercadoResultado> marcarFailed(
            long epochEsperado, String motivo) {
        Optional<EstadoMercadoEntity> estadoBloqueado = estadoBloqueadoOpcional();
        if (estadoBloqueado.isEmpty()) {
            return Optional.empty();
        }
        EstadoMercadoEntity estado = estadoBloqueado.get();
        if (estado.getEpoch() != epochEsperado
                || !"INITIALIZING".equals(estado.getStatus())) {
            return Optional.empty();
        }

        estado.marcarFailed(motivo, clock.instant());
        estados.save(estado);
        return Optional.of(resultado(estado));
    }

    /** Confirma READY únicamente para la ejecución que completó todas las etapas. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<InicializacionMercadoResultado> marcarReady(long epochEsperado) {
        Optional<EstadoMercadoEntity> estadoBloqueado = estadoBloqueadoOpcional();
        if (estadoBloqueado.isEmpty()) {
            return Optional.empty();
        }
        EstadoMercadoEntity estado = estadoBloqueado.get();
        if (estado.getEpoch() != epochEsperado
                || !"INITIALIZING".equals(estado.getStatus())) {
            return Optional.empty();
        }

        estado.marcarReady(clock.instant());
        estados.save(estado);
        return Optional.of(resultado(estado));
    }

    private EstadoMercadoEntity estadoBloqueado() {
        return estadoBloqueadoOpcional().orElseThrow(() -> new IllegalStateException(
                "No existe la fila singleton del estado del mercado."));
    }

    private Optional<EstadoMercadoEntity> estadoBloqueadoOpcional() {
        return estados.findSingletonForUpdate();
    }

    private static InicializacionMercadoResultado resultado(EstadoMercadoEntity estado) {
        return new InicializacionMercadoResultado(
                com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado
                        .valueOf(estado.getStatus()),
                estado.getEpoch(),
                estado.getReservaInicialInicializada());
    }
}
