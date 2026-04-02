-- 인터넷 도용 탐지 결과에 판매자/침해자 정보 컬럼 추가
ALTER TABLE internet_detection_results
    ADD COLUMN platform_type VARCHAR(50),
    ADD COLUMN seller_name VARCHAR(200),
    ADD COLUMN business_reg_number VARCHAR(20),
    ADD COLUMN representative_name VARCHAR(100),
    ADD COLUMN business_address VARCHAR(500),
    ADD COLUMN contact_phone VARCHAR(50),
    ADD COLUMN contact_email VARCHAR(200),
    ADD COLUMN store_url VARCHAR(500);
