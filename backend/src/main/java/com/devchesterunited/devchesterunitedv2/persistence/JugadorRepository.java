package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorCatalogoProjection;
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
    String FROM_Y_FILTROS = """
            FROM jugadores j
            JOIN equipos e ON e.id = j.team_id
            WHERE j.is_active = TRUE
              AND e.is_active = TRUE
              AND (CAST(:liga AS text) IS NULL OR e.league ILIKE
                ('%' || replace(replace(replace(CAST(:liga AS text), '!', '!!'), '%', '!%'), '_', '!_') || '%') ESCAPE '!')
              AND (CAST(:equipo AS text) IS NULL OR e.name ILIKE
                ('%' || replace(replace(replace(CAST(:equipo AS text), '!', '!!'), '%', '!%'), '_', '!_') || '%') ESCAPE '!')
              AND (CAST(:posicion AS text) IS NULL OR j.position ILIKE
                ('%' || replace(replace(replace(CAST(:posicion AS text), '!', '!!'), '%', '!%'), '_', '!_') || '%') ESCAPE '!')
            """;

    String COLUMNAS_CATALOGO = """
            SELECT j.id AS id,
                   j.name AS nombre,
                   e.league AS liga,
                   e.name AS equipo,
                   j.position AS posicion,
                   j.jersey_number AS dorsal,
                   j.nationality AS nacionalidad
            """;

    /** Offset basado en cero; orden fijo, independiente de cualquier entrada del cliente. */
    @Query(value = COLUMNAS_CATALOGO + FROM_Y_FILTROS
            + " ORDER BY id ASC LIMIT :limite OFFSET :offset", nativeQuery = true)
    List<JugadorCatalogoProjection> buscar(@Param("liga") String liga,
            @Param("equipo") String equipo,
            @Param("posicion") String posicion,
            @Param("offset") long offset,
            @Param("limite") int limite);

    /** Total de coincidencias antes de paginar, incluso cuando la página solicitada está vacía. */
    @Query(value = "SELECT COUNT(*) " + FROM_Y_FILTROS, nativeQuery = true)
    long contar(@Param("liga") String liga,
            @Param("equipo") String equipo,
            @Param("posicion") String posicion);
}
