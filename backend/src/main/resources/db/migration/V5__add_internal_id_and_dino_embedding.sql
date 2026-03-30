-- User internalId for TrustMark payload (32-bit userId encoding)
ALTER TABLE users ADD COLUMN internal_id BIGSERIAL UNIQUE;

-- DINOv2 embedding column
ALTER TABLE images ADD COLUMN embedding_dino bytea;

-- Watermark output dimensions
ALTER TABLE images ADD COLUMN watermark_width INTEGER;
ALTER TABLE images ADD COLUMN watermark_height INTEGER;
