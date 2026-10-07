package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolución conservadora de equipos; nunca crea ni persiste durante el
 * staging. Solo una coincidencia canónica exacta dentro de la competición
 * permite devolver una identidad candidata para la promoción atómica.
 */
@Service
public class IdentidadEquipoResolver {
    private final EquipoRepository equipos;
    private final IdentidadExternaEquipoRepository identidades;

    public IdentidadEquipoResolver(EquipoRepository equipos,
            IdentidadExternaEquipoRepository identidades) {
        this.equipos = equipos;
        this.identidades = identidades;
    }

    public Resultado resolver(IdentidadEstadisticaJugador hint, CompeticionEstadistica competicion) {
        if (hint.teamId() != null) {
            var persisted = identidades.findByProveedorAndExternalId(
                    ProveedorIdentidadExterna.WHOSCORED, hint.teamId());
            if (persisted.isPresent()) {
                return new Resultado(persisted.get().getEquipo().getId(), null);
            }
        }
        if (hint.teamId() == null || hint.nombreEquipo() == null || hint.nombreEquipo().isBlank()) {
            throw new IdentidadEstadisticaNoResueltaException("Falta evidencia suficiente para resolver el equipo.");
        }
        String liga = nombreLiga(competicion);
        List<EquipoEntity> candidatos = equipos.findAllByActivoTrueAndLigaIgnoreCase(liga).stream()
                .filter(equipo -> canonico(equipo.getNombre()).equals(canonico(hint.nombreEquipo())))
                .toList();
        if (candidatos.size() != 1) {
            throw new IdentidadEstadisticaNoResueltaException("La identidad del equipo no es inequívoca.");
        }
        EquipoEntity candidato = candidatos.get(0);
        return new Resultado(candidato.getId(), new NuevaIdentidad(
                candidato.getId(), hint.teamId(), candidato));
    }

    public record Resultado(UUID equipoId, NuevaIdentidad identidadNueva) { }

    public record NuevaIdentidad(UUID equipoId, long externalId, EquipoEntity equipo) { }

    static String canonico(String value) {
        String normalizado = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC);
        return normalizado.trim().replaceAll("[\\s\\p{Z}]+", " ").toLowerCase(Locale.ROOT);
    }

    static String nombreLiga(CompeticionEstadistica competicion) {
        return switch (competicion) {
            case PREMIER_LEAGUE -> "Premier League";
            case LA_LIGA -> "La Liga";
            case SERIE_A -> "Serie A";
            case BUNDESLIGA -> "Bundesliga";
            case LIGUE_1 -> "Ligue 1";
        };
    }
}
