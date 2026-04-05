-- users 테이블: 네이버 전용 사용자는 이메일/비밀번호 없이 가입 가능
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- 네이버 계정 식별자
ALTER TABLE users ADD COLUMN naver_account_uid VARCHAR(100) UNIQUE;
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'EMAIL';

-- platform_connections: 네이버 커머스 솔루션 관련 필드 추가
ALTER TABLE platform_connections ADD COLUMN channel_no BIGINT;
ALTER TABLE platform_connections ADD COLUMN store_url VARCHAR(500);
ALTER TABLE platform_connections ADD COLUMN solution_id VARCHAR(100);
ALTER TABLE platform_connections ADD COLUMN subscription_id VARCHAR(100);
ALTER TABLE platform_connections ADD COLUMN plan_id VARCHAR(100);
ALTER TABLE platform_connections ADD COLUMN subscription_status VARCHAR(50);
ALTER TABLE platform_connections ADD COLUMN business_registration_number VARCHAR(50);
