package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.util.List;
import java.util.UUID;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta PostgreSQL con filtros literales opcionales (null significa ausente).
 * La capa superior valida filtros, offset y límite antes de invocar el repositorio.
 * No normaliza acentos ni invoca lógica del dominio.
 */
public interface JugadorRepository extends JpaRepository<JugadorEntity, UUID> {
    Optional<JugadorEntity> findByIdExterno(Long idExterno);
    java.util.List<JugadorEntity> findAllByIdExternoIn(Collection<Long> idsExternos);
    java.util.List<JugadorEntity> findAllByEquipoId(UUID equipoId);
    // Escapar primero ! para no volver a escapar los ! introducidos para % y _.
    // Los casts también permiten a PostgreSQL resolver el tipo de parámetros nulos.
    String FILTROS = """
            WHERE j.is_active = TRUE
              AND EXISTS (SELECT 1 FROM equipos e WHERE e.id = j.team_id AND e.is_active = TRUE)
              AND (CAST(:liga AS text) IS NULL OR j.league ILIKE
                ('%' || replace(replace(replace(CAST(:liga AS text), '!', '!!'), '%', '!%'), '_', '!_') || '%') ESCAPE '!')
              AND (CAST(:equipo AS text) IS NULL OR j.team ILIKE
                ('%' || replace(replace(replace(CAST(:equipo AS text), '!', '!!'), '%', '!%'), '_', '!_') || '%') ESCAPE '!')
              AND (CAST(:posicion AS text) IS NULL OR j.position ILIKE
                ('%' || replace(replace(replace(CAST(:posicion AS text), '!', '!!'), '%', '!%'), '_', '!_') || '%') ESCAPE '!')
            """;

    /** Offset basado en cero; orden fijo, independiente de cualquier entrada del cliente. */
    @Query(value = "SELECT j.* FROM jugadores j " + FILTROS
            + " ORDER BY id ASC LIMIT :limite OFFSET :offset", nativeQuery = true)
    List<JugadorEntity> buscar(@Param("liga") String liga,
            @Param("equipo") String equipo,
            @Param("posicion") String posicion,
            @Param("offset") long offset,
            @Param("limite") int limite);

    /** Total de coincidencias antes de paginar, incluso cuando la página solicitada está vacía. */
    @Query(value = "SELECT COUNT(*) FROM jugadores j " + FILTROS, nativeQuery = true)
    long contar(@Param("liga") String liga,
            @Param("equipo") String equipo,
            @Param("posicion") String posicion);
}
