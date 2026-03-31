-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- vector 컬럼 추가 (기존 bytea 유지, 병행)
ALTER TABLE images ADD COLUMN embedding_sscd vector(512);
ALTER TABLE images ADD COLUMN embedding_dino_vec vector(384);

-- HNSW 인덱스 (코사인 유사도)
CREATE INDEX idx_images_embedding_sscd ON images
  USING hnsw (embedding_sscd vector_cosine_ops) WITH (m = 16, ef_construction = 200);
CREATE INDEX idx_images_embedding_dino ON images
  USING hnsw (embedding_dino_vec vector_cosine_ops) WITH (m = 16, ef_construction = 200);

-- 탐지 스캔 이력
CREATE TABLE detection_scans (
  id BIGSERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  total_images INTEGER NOT NULL DEFAULT 0,
  scanned_images INTEGER NOT NULL DEFAULT 0,
  matches_found INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);

-- 탐지 결과
CREATE TABLE detection_results (
  id BIGSERIAL PRIMARY KEY,
  scan_id BIGINT NOT NULL REFERENCES detection_scans(id) ON DELETE CASCADE,
  source_image_id BIGINT NOT NULL REFERENCES images(id),
  matched_image_id BIGINT NOT NULL REFERENCES images(id),
  matched_user_id UUID NOT NULL,
  sscd_similarity DOUBLE PRECISION,
  dino_similarity DOUBLE PRECISION,
  judgment VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_detection_scans_user ON detection_scans(user_id);
CREATE INDEX idx_detection_results_scan ON detection_results(scan_id);
CREATE UNIQUE INDEX idx_detection_results_unique_pair
  ON detection_results(scan_id, source_image_id, matched_image_id);
