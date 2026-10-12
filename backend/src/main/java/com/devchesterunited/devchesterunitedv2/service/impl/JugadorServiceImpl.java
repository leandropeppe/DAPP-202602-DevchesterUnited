package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorCatalogoProjection;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.dto.FiltroJugadoresConsulta;
import com.devchesterunited.devchesterunitedv2.service.dto.EstadisticasJugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadorDetalleResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.JugadoresResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.PaginacionResultado;
import com.devchesterunited.devchesterunitedv2.service.exception.JugadorNoEncontradoException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JugadorServiceImpl implements JugadorService {
    private final JugadorRepository repositorio;
    private final EstadisticasJugadorRepository estadisticasRepositorio;

    public JugadorServiceImpl(JugadorRepository repositorio,
            EstadisticasJugadorRepository estadisticasRepositorio) {
        this.repositorio = repositorio;
        this.estadisticasRepositorio = estadisticasRepositorio;
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

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JugadorDetalleResultado consultarDetalle(String idPublico) {
        UUID id = parsearIdPublico(idPublico);
        JugadorCatalogoProjection jugador = repositorio.buscarActivoPorId(id)
                .orElseThrow(JugadorNoEncontradoException::new);
        EstadisticasJugadorResultado estadisticas = estadisticasRepositorio.findByJugadorId(id)
                .map(JugadorServiceImpl::aEstadisticasResultado)
                .orElse(null);
        return new JugadorDetalleResultado(
                jugador.getId(),
                jugador.getNombre(),
                jugador.getLiga(),
                jugador.getEquipo(),
                jugador.getPosicion(),
                jugador.getDorsal() == null ? 0 : jugador.getDorsal(),
                jugador.getNacionalidad(),
                estadisticas);
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

    private static EstadisticasJugadorResultado aEstadisticasResultado(EstadisticasJugadorEntity entidad) {
        return new EstadisticasJugadorResultado(
                entidad.getPosicion(),
                entidad.getMinutosJugados(),
                entidad.getGoles(),
                entidad.getAsistencias(),
                entidad.getTiros(),
                entidad.getPasesClave(),
                entidad.getRegates(),
                entidad.getTackles(),
                entidad.getValoracion(),
                entidad.getTarjetasAmarillas(),
                entidad.getTarjetasRojas());
    }

    private static UUID parsearIdPublico(String idPublico) {
        if (idPublico == null || !idPublico.startsWith("ply_")) {
            throw new JugadorNoEncontradoException();
        }
        try {
            return UUID.fromString(idPublico.substring("ply_".length()));
        } catch (IllegalArgumentException exception) {
            throw new JugadorNoEncontradoException();
        }
    }
}
