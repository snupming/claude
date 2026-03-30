CREATE TABLE platform_connections (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform        VARCHAR(20)     NOT NULL,
    store_name      VARCHAR(200),
    status          VARCHAR(20)     NOT NULL DEFAULT 'CONNECTED',
    access_token    VARCHAR(500),
    refresh_token   VARCHAR(500),
    token_expires_at TIMESTAMPTZ,
    last_synced_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, platform)
);

CREATE INDEX idx_platform_connections_user_id ON platform_connections(user_id);
