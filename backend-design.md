# Ownpic 백엔드 설계서

> **문서 버전**: v3.0 | **작성일**: 2026.03.28
> **통합 문서**: phase1~5, phase-a~b, EVIDENCE_IMPL_PLAN, LOCAL_TEST_GUIDE, gcp-deployment-guide, full-mvp-execution-plan 통합

---

## 1. 프로젝트 현황

| 모듈 | 백엔드 | 프론트엔드 | 테스트 |
|------|--------|-----------|--------|
| auth | ✅ 완성 (signup/login/JWT/refresh/logout) | 🔲 미연동 | ✅ 19 tests |
| image | ✅ 완성 (업로드→워터마킹→SSCD+DINOv2 듀얼 임베딩) | 🔲 미연동 | ✅ 25 tests |
| detection | ✅ 완성 (듀얼 탐지 A/B + 자동스캔 + 프라이버시) | 🔲 미연동 | ✅ 20 tests |
| evidence | ✅ 코드 완성 (TrustMark 디코더 + 공정위 + PDF) | 🔲 미연동 | ✅ 존재 |
| billing | 🔲 스텁만 (4파일) | 🔲 | 🔲 |
| notification | 🔲 포트만 (2파일) | 🔲 | 🔲 |
| shared | ✅ (SecurityConfig, JacksonConfig, OpenApiConfig) | — | ✅ 9 tests |

**ONNX 모델 4개**: SSCD(93MB) + DINOv2(85MB) + TrustMark Encoder(16.5MB) + Decoder(45MB) = 약 240MB

---

## 2. 기술 스택 & 의존성

| 의존성 | 버전 | 비고 |
|---|---|---|
| Java | 25 (toolchain, foojay 자동 다운로드) | |
| Gradle | 9.3.1 | 9.4.x 배포 서버 404 |
| Spring Boot | 4.0.4 | 2026.03.19 릴리스 |
| Spring Cloud GCP BOM | 8.0.1 | Boot 4.0.x 호환 |
| springdoc-openapi | **3.0.2** | 3.x = Boot 4 전용 |
| Flyway | Boot 4 BOM 관리 | |
| Bucket4j | 8.14.0 | 인메모리 Rate Limiting |
| logstash-logback-encoder | 8.0 | 구조화 JSON 로깅 |
| JJWT | 0.12.6 | JWT 생성/검증 |
| pgvector-java | 0.1.6 | pgvector JPA 지원 |
| ONNX Runtime | 1.23.2 | SSCD + TrustMark 추론 |
| OpenPDF | 1.3.35 | 증거 PDF 리포트 |

### gradle/libs.versions.toml

```toml
[versions]
spring-boot = "4.0.4"
spring-dependency-management = "1.1.7"
spring-cloud-gcp = "8.0.1"
springdoc = "3.0.2"
bucket4j = "8.14.0"
logstash = "8.0"
jjwt = "0.12.6"
pgvector = "0.1.6"
onnxruntime = "1.23.2"
openpdf = "1.3.35"

[libraries]
springdoc-webmvc = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }
bucket4j-core = { module = "com.bucket4j:bucket4j_jdk17-core", version.ref = "bucket4j" }
logstash-encoder = { module = "net.logstash.logback:logstash-logback-encoder", version.ref = "logstash" }
jjwt-api = { module = "io.jsonwebtoken:jjwt-api", version.ref = "jjwt" }
jjwt-impl = { module = "io.jsonwebtoken:jjwt-impl", version.ref = "jjwt" }
jjwt-jackson = { module = "io.jsonwebtoken:jjwt-jackson", version.ref = "jjwt" }
pgvector = { module = "com.pgvector:pgvector", version.ref = "pgvector" }
onnxruntime = { module = "com.microsoft.onnxruntime:onnxruntime", version.ref = "onnxruntime" }
openpdf = { module = "com.github.librepdf:openpdf", version.ref = "openpdf" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

---

## 3. 프로젝트 구조

```
src/main/java/com/ownpic/
├── OwnpicApplication.java          # @SpringBootApplication @EnableAsync @EnableScheduling
├── shared/
│   ├── exception/ (OwnpicException, GlobalExceptionHandler)
│   ├── dto/ApiPaths.java
│   ├── config/ (OpenApiConfig, RestClientConfig)
│   └── ml/ (OnnxModelResolver, PgvectorUtils)
├── auth/
│   ├── AuthService.java
│   ├── RefreshTokenCleanupTask.java
│   ├── controller/AuthController.java
│   ├── domain/ (User, UserRepository, RefreshToken, RefreshTokenRepository, AuthProvider, UserRole)
│   ├── jwt/ (JwtTokenProvider, JwtAuthFilter, JwtProperties)
│   └── exception/ (AuthenticationFailedException, DuplicateEmailException, InvalidTokenException)
├── image/
│   ├── ImageService.java / ImageIngestionService.java
│   ├── ImageProtectedEvent / ImageIndexedEvent
│   ├── controller/ImageController.java
│   ├── domain/ (Image, ImageRepository, ImageStatus, SourceType)
│   ├── port/ (ImageStoragePort, WatermarkPort)
│   ├── adapter/ (LocalFileStorageAdapter, TrustMarkWatermarkAdapter, TrustMarkPayloadBuilder)
│   └── exception/
├── detection/
│   ├── DetectionService.java
│   ├── service/AutoScanService.java
│   ├── controller/DetectionController.java
│   ├── domain/ (Detection, DetectionResult, ScanJob, SearchMode, SellerAction, ScanJobStatus, ScanPlatform)
│   ├── port/ (SscdEmbeddingPort, SimilarImageSearchPort, ShoppingSearchPort)
│   ├── adapter/ (SscdEmbeddingAdapter, PgvectorSearchAdapter, NaverShoppingSearchAdapter, ImagePreprocessor)
│   └── listener/ (ImageProtectedListener, ImageIndexedListener)
├── evidence/
│   ├── EvidenceService.java
│   ├── domain/ (Evidence, EvidenceRepository, EvidenceStatus, BizLookupStatus, EvidenceStrength)
│   ├── controller/EvidenceController.java
│   ├── port/ (BusinessInfoLookupPort, WatermarkDecoderPort)
│   ├── adapter/ (FtcBusinessLookupAdapter, TrustMarkDecoderAdapter)
│   ├── report/PdfReportGenerator.java
│   └── exception/EvidenceNotFoundException.java
├── billing/ (BillingService, port/, exception/)
└── notification/ (port/)
```

### Flyway 마이그레이션

```
db/migration/
├── V20260322001__enable_pgvector.sql
├── V20260322002__create_users.sql
├── V20260322003__create_refresh_tokens.sql
├── V20260322004__create_images.sql
├── V20260322005__add_embedding_index.sql
├── V20260322006__create_detections.sql
├── V20260322007__create_evidences.sql
├── V20260322008__create_subscriptions.sql
├── V20260322009__create_notifications.sql
└── V20260322010__create_platforms.sql
```

---

## 4. GCP 인프라

### 4.1 프로젝트

단일 프로젝트 `ownpic`, 리전 `asia-northeast3` (서울). 서비스 이름 접미사(`-dev`, `-prod`)로 환경 분리.

### 4.2 Cloud Run

| 항목 | Backend | Frontend |
|------|---------|----------|
| CPU | 1 vCPU | 1 vCPU |
| Memory | **2Gi** | 256Mi |
| Min instances | 0→1 (베타 시) | 0 |
| Max instances | 3 | 2 |
| Concurrency | 200 | 80 |
| Startup CPU boost | ✅ | — |
| Request timeout | 600s (SSE) | 60s |

### 4.3 서비스 계정 & IAM

**ownpic-backend**: secretmanager.secretAccessor, storage.objectAdmin, cloudtrace.agent, logging.logWriter, monitoring.metricWriter

**ownpic-frontend**: logging.logWriter

**Cloud Build**: run.admin, artifactregistry.writer, iam.serviceAccountUser

### 4.4 Secret Manager (Cloud Run native --set-secrets)

```
ownpic-{env}-db-url / db-username / db-password
ownpic-{env}-auth-jwt-secret / auth-naver-client-id / auth-naver-client-secret / auth-kakao-client-id / auth-kakao-client-secret
ownpic-{env}-billing-portone-secret
ownpic-{env}-notification-resend-api-key
```

### 4.5 GCS / Artifact Registry / Cloud Build

- GCS: `ownpic-{env}-images` (Standard), `ownpic-prod-backups` (Nearline 90일), `ownpic-prod-models` (ONNX)
- Artifact Registry: `ownpic-docker`, asia-northeast3, 최근 5태그
- Cloud Build: 2 트리거 (`backend/**`, `frontend/**`), 무료 2,500분/월

### 4.6 Cloud Scheduler / Monitoring

- Scheduler: Supabase keepalive 6시간마다 (`/actuator/health`)
- Monitoring: 5xx 급증(5분 내 10건+), 메모리 80%+(2Gi 기준 ~1.6Gi)

---

## 5. DB 설계

### 5.1 Supabase Session Mode

Session Mode Pooler 5432 사용. 직접 호스트는 IPv6 전용으로 국내 ISP 연결 불가. Transaction Mode(6543)는 prepared statement 충돌으로 미채택.

HikariCP: `maximum-pool-size: 10`, `minimum-idle: 2`, `connection-timeout: 5000`

### 5.2 스키마

```sql
CREATE TABLE images (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL, gcs_path VARCHAR(500) NOT NULL,
    file_size INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROTECTED',
    embedding vector(512), watermark_payload VARCHAR(200),
    keywords VARCHAR(500) NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'UPLOAD',
    source_product_id VARCHAR(200), source_image_url VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), indexed_at TIMESTAMPTZ,
    UNIQUE(user_id, sha256)
);
CREATE INDEX idx_images_embedding ON images
    USING hnsw (embedding vector_cosine_ops) WITH (m=16, ef_construction=200);

CREATE TABLE scan_jobs (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL,
    platform VARCHAR(20) NOT NULL, keyword VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    images_scanned INTEGER DEFAULT 0, matches_found INTEGER DEFAULT 0,
    started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE detections (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL,
    scan_job_id BIGINT REFERENCES scan_jobs(id),
    query_image_gcs VARCHAR(500), query_embedding vector(512) NOT NULL,
    search_mode VARCHAR(10) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE detection_results (
    id BIGSERIAL PRIMARY KEY, detection_id BIGINT NOT NULL REFERENCES detections(id) ON DELETE CASCADE,
    matched_image_id BIGINT NOT NULL, similarity DOUBLE PRECISION NOT NULL,
    suspect_source_url VARCHAR(1000), suspect_mall_name VARCHAR(200),
    seller_action VARCHAR(20), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evidences (
    id BIGSERIAL PRIMARY KEY, detection_result_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL, original_image_id BIGINT NOT NULL,
    original_registered_at TIMESTAMPTZ NOT NULL,
    suspect_image_gcs VARCHAR(500) NOT NULL, suspect_source_url VARCHAR(1000),
    suspect_mall_name VARCHAR(200),
    sscd_similarity DOUBLE PRECISION NOT NULL,
    watermark_detected BOOLEAN NOT NULL, watermark_payload VARCHAR(200),
    evidence_strength VARCHAR(20) NOT NULL,
    biz_lookup_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    biz_name VARCHAR(200), biz_registration_no VARCHAR(20),
    biz_representative VARCHAR(100), biz_address VARCHAR(500),
    biz_status VARCHAR(20), biz_permit_no VARCHAR(100),
    biz_registered_at VARCHAR(20), biz_domain VARCHAR(500),
    biz_candidates JSONB,
    report_gcs_path VARCHAR(500), package_gcs_path VARCHAR(500),
    package_hash VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 6. application.yml

```yaml
# 공통
spring:
  application.name: ownpic-backend
  threads.virtual.enabled: true
  jpa:
    open-in-view: false
    hibernate.ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
  mvc.problemdetails.enabled: true
ownpic:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiration: 900000    # 15분
    refresh-token-expiration: 604800000 # 7일
```

```yaml
# dev
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/postgres}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}
    hikari: { maximum-pool-size: 10, minimum-idle: 2, connection-timeout: 5000 }
  jpa.show-sql: true
logging.level.com.ownpic: DEBUG
```

```yaml
# prod
spring:
  datasource: { url: ${DB_URL}, username: ${DB_USERNAME}, password: ${DB_PASSWORD} }
  jpa.show-sql: false
springdoc.swagger-ui.enabled: false
logging.level.com.ownpic: INFO
```

---

## 7. API 설계

`ApiPaths.V1 = "/api/v1"`

| Method | Path | 모듈 | 설명 |
|--------|------|------|------|
| POST | /auth/signup | auth | 회원가입 |
| POST | /auth/login | auth | 로그인 |
| POST | /auth/refresh | auth | 토큰 갱신 |
| POST | /auth/logout | auth | 로그아웃 |
| GET | /auth/me | auth | 현재 사용자 |
| POST | /images/upload | image | 이미지 업로드 (multipart) |
| GET | /images | image | 목록 |
| GET | /images/{id} | image | 상세 |
| POST | /detections/detect | detection | 탐지 실행 (multipart) |
| GET | /detections/{id} | detection | 탐지 상세 |
| PATCH | /detections/results/{id}/action | detection | 결과 액션 |
| POST | /evidences | evidence | 증거 생성 (비동기) |
| GET | /evidences | evidence | 목록 |
| GET | /evidences/{id} | evidence | 상세 |
| PUT | /evidences/{id}/biz-selection | evidence | 셀러 선택 |
| PUT | /evidences/{id}/biz-retry | evidence | 재조회 |

---

## 8. 보안 & 인증

### 8.1 User.id 전략

내부 PK: `BIGSERIAL` (조인/FK 성능). 외부: `public_id UUID` (API 노출).

### 8.2 JWT + Refresh Token Rotation

| 항목 | 값 |
|------|---|
| Access Token | 15분, HS256 |
| Refresh Token | 7일, SHA-256 해시 DB 저장 |
| Rotation | 갱신 시 기존 revoked + 새 토큰 |
| 탈취 감지 | revoked 토큰 재사용 → 전체 무효화 |

### 8.3 구현 상태

W03~W04 완료 (이메일 JWT + BFF). OAuth2 네이버/카카오는 Phase B 이후 연기.

## 9. 서비스 흐름 — 보호

### 9.1 파이프라인

```
POST /api/images/upload (multipart) — 동기 ~600ms
  (1) 이미지 검증 (포맷, 크기, 해상도)
  (2) SHA256 → 중복 체크 (409 Conflict + 기존 ID)
  (3) TrustMark 워터마킹 (399ms)
  (4) GCS 저장 (~200ms)
  (5) DB INSERT (status=PROTECTED, embedding=null)
  (6) 응답 "보호 완료"
  (7) ImageProtectedEvent 발행
비동기 (@Async + @TransactionalEventListener AFTER_COMMIT)
  (8) GCS에서 워터마킹 이미지 로드
  (9) 패딩 트리밍 → SSCD 임베딩 (287ms)
  (10) pgvector INSERT + status→INDEXED
  (11) ImageIndexedEvent → 자동 스캔 트리거
```

### 9.2 대량 업로드

프론트 병렬 3개씩. 비동기 장당 ~1초.

### 9.3 GCS 경로

`ownpic-{env}-images/{userId}/{imageId}.webp` (워터마킹 버전이 유일 저장본)

### 9.4 TrustMark 페이로드

`userId(32bit) + imageId(32bit) + timestamp(24bit) + checksum(12bit) = 100bit`

### 9.5 실패 처리

| 실패 지점 | 처리 |
|----------|------|
| 이미지 검증 | 400, DB/GCS 없음 |
| TrustMark 인코딩 | 500, 프론트 재시도 |
| GCS 업로드 | 500, DB 없음 |
| DB INSERT | GCS 고아 → 삭제 시도 + 정기 클린업 |
| SHA256 중복 | 409 + 기존 이미지 ID |
| SSCD 임베딩 | 3회 재시도 (1s→2s→4s). 실패 시 PROTECTED 유지 |
| 이벤트 유실 | MVP: 대시보드 "재인덱싱" 수동 트리거 |

---

## 10. 서비스 흐름 — 탐지

### 10.1 3가지 모드

| 모드 | 시작 | 비교 범위 | MVP |
|------|------|----------|-----|
| A. 수동 (내 이미지) | 셀러 | 내 보호 이미지 | ✅ |
| B. 사전 확인 | 셀러 | 전체 (블러) | ✅ |
| C. 자동 | 시스템 | 내 보호 이미지 | ✅ |

### 10.2 수동 탐지 흐름 (~400ms)

(1) 검증 → (2) SSCD 임베딩(287ms) → (3) pgvector 검색(<50ms) → (4) DB INSERT → (5) 결과 반환

### 10.3 프라이버시 (모드 B)

| 정보 | 모드 A | 모드 B |
|------|-------|-------|
| 썸네일 | 원본 | 블러 |
| 소유자 | 본인 | "다른 셀러의 보호 이미지" |
| 증거 생성 | 가능 | 불가 |

### 10.4 자동 탐지 (모드 C) — 네이버 쇼핑 API

| 항목 | 값 |
|------|---|
| URL | `https://openapi.naver.com/v1/search/shop.json` |
| 인증 | X-Naver-Client-Id + Secret |
| 일일 한도 | 25,000회 |
| 트리거 | ImageIndexedEvent 시 자동 1회 + 수동 재스캔 |

API 호출량: 셀러 10명=200~600/일(1~2%), 50명=1,000~3,000(4~12%), 100명=3,000~9,000(12~36%)

키워드 품질: 자동 토큰화 1~3개 + 검색 결과 건수 미리보기

---

## 11. 서비스 흐름 — 증거

### 11.1 증거 강도

- **CONCLUSIVE**: TrustMark CRC 통과 + SSCD ≥ 0.30
- **CIRCUMSTANTIAL**: TrustMark CRC 실패 + SSCD ≥ 0.30

### 11.2 비동기 생성 파이프라인

```
POST /evidences → 202 (GENERATING, PENDING)
  [@Async]
  (1) DetectionResult 조회
  (2) Image 조회 → gcsPath, watermarkPayload
  (3) 원본 이미지 로드
  (4) 도용 이미지 로드/다운로드
  (5) TrustMark 디코딩 → evidence_strength
  (6) 공정위 API 조회 (best-effort)
  (7) PDF 리포트 생성 (OpenPDF)
  (8) PDF + 이미지 저장
  (9) SHA256 해시 → package_hash
  (10) DB UPDATE: COMPLETED
```

### 11.3 상태 전이 매트릭스 (6개 유효 조합)

| status | biz_lookup_status | 의미 |
|--------|------------------|------|
| GENERATING | PENDING | 초기 |
| COMPLETED | FOUND | 사업자 단일 매칭 |
| COMPLETED | MULTIPLE_FOUND | 셀러 선택 대기 |
| COMPLETED | NOT_FOUND | 사업자 미발견 |
| COMPLETED | API_ERROR | 재조회 가능 |
| FAILED | PENDING | 생성 실패 |

```
(GENERATING, PENDING)
  ├─ 실패 → (FAILED, PENDING)
  ├─ 1건 → (COMPLETED, FOUND)
  ├─ 2~10건 → (COMPLETED, MULTIPLE_FOUND) → 셀러선택 → (COMPLETED, FOUND)
  ├─ 0건/10건+ → (COMPLETED, NOT_FOUND)
  └─ API장애 → (COMPLETED, API_ERROR) → 재조회 → FOUND/NOT_FOUND/MULTIPLE_FOUND
```

### 11.4 공정위 통신판매사업자 API

URL: `http://apis.data.go.kr/1130000/MllBs_1Service/getMllBsPrmsnMgtNoInfo_1`

| mallName 매칭 | 결과 | 대응 |
|-------------|------|------|
| 정확 1건 | FOUND | 자동 채택 |
| 2~10건 | MULTIPLE_FOUND | 셀러 수동 선택 |
| 0건/10건+ | NOT_FOUND | 수동 입력 안내 |
| API 장애 | API_ERROR | 재조회 버튼 |

타임아웃: 연결 2초, 읽기 3초. 재시도 최대 2회. 장애 시 증거 생성은 계속 진행.

### 11.5 PDF 리포트

```
┌─────────────────────────────────────┐
│  Ownpic 이미지 도용 증거 리포트       │
├─ 이미지 비교 (원본/도용) ────────────┤
├─ 유사도 + 워터마크 + 증거 강도 ──────┤
├─ 도용 출처 ─────────────────────────┤
├─ 도용자 사업자 정보 (공정위) ────────┤
├─ 면책 조항 ─────────────────────────┤
└─────────────────────────────────────┘
```

---

## 12. 모듈 상세 — billing

### 12.1 요금제

| 플랜 | 가격 | 보호 이미지 | 수동 탐지 | 자동 탐지 |
|------|------|-----------|----------|----------|
| STARTER | 무료 | 50장 | 30회/월 | ❌ |
| PRO | ₩29,000/월 | 1,000장 | 300회/월 | ✅ |
| BUSINESS | ₩79,000/월 | 무제한 | 무제한 | ✅ |

### 12.2 포트원 V2 플로우

프론트: 포트원 JS SDK → paymentId → `POST /billing/verify` → PortOneAdapter.verify → subscription 생성 (ACTIVE, +30일)

웹훅: `POST /billing/webhook` → 서명 검증 → 구독 갱신

### 12.3 쿼터 가드

```java
// ImageIngestionService.ingest()
billingQuotaGuard.checkImageUploadQuota(userId);
// DetectionService.detectManual()
billingQuotaGuard.checkDetectionQuota(userId);
```

---

## 13. 모듈 상세 — notification

### 13.1 이벤트 연결

```
ImageIndexedEvent → AutoScanService → SuspectFoundEvent
  → SuspectFoundListener
    ├─ ResendEmailAdapter.send()
    └─ SseEmitterAdapter.push()
```

### 13.2 Resend API

```json
POST https://api.resend.com/emails
{ "from": "Ownpic <noreply@ownpic.com>",
  "to": ["seller@example.com"],
  "subject": "[Ownpic] 도용 의심 이미지 탐지",
  "html": "..." }
```

### 13.3 SSE 엔드포인트

```
GET /api/v1/notifications/stream
Accept: text/event-stream
→ data: {"type":"SUSPECT_FOUND","detectionId":5,"matchCount":2}
```

---

## 14. Dockerfile & 배포

```dockerfile
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/libs.versions.toml gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
COPY models/ /app/models/
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC --enable-native-access=ALL-UNNAMED"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

JVM: MaxRAMPercentage=75%(2Gi→1536MB), ZGC(Virtual Threads 궁합), enable-native-access(ONNX).

---

## 15. GCP 배포 가이드

```bash
# Artifact Registry
gcloud artifacts repositories create ownpic-docker --repository-format=docker --location=asia-northeast3

# GCS 버킷
gsutil mb -l asia-northeast3 gs://ownpic-prod-models/
gsutil mb -l asia-northeast3 gs://ownpic-prod-images/

# 모델 업로드
gsutil cp sscd.onnx gs://ownpic-prod-models/sscd/
gsutil cp dinov2_vits14_cls.onnx gs://ownpic-prod-models/dinov2/
gsutil cp encoder_Q.onnx gs://ownpic-prod-models/trustmark/
gsutil cp decoder_Q.onnx gs://ownpic-prod-models/trustmark/

# 서비스 계정
gcloud iam service-accounts create ownpic-backend --display-name="Ownpic Backend"
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:ownpic-backend@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:ownpic-backend@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/storage.objectUser"

# 백엔드 배포
gcloud builds submit --config=infra/cloudbuild-backend.yaml --substitutions=_ENV=dev,_MIN_INSTANCES=0

# 프론트엔드 배포
gcloud builds submit --config=infra/cloudbuild-frontend.yaml --substitutions=_ENV=dev,_BACKEND_URL=https://ownpic-backend-dev-XXXXX.run.app
```

### 트러블슈팅

| 문제 | 해결 |
|------|------|
| Cloud Build permission denied | Cloud Build SA 권한 재실행 |
| 모델 다운로드 실패 | GCS 버킷명 확인 |
| DB 연결 실패 | `?pgbouncer=true` 확인 |
| 502 Bad Gateway | 콜드스타트 30초 → 재시도 or MIN_INSTANCES=1 |
| JWT 인증 실패 | Secret Manager JWT_SECRET 확인 |

### 비용 예상 (월, 트래픽 없을 때)

| 서비스 | 비용 |
|--------|------|
| Cloud Run Backend (min=0) | ~$0 |
| Cloud Run Frontend (min=0) | ~$0 |
| Artifact Registry | ~$0.05 |
| Secret Manager | ~$0.06 |
| GCS | ~$0.01 |
| **합계** | **~$0.12** |

---

## 16. 로컬 테스트 가이드

### 16.1 테스트 분류

| 레벨 | 필요 조건 | 테스트 수 |
|------|----------|----------|
| L1 | Java만 | 72 (단위 테스트 9파일) |
| L2 | +Supabase DB | bootRun validate |
| L3 | +ONNX 모델 | bootRun 완전 기동 |
| L4 | +네이버 API 키 | E2E 수동 |

### 16.2 L1 실행

```powershell
cd backend
.\gradlew clean test
# 예상: 72 passed, 0 failed, 1 skipped
```

### 16.3 L2 DB 리셋 (Supabase SQL Editor)

```sql
DROP TABLE IF EXISTS detection_results CASCADE;
DROP TABLE IF EXISTS detections CASCADE;
DROP TABLE IF EXISTS scan_jobs CASCADE;
DROP TABLE IF EXISTS evidences CASCADE;
DROP TABLE IF EXISTS platform_connections CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS images CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS flyway_schema_history CASCADE;
```

### 16.4 L3~L4 bootRun

```powershell
.\gradlew bootRun --args="--spring.profiles.active=dev"
```

기동 로그: Flyway → JPA validate → TrustMark → SSCD → LocalStorage → Started

### 16.5 체크리스트

- **5분**: `.\gradlew test` → 72 passed
- **15분**: + Flyway + bootRun + JPA validate
- **30분**: + 회원가입→로그인→업로드→INDEXED→수동탐지A/B→액션→자동탐지

## 17. 비용 분석

### 17.1 Cloud Run 가격 (Seoul, Request-based)

| 리소스 | Active | Idle |
|--------|--------|------|
| CPU | $0.0000312/vCPU-sec | $0.00000325/vCPU-sec |
| Memory | $0.00000325/GiB-sec | $0.00000325/GiB-sec |
| Requests | $0.52/100만 건 | — |

무료 티어: CPU 180,000 vCPU-sec, Memory 360,000 GiB-sec, 요청 200만 건

### 17.2 Backend min=0 vs min=1

| min | 월 비용 | 근거 |
|-----|---------|------|
| 0 | ~$0.02 | 무료 티어 내 |
| 1 | ~$24 | CPU idle $7.84 + Memory idle(2Gi) $15.68 |

### 17.3 단계적 전환

| 단계 | 기간 | min | 월 비용 | 누적 |
|------|------|-----|---------|------|
| 개발 | 0~5개월 | 0 | ~$3 | ~$15 |
| 베타 | 6~12개월 | 1 | ~$26 | ~$182 |
| 유료 | 12개월~ | 1 | 수익 충당 | — |

**12개월 크레딧 잔여: $300 - $197 = $103 (34%)**

### 17.4 손익분기

| 시나리오 | 월 매출 | 월 비용 | 수지 |
|---------|---------|---------|------|
| 프로 1명 | ₩29,000 | ~₩35,000 | -₩6,000 |
| **프로 2명** | **₩58,000** | **~₩35,000** | **+₩23,000** |
| +Supabase Pro 3명 | ₩87,000 | ~₩70,000 | +₩17,000 |
| 목표 10명 | ₩290,000 | ~₩93,000 | +₩197,000 |

---

## 18. 리스크 매트릭스

### 18.1 운영 리스크

| # | 리스크 | 대응 | 상태 |
|---|--------|------|------|
| R1 | Supabase 1주 비활동 → 일시정지 | Cloud Scheduler keepalive | ✅ |
| R2 | Session Mode 연결 한계 60개 | max 3×10=30 (50%) | 모니터링 |
| R3 | springdoc 3.0.2 + Boot 4.0.4 | Phase 0 검증 완료 | ✅ |
| R4 | Modulith Flyway | 제거됨, 단일 db/migration/ | ✅ |
| R5 | SSCD+TrustMark 메모리 | 2Gi 확정 (피크 911MB) | ✅ |
| R6 | TrustMark 첫 로드 시간 | 기동 시 로드, 컨테이너 캐시 | ✅ |

### 18.2 비용 리스크

| # | 리스크 | 대응 | 상태 |
|---|--------|------|------|
| R7 | GCP 크레딧 $300 만료 | 유료 전환 후에도 잔여 사용 가능 | ✅ |
| R8 | 유료 고객 0명에서 소진 | 프로 2명 손익분기 | 전략 확정 |
| R9 | Supabase Pro 전환 시점 | Week 19, $25/월 | 예정 |

### 18.3 기술 부채

| # | 항목 | 시점 |
|---|------|------|
| D1 | Terraform 전환 | 팀원 합류 시 |
| D2 | CRaC / GraalVM | 크레딧 소진 시 |
| D3 | GCS Object Versioning | Pro 플랜 시 |
| D4 | Cloud SQL 전환 | Supabase 한계 시 |
| D5 | Redis (Memorystore) | 100명+ |
| D6 | ~~lock 파일~~ | ✅ 완료 |
| D7 | E2E 테스트 Playwright | MVP 이후 |
| D8 | ~~main 브랜치 정리~~ | ✅ 완료 |

---

## 19. MVP 실행 계획

| Step | 작업 | 기간 | DoD |
|------|------|------|-----|
| 1 | GCP 배포 (현재 기능 Cloud Run) | 1~2일 | health 200, Flyway, 4 ONNX 로드, E2E |
| 2 | billing (포트원 결제) | 3~4일 | STARTER 자동, 쿼터 402, 테스트 결제, 웹훅 |
| 3 | notification (Resend+SSE) | 2~3일 | 이메일 수신, SSE 알림, DB 이력 |
| 4 | 프론트엔드 API 연동 | 5~7일 | JWT 저장, 업로드→INDEXED, 탐지 결과, 결제 |
| 5 | 프론트엔드 배포 | 1일 | Cloud Run 접속, 전체 플로우 |
| 6 | 마무리 (도메인+모니터링+베타) | 1~2일 | https://ownpic.com, 외부 사용자 접근 |
| **합계** | | **13~19일** | |

---

## 20. 잔여 과제

| # | 과제 | 상태 | 시점 |
|---|------|------|------|
| R1 | SSCD threshold 대규모 재검증 | ✅ 완료 | — |
| R2 | 실서비스 변형 검증 | ✅ 완료 | — |
| R3 | TrustMark 내구성 테스트 | ✅ 완료 | — |
| R4 | pgvector 성능 테스트 | 미착수 | MVP Phase 1 |
| R8 | bg_swap — BiRefNet/로컬 피처 | 미착수 | MVP 이후 |
| R9 | **법무 검토** (네이버 API + 도용 이미지 저장 + 공정위 사업자 정보 + 개인정보 처리방침) | **미착수** | **MVP 착수 전 필수** |
| R10 | 네이버 썸네일 SSCD 정확도 PoC | 미착수 | MVP 초반 |
| R11 | evidence_biz_info 테이블 분리 | 미착수 | Phase C |

⚠ **R9(법무 검토)는 개발과 병행 불가. 법무 확인 전까지 네이버 API·공정위 API 코드는 작성하되 prod 배포 금지.**

---

## 21. 감리 지적 반영 총괄

### 21.1 보호 흐름 (6건)

| 지적 | 조치 |
|------|------|
| 실패 보상 트랜잭션 | GCS 삭제 + 수동 재인덱싱 |
| SHA256 중복 한계 | MVP 수용, Phase C 유사도 그룹핑 |
| 동시성 제어 | 409 Conflict + 기존 이미지 |
| Phase C 대량 처리 | BatchIngestionPort 선점 |
| 워터마크 내구성 | R3 PoC 완료 |
| threshold 거버넌스 | application.yml 설정값 |

### 21.2 네이버 API (6건)

| 지적 | 조치 |
|------|------|
| 수동 트리거 명칭 | 보호 등록 시 자동 1회 스캔 |
| API 키 SPOF | 403 폴백 + 알림 |
| 키워드 품질 | 자동 추출 + 건수 미리보기 |
| API 약관 법무 | **R9 필수** |
| URL 안정성 | 탐지 시점 이미지 저장 |
| 유료화 리스크 | 어댑터 교체 구조 유지 |

### 21.3 증거 생성 (12건)

| 지적 | 조치 |
|------|------|
| 증거 무결성 | SHA256 + GCS Versioning |
| 썸네일 TrustMark | R3 PoC 완료 |
| 동기 500ms 낙관 | 비동기 + polling 30초 |
| 도용 이미지 저장 법적 | R9 통합 |
| evidence_bundle 확장 | Phase C |
| strength 세분화 | sscd_similarity 시각 구분 |
| 부분 매칭 자동 채택 | MULTIPLE_FOUND + 셀러 선택 |
| 개인정보보호법 | R9 통합 |
| 상태 불일치 | 6 유효 조합 + 엔티티 가드 |
| biz 테이블 분리 | R11 Phase C |
| 알림 메커니즘 | polling → SSE → Resend |
| selectBizCandidate 파싱 | 서비스 레이어 파싱, 엔티티에 DTO 전달 |

---

## 22. 베타 출시 체크리스트

| # | 항목 | Phase |
|---|------|-------|
| 1 | `./gradlew test` 전체 통과 | 지속 |
| 2 | prod 시크릿 전체 설정 | F |
| 3 | Cloud Run 2개 서비스 배포 | F |
| 4 | Backend min=1 전환 | F |
| 5 | Supabase Pro 전환 ($25/월) | F |
| 6 | Cloud Scheduler keepalive | F |
| 7 | Cloud Monitoring 알림 | F |
| 8 | 도메인 + SSL | F |
| 9 | 포트원 테스트 결제 → 환불 | C |
| 10 | 네이버/카카오 OAuth prod 키 | A |
| 11 | 개인정보 처리방침 + 이용약관 | F |
| 12 | pnpm-lock.yaml 커밋 | ✅ |
