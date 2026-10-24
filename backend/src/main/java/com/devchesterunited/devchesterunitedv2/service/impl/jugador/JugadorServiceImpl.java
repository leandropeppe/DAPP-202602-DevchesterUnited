package com.devchesterunited.devchesterunitedv2.service.impl.jugador;

import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.projection.JugadorCatalogoProjection;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.jugador.JugadorService;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.FiltroJugadoresConsulta;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.EstadisticasJugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorDetalleResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadoresResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.jugador.JugadorRankingResultado;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import com.devchesterunited.devchesterunitedv2.exception.JugadorNoEncontradoException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
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
                estadisticas,
                jugador.getValuation());
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<JugadorRankingResultado> consultarRanking(int limite) {
        if (limite < 1 || limite > 100) {
            throw new IllegalArgumentException("El limite debe estar entre 1 y 100.");
        }
        List<JugadorCatalogoProjection> jugadores = repositorio.buscarRanking(limite);
        return IntStream.range(0, jugadores.size())
                .mapToObj(indice -> {
                    JugadorCatalogoProjection jugador = jugadores.get(indice);
                    return new JugadorRankingResultado(indice + 1, jugador.getId(), jugador.getNombre(),
                            jugador.getPosicion(), jugador.getEquipo(), jugador.getValuation(),
                            jugador.getStrategyId(), jugador.getStrategyVersion());
                })
                .toList();
    }

    private static JugadorResultado aRespuesta(JugadorCatalogoProjection entidad) {
        return new JugadorResultado(
                entidad.getId(),
                entidad.getNombre(),
                entidad.getLiga(),
                entidad.getEquipo(),
                entidad.getPosicion(),
                entidad.getDorsal() == null ? 0 : entidad.getDorsal(),
                entidad.getNacionalidad(),
                entidad.getValuation()
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
                entidad.getTarjetasRojas(),
                entidad.getApps() == null && entidad.getSubOn() == null && entidad.getManOfTheMatch() == null && entidad.getAerialWonPerGame() == null ? null : new EstadisticasJugadorResultado.Generales(entidad.getApps(), entidad.getSubOn(), entidad.getMinutosJugados(),
                        entidad.getManOfTheMatch(), entidad.getAerialWonPerGame(), entidad.getValoracion(), entidad.getTarjetasAmarillas(), entidad.getTarjetasRojas()),
                entidad.getFoulGivenPerGame() == null && entidad.getOffsideGivenPerGame() == null && entidad.getDispossessedPerGame() == null && entidad.getTurnoverPerGame() == null ? null : new EstadisticasJugadorResultado.Ofensivas(entidad.getGoles(), entidad.getAsistencias(), entidad.getTiros(), entidad.getPasesClave(), entidad.getRegates(),
                        entidad.getFoulGivenPerGame(), entidad.getOffsideGivenPerGame(), entidad.getDispossessedPerGame(), entidad.getTurnoverPerGame()),
                entidad.getInterceptionPerGame() == null && entidad.getFoulsPerGame() == null && entidad.getOffsideWonPerGame() == null && entidad.getClearancePerGame() == null && entidad.getWasDribbledPerGame() == null && entidad.getOutfielderBlockPerGame() == null && entidad.getGoalOwn() == null ? null : new EstadisticasJugadorResultado.Defensivas(entidad.getTackles(), entidad.getInterceptionPerGame(), entidad.getFoulsPerGame(), entidad.getOffsideWonPerGame(),
                        entidad.getClearancePerGame(), entidad.getWasDribbledPerGame(), entidad.getOutfielderBlockPerGame(), entidad.getGoalOwn()),
                entidad.getTotalPassesPerGame() == null && entidad.getPassSuccess() == null && entidad.getAccurateCrossesPerGame() == null && entidad.getAccurateLongPassPerGame() == null && entidad.getAccurateThroughBallPerGame() == null ? null : new EstadisticasJugadorResultado.Pases(entidad.getTotalPassesPerGame(), entidad.getPassSuccess(), entidad.getAccurateCrossesPerGame(),
                        entidad.getAccurateLongPassPerGame(), entidad.getAccurateThroughBallPerGame()));
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
