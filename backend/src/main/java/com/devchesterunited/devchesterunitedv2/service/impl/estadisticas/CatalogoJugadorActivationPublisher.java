package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.service.event.JugadorActivadoEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CatalogoJugadorActivationPublisher {
    private final ApplicationEventPublisher eventos;

    public CatalogoJugadorActivationPublisher(ApplicationEventPublisher eventos) {
        this.eventos = eventos;
    }

    public void publicar(List<UUID> jugadoresActivados) {
        jugadoresActivados.forEach(id -> eventos.publishEvent(new JugadorActivadoEvent(id)));
    }
}
