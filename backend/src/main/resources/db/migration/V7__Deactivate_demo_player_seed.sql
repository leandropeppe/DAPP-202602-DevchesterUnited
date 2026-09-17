-- La semilla V3 se conserva para trazabilidad, pero no forma parte del catalogo consultable.
-- No se eliminan filas ni se afectan jugadores importados (external_id IS NOT NULL).
UPDATE jugadores
SET is_active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE external_id IS NULL
  AND id::text LIKE '30000000-0000-0000-0000-0000000000%';
