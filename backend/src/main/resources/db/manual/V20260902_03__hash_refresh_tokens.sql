CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'refresh_tokens'
          AND column_name = 'token'
    ) THEN
        UPDATE refresh_tokens
        SET token_hash = encode(digest(token, 'sha256'), 'hex')
        WHERE token_hash IS NULL;
    END IF;
END
$$;

ALTER TABLE refresh_tokens
    ALTER COLUMN token_hash SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_tokens_token_hash
    ON refresh_tokens(token_hash);

ALTER TABLE refresh_tokens
    DROP COLUMN IF EXISTS token;
