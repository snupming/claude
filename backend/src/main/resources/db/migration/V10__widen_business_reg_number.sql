-- business_reg_number 컬럼 길이 확장 (20 → 50)
-- 구조화 파싱 시 부가 텍스트가 포함되거나 해외 사업자번호 등을 수용하기 위함
ALTER TABLE internet_detection_results
    ALTER COLUMN business_reg_number TYPE VARCHAR(50);
