package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorCatalogoProjection;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorPortfolioProjection;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta PostgreSQL con filtros literales opcionales (null significa ausente).
 * La capa superior valida filtros, offset y límite antes de invocar el repositorio.
 * No normaliza acentos ni invoca lógica del dominio.
 */
public interface JugadorRepository extends JpaRepository<JugadorEntity, UUID> {
    List<JugadorEntity> findAllByActivoTrueOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select jugador from JugadorEntity jugador where jugador.id = :id")
    java.util.Optional<JugadorEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select jugador from JugadorEntity jugador where jugador.id = :id")
    java.util.Optional<JugadorEntity> findByIdForRead(@Param("id") UUID id);

    java.util.List<JugadorEntity> findAllByEquipoId(UUID equipoId);
    java.util.List<JugadorEntity> findAllByEquipoIdAndActivoTrue(UUID equipoId);

    /** Devuelve descripciÃ³n canÃ³nica en una sola consulta, incluyendo jugadores inactivos. */
    @Query(value = """
            SELECT j.id AS "playerId",
                   j.name AS "playerName",
                   e.name AS "team",
                   e.league AS "league"
            FROM jugadores j
            LEFT JOIN equipos e ON e.id = j.team_id
            WHERE j.id IN (:playerIds)
            """, nativeQuery = true)
    List<JugadorPortfolioProjection> findPortfolioDescriptions(
            @Param("playerIds") Collection<UUID> playerIds);
    // Escapar primero ! para no volver a escapar los ! introducidos para % y _.
    // Los casts también permiten a PostgreSQL resolver el tipo de parámetros nulos.
    String FROM_Y_FILTROS = """
            FROM jugadores j
            JOIN equipos e ON e.id = j.team_id
            LEFT JOIN estrategia_visible ev ON ev.singleton_id = 1
            LEFT JOIN cotizacion_actual_estrategia cae
              ON cae.jugador_id = j.id
             AND cae.estrategia_id = ev.estrategia_id
             AND cae.estrategia_version = ev.estrategia_version
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
                   j.nationality AS nacionalidad,
                   cae.cotizacion AS valuation,
                   ev.estrategia_id AS strategyId,
                   ev.estrategia_version AS strategyVersion
            """;

    String FROM_CATALOGO_ACTIVO = """
            FROM jugadores j
            JOIN equipos e ON e.id = j.team_id
            LEFT JOIN estrategia_visible ev ON ev.singleton_id = 1
            LEFT JOIN cotizacion_actual_estrategia cae
              ON cae.jugador_id = j.id
             AND cae.estrategia_id = ev.estrategia_id
             AND cae.estrategia_version = ev.estrategia_version
            WHERE j.id = :id
              AND j.is_active = TRUE
              AND e.is_active = TRUE
            """;

    String FROM_RANKING = """
            FROM jugadores j
            JOIN equipos e ON e.id = j.team_id
            JOIN estrategia_visible ev ON ev.singleton_id = 1
            JOIN cotizacion_actual_estrategia cae
              ON cae.jugador_id = j.id
             AND cae.estrategia_id = ev.estrategia_id
             AND cae.estrategia_version = ev.estrategia_version
            WHERE j.is_active = TRUE
              AND e.is_active = TRUE
              AND cae.cotizacion IS NOT NULL
            """;

    @Query(value = COLUMNAS_CATALOGO + FROM_CATALOGO_ACTIVO, nativeQuery = true)
    java.util.Optional<JugadorCatalogoProjection> buscarActivoPorId(@Param("id") UUID id);

    /** Offset basado en cero; orden fijo, independiente de cualquier entrada del cliente. */
    @Query(value = COLUMNAS_CATALOGO + FROM_Y_FILTROS
            + " ORDER BY valuation DESC NULLS LAST, id ASC LIMIT :limite OFFSET :offset", nativeQuery = true)
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

    @Query(value = COLUMNAS_CATALOGO + FROM_RANKING
            + " ORDER BY cae.cotizacion DESC, j.id ASC LIMIT :limite", nativeQuery = true)
    List<JugadorCatalogoProjection> buscarRanking(@Param("limite") int limite);
}
