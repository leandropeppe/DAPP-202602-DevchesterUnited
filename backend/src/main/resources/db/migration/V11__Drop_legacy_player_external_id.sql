-- V10 ya copió cada jugadores.external_id a una identidad FOOTBALL_DATA.
-- Abortar si quedara alguna identidad legacy sin representar antes de eliminar la columna.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM jugadores j
        LEFT JOIN identidades_externas_jugador i
          ON i.jugador_id = j.id
         AND i.proveedor = 'FOOTBALL_DATA'
         AND i.external_id = j.external_id
        WHERE j.external_id IS NOT NULL
          AND i.id IS NULL
    ) THEN
        RAISE EXCEPTION 'No se puede eliminar jugadores.external_id: falta una identidad FOOTBALL_DATA migrada';
    END IF;
END $$;

ALTER TABLE jugadores DROP COLUMN external_id;
