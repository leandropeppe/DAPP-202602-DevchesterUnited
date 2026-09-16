package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.controller.dto.FiltroJugadoresDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.JugadorResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.JugadoresResponseDTO;
import com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.JugadorService;
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
    public JugadoresResponseDTO consultar(FiltroJugadoresDTO filtros) {
        Objects.requireNonNull(filtros, "Los filtros de consulta son obligatorios.");
        long offset = filtros.offset();
        long total = repositorio.contar(filtros.liga(), filtros.equipo(), filtros.posicion());
        // División con resto: evita overflow de total + limite - 1.
        long paginas = total / filtros.limite() + (total % filtros.limite() == 0 ? 0 : 1);
        List<JugadorResponseDTO> jugadores = offset >= total ? List.of()
                : repositorio.buscar(filtros.liga(), filtros.equipo(), filtros.posicion(), offset, filtros.limite())
                        .stream().map(JugadorServiceImpl::aRespuesta).toList();
        return new JugadoresResponseDTO(jugadores,
                new PaginacionDTO(filtros.pagina(), filtros.limite(), paginas, total));
    }

    private static JugadorResponseDTO aRespuesta(JugadorEntity entidad) {
        return new JugadorResponseDTO(
                "ply_" + entidad.getId(),
                entidad.getNombre(),
                entidad.getApellido(),
                entidad.getLiga(),
                entidad.getEquipo(),
                entidad.getPosicion(),
                entidad.getDorsal(),
                entidad.getNacionalidad()
        );
    }
}
