-- 이미지 추정 라벨 및 감지 엔터티 컬럼 추가
ALTER TABLE internet_detection_results
    ADD COLUMN best_guess_label VARCHAR(200),
    ADD COLUMN detected_entity VARCHAR(200);
