package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorCatalogoProjection;
import com.devchesterunited.devchesterunitedv2.service.interfaces.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.dto.FiltroJugadoresConsulta;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadoresResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.PaginacionResultado;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JugadorServiceImpl implements JugadorService {
    private final JugadorRepository repositorio;

    public JugadorServiceImpl(JugadorRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JugadoresResultado consultar(FiltroJugadoresConsulta filtros) {
        Objects.requireNonNull(filtros, "Los filtros de consulta son obligatorios.");
        long offset = filtros.offset();
        long total = repositorio.contar(filtros.liga(), filtros.equipo(), filtros.posicion());
        // División con resto: evita overflow de total + limite - 1.
        long paginas = total / filtros.limite() + (total % filtros.limite() == 0 ? 0 : 1);
        List<JugadorResultado> jugadores = offset >= total ? List.of()
                : repositorio.buscar(filtros.liga(), filtros.equipo(), filtros.posicion(), offset, filtros.limite())
                        .stream().map(JugadorServiceImpl::aRespuesta).toList();
        return new JugadoresResultado(jugadores,
                new PaginacionResultado(filtros.pagina(), filtros.limite(), paginas, total));
    }

    private static JugadorResultado aRespuesta(JugadorCatalogoProjection entidad) {
        return new JugadorResultado(
                entidad.getId(),
                entidad.getNombre(),
                entidad.getLiga(),
                entidad.getEquipo(),
                entidad.getPosicion(),
                entidad.getDorsal() == null ? 0 : entidad.getDorsal(),
                entidad.getNacionalidad()
        );
    }
}
