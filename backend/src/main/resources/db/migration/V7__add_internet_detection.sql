-- 인터넷 도용 탐지 결과 테이블
ALTER TABLE detection_scans ADD COLUMN scan_type VARCHAR(20) NOT NULL DEFAULT 'INTERNET';

CREATE TABLE internet_detection_results (
    id              BIGSERIAL PRIMARY KEY,
    scan_id         BIGINT NOT NULL REFERENCES detection_scans(id) ON DELETE CASCADE,
    source_image_id BIGINT NOT NULL REFERENCES images(id),
    found_image_url VARCHAR(2000) NOT NULL,
    source_page_url VARCHAR(2000),
    source_page_title VARCHAR(500),
    sscd_similarity   DOUBLE PRECISION,
    dino_similarity   DOUBLE PRECISION,
    judgment          VARCHAR(20) NOT NULL,
    search_engine     VARCHAR(20) NOT NULL DEFAULT 'NAVER',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_inet_results_scan ON internet_detection_results(scan_id);
CREATE INDEX idx_inet_results_source ON internet_detection_results(source_image_id);
