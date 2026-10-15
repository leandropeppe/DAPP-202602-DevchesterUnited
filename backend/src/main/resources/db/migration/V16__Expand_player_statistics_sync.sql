ALTER TABLE jugador_estadisticas
    ADD COLUMN apps INTEGER,
    ADD COLUMN sub_on INTEGER,
    ADD COLUMN man_of_the_match INTEGER,
    ADD COLUMN aerial_won_per_game NUMERIC(10,3),
    ADD COLUMN foul_given_per_game NUMERIC(10,3),
    ADD COLUMN offside_given_per_game NUMERIC(10,3),
    ADD COLUMN dispossessed_per_game NUMERIC(10,3),
    ADD COLUMN turnover_per_game NUMERIC(10,3),
    ADD COLUMN interception_per_game NUMERIC(10,3),
    ADD COLUMN fouls_per_game NUMERIC(10,3),
    ADD COLUMN offside_won_per_game NUMERIC(10,3),
    ADD COLUMN clearance_per_game NUMERIC(10,3),
    ADD COLUMN was_dribbled_per_game NUMERIC(10,3),
    ADD COLUMN outfielder_block_per_game NUMERIC(10,3),
    ADD COLUMN goal_own INTEGER,
    ADD COLUMN total_passes_per_game NUMERIC(10,3),
    ADD COLUMN pass_success NUMERIC(10,3),
    ADD COLUMN accurate_crosses_per_game NUMERIC(10,3),
    ADD COLUMN accurate_long_pass_per_game NUMERIC(10,3),
    ADD COLUMN accurate_through_ball_per_game NUMERIC(10,3);

ALTER TABLE estadisticas_sync_staging
    ADD COLUMN familia_pases_recibida BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN apps INTEGER, ADD COLUMN sub_on INTEGER, ADD COLUMN man_of_the_match INTEGER,
    ADD COLUMN aerial_won_per_game NUMERIC(10,3), ADD COLUMN foul_given_per_game NUMERIC(10,3),
    ADD COLUMN offside_given_per_game NUMERIC(10,3), ADD COLUMN dispossessed_per_game NUMERIC(10,3),
    ADD COLUMN turnover_per_game NUMERIC(10,3), ADD COLUMN interception_per_game NUMERIC(10,3),
    ADD COLUMN fouls_per_game NUMERIC(10,3), ADD COLUMN offside_won_per_game NUMERIC(10,3),
    ADD COLUMN clearance_per_game NUMERIC(10,3), ADD COLUMN was_dribbled_per_game NUMERIC(10,3),
    ADD COLUMN outfielder_block_per_game NUMERIC(10,3), ADD COLUMN goal_own INTEGER,
    ADD COLUMN total_passes_per_game NUMERIC(10,3), ADD COLUMN pass_success NUMERIC(10,3),
    ADD COLUMN accurate_crosses_per_game NUMERIC(10,3), ADD COLUMN accurate_long_pass_per_game NUMERIC(10,3),
    ADD COLUMN accurate_through_ball_per_game NUMERIC(10,3);

ALTER TABLE jugador_estadisticas ADD CONSTRAINT ck_jugador_estadisticas_pass_success
    CHECK (pass_success IS NULL OR (pass_success >= 0 AND pass_success <= 100));
