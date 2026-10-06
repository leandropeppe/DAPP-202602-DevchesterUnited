-- La columna legacy solo puede eliminarse si todas sus identidades
-- FOOTBALL_DATA fueron backfilleadas y siguen siendo consistentes.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM equipos e
        LEFT JOIN identidades_externas_equipo i
            ON i.equipo_id = e.id
           AND i.proveedor = 'FOOTBALL_DATA'
        WHERE e.external_id IS NOT NULL
          AND (
              i.id IS NULL
              OR i.external_id IS DISTINCT FROM e.external_id
          )
    ) THEN
        RAISE EXCEPTION
            'No se puede eliminar equipos.external_id: existen equipos sin identidad FOOTBALL_DATA coincidente';
    END IF;

END $$;

ALTER TABLE equipos DROP COLUMN external_id;
