package com.devchesterunited.devchesterunitedv2.service.impl.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository.EstrategiaVisibleRepository;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaVisibleEntity;
import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.EstrategiasIniciales;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.EstrategiaVisibleService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstrategiaVisibleServiceImpl implements EstrategiaVisibleService {
    private final EstrategiaVisibleRepository repository;
    private final EstrategiasIniciales iniciales;
    private final Clock clock;

    public EstrategiaVisibleServiceImpl(EstrategiaVisibleRepository repository,
            EstrategiasIniciales iniciales, Clock clock) {
        this.repository = repository;
        this.iniciales = iniciales;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void seleccionar(String estrategiaId, int version) {
        EstrategiaValorizacion estrategia = estrategias().get(estrategiaId + ":" + version);
        if (estrategia == null) throw new IllegalArgumentException("La estrategia visible no está disponible.");
        repository.save(new EstrategiaVisibleEntity(estrategia.id(), estrategia.version(), Instant.now(clock)));
    }

    @Override
    @Transactional(readOnly = true)
    public SeleccionVisible obtener() {
        return repository.buscar().map(entity -> new SeleccionVisible(entity.getEstrategiaId(), entity.getEstrategiaVersion()))
                .orElse(new SeleccionVisible(iniciales.estrategiaVisibleId(), iniciales.estrategiaVisibleVersion()));
    }

    private Map<String, EstrategiaValorizacion> estrategias() {
        return iniciales.habilitadas().stream().collect(Collectors.toMap(
                estrategia -> estrategia.id() + ":" + estrategia.version(), Function.identity()));
    }
}
