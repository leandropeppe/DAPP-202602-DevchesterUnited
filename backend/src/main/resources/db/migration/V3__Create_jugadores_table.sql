CREATE TABLE jugadores (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    league VARCHAR(100) NOT NULL,
    team VARCHAR(100) NOT NULL,
    position VARCHAR(30) NOT NULL,
    jersey_number INTEGER NOT NULL,
    nationality VARCHAR(100) NOT NULL,
    CONSTRAINT ck_jugadores_first_name CHECK (first_name ~ '[^[:space:]]'),
    CONSTRAINT ck_jugadores_last_name CHECK (last_name ~ '[^[:space:]]'),
    CONSTRAINT ck_jugadores_league CHECK (league ~ '[^[:space:]]'),
    CONSTRAINT ck_jugadores_team CHECK (team ~ '[^[:space:]]'),
    CONSTRAINT ck_jugadores_nationality CHECK (nationality ~ '[^[:space:]]'),
    CONSTRAINT ck_jugadores_position CHECK (
        position IN ('Forward', 'Midfielder', 'Defender', 'Goalkeeper')
    ),
    CONSTRAINT ck_jugadores_jersey_number CHECK (jersey_number BETWEEN 1 AND 99)
);

-- Semilla ficticia y determinista de desarrollo/aceptación, no plantillas deportivas reales.
-- 24 registros: 12 por equipo, 3 por posición en cada equipo; paginación default 20 + 4.
-- UUID asignados explícitamente; el orden de consulta debe declararse con ORDER BY id.
INSERT INTO jugadores (id, first_name, last_name, league, team, position, jersey_number, nationality)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'Álvaro', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Goalkeeper', 1, 'España'),
    ('30000000-0000-0000-0000-000000000002', 'Bruno', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Defender', 2, 'España'),
    ('30000000-0000-0000-0000-000000000003', 'Carlos', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Midfielder', 3, 'España'),
    ('30000000-0000-0000-0000-000000000004', 'Diego', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Forward', 4, 'España'),
    ('30000000-0000-0000-0000-000000000005', 'Emilio', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Goalkeeper', 5, 'España'),
    ('30000000-0000-0000-0000-000000000006', 'Felipe', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Defender', 6, 'España'),
    ('30000000-0000-0000-0000-000000000007', 'Gabriel', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Midfielder', 7, 'España'),
    ('30000000-0000-0000-0000-000000000008', 'Hugo', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Forward', 8, 'España'),
    ('30000000-0000-0000-0000-000000000009', 'Iván', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Goalkeeper', 9, 'España'),
    ('30000000-0000-0000-0000-000000000010', 'Javier', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Defender', 10, 'España'),
    ('30000000-0000-0000-0000-000000000011', 'Lucas', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Midfielder', 11, 'España'),
    ('30000000-0000-0000-0000-000000000012', 'Mateo', 'Ejemplo Real', 'LaLiga', 'Real Madrid', 'Forward', 12, 'España'),
    ('30000000-0000-0000-0000-000000000013', 'Álvaro', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Goalkeeper', 1, 'España'),
    ('30000000-0000-0000-0000-000000000014', 'Bruno', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Defender', 2, 'España'),
    ('30000000-0000-0000-0000-000000000015', 'Carlos', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Midfielder', 3, 'España'),
    ('30000000-0000-0000-0000-000000000016', 'Diego', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Forward', 4, 'España'),
    ('30000000-0000-0000-0000-000000000017', 'Emilio', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Goalkeeper', 5, 'España'),
    ('30000000-0000-0000-0000-000000000018', 'Felipe', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Defender', 6, 'España'),
    ('30000000-0000-0000-0000-000000000019', 'Gabriel', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Midfielder', 7, 'España'),
    ('30000000-0000-0000-0000-000000000020', 'Hugo', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Forward', 8, 'España'),
    ('30000000-0000-0000-0000-000000000021', 'Iván', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Goalkeeper', 9, 'España'),
    ('30000000-0000-0000-0000-000000000022', 'Javier', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Defender', 10, 'España'),
    ('30000000-0000-0000-0000-000000000023', 'Lucas', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Midfielder', 11, 'España'),
    ('30000000-0000-0000-0000-000000000024', 'Mateo', 'Ejemplo Atlético', 'LaLiga', 'Atlético de Madrid', 'Forward', 12, 'España');
