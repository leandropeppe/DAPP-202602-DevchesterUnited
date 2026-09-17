-- Football-Data entrega un unico campo name con el nombre completo.
ALTER TABLE jugadores ADD COLUMN name VARCHAR(200);

UPDATE jugadores
SET name = trim(concat_ws(' ', first_name, last_name));

ALTER TABLE jugadores ALTER COLUMN name SET NOT NULL;
ALTER TABLE jugadores ADD CONSTRAINT ck_jugadores_name CHECK (name ~ '[^[:space:]]');
ALTER TABLE jugadores DROP CONSTRAINT ck_jugadores_first_name;
ALTER TABLE jugadores DROP CONSTRAINT ck_jugadores_last_name;
ALTER TABLE jugadores DROP COLUMN first_name;
ALTER TABLE jugadores DROP COLUMN last_name;
