-- Football-Data puede omitir la posicion principal de un jugador.
-- El CHECK existente sigue restringiendo los valores informados; NULL representa
-- una posicion no clasificada por el proveedor.
ALTER TABLE jugadores ALTER COLUMN position DROP NOT NULL;
