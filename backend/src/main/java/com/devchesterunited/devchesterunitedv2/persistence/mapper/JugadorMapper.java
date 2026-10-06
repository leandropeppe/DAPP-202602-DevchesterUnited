package com.devchesterunited.devchesterunitedv2.persistence.mapper;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoJugador;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Traduce el perfil de dominio de un jugador a su representación JPA. */
@Component
public class JugadorMapper {

    /** Mapea una fila persistida a su representación de dominio. */
    public Jugador toDomain(JugadorEntity entidad) {
        Objects.requireNonNull(entidad, "entidad es obligatoria");
        return Jugador.desdePersistencia(
                entidad.getId(),
                entidad.getNombre(),
                entidad.getEquipoId(),
                entidad.getPosicion(),
                entidad.getNumeroCamiseta(),
                entidad.getNacionalidad(),
                EstadoJugador.desdePersistencia(
                        entidad.isActivo(), entidad.getCreadoEn(), entidad.getActualizadoEn()));
    }

    /**
     * Mapea un jugador del snapshot, conservando la identidad local y la fecha
     * de creación cuando ya existe una fila persistida.
     */
    public JugadorEntity toEntity(Jugador jugador, UUID equipoId,
            JugadorEntity anterior, Instant ahora) {
        Objects.requireNonNull(jugador, "jugador es obligatorio");
        Objects.requireNonNull(equipoId, "equipoId es obligatorio");
        Objects.requireNonNull(ahora, "ahora es obligatorio");

        JugadorEntity entidad = new JugadorEntity(
                anterior == null ? jugador.getId() : anterior.getId(),
                jugador.getNombre(),
                equipoId,
                jugador.getPosicion(),
                jugador.getDorsal(),
                jugador.getNacionalidad(),
                jugador.isActivo());
        entidad.conservarAuditoria(anterior == null ? ahora : anterior.getCreadoEn(), ahora);
        return entidad;
    }

    /** Mapea una fila existente al estado inactivo sin alterar sus datos. */
    public JugadorEntity toInactiveEntity(JugadorEntity actual, Instant ahora) {
        Objects.requireNonNull(actual, "actual es obligatorio");
        Objects.requireNonNull(ahora, "ahora es obligatorio");

        JugadorEntity entidad = new JugadorEntity(
                actual.getId(),
                actual.getNombre(),
                actual.getEquipoId(),
                actual.getPosicion(),
                actual.getNumeroCamiseta(),
                actual.getNacionalidad(),
                false);
        entidad.conservarAuditoria(actual.getCreadoEn(), ahora);
        return entidad;
    }
}
