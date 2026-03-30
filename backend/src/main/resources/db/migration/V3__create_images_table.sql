CREATE TABLE images (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255)    NOT NULL,
    sha256      VARCHAR(64)     NOT NULL,
    file_size   INTEGER         NOT NULL,
    width       INTEGER         NOT NULL,
    height      INTEGER         NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    gcs_path    VARCHAR(500),
    embedding   bytea,
    watermark_payload VARCHAR(200),
    keywords    VARCHAR(500),
    source_type VARCHAR(20)     NOT NULL DEFAULT 'UPLOAD',
    source_platform VARCHAR(20),
    source_product_id VARCHAR(200),
    source_image_url VARCHAR(1000),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    indexed_at  TIMESTAMPTZ,
    UNIQUE(user_id, sha256)
);

CREATE INDEX idx_images_user_id ON images(user_id);
CREATE INDEX idx_images_status ON images(status);
CREATE INDEX idx_images_source_type ON images(source_type);
