CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(255)    NOT NULL UNIQUE,
    user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token) WHERE revoked = FALSE;
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
