ALTER TABLE usuarios ALTER COLUMN username TYPE VARCHAR(255);

ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_username;

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_username CHECK (
    char_length(username) BETWEEN 1 AND 255
    AND username !~ '[[:space:]]'
    AND username !~ '[[:cntrl:]]'
);
