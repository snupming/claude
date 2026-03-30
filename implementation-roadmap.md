# Ownpic 구현 로드맵

> **작성일**: 2026-03-30

## 현재 상태

- **백엔드**: auth만 (19파일, 레이어드 구조, 테스트 0개)
- **프론트엔드**: 랜딩/로그인/회원가입/대시보드 셸 (12페이지, 3 composables)
- **인프라**: Dockerfile 없음, GCP 미배포

---

## Phase 0: 기반 정비 (선행 필수)

설계서 불일치 해소 + 보안 수정 + 아키텍처 전환. 이후 모든 모듈의 토대.

### 0-1. 설계서 불일치 통일

3개 설계서 + 코드에서 값이 다른 항목을 하나로 확정:

| 항목 | 확정 값 (backend-design.md 기준) |
|---|---|
| 요금제 명칭 | STARTER / PRO / BUSINESS |
| 요금 | ₩0 / ₩29,000 / ₩79,000 |
| 이미지 쿼터 | 50 / 1,000 / 무제한 |
| User.role enum | FREE / PRO / ADMIN |
| Primary 컬러 | Indigo (`234 89% 64%`) |
| 폰트 | Pretendard + JetBrains Mono |

**수정 대상**: frontend-design.md, frontend 코드 (pricing, tailwind.css, useAuth)

### 0-2. 백엔드 보안 수정

| 작업 | 파일 | 내용 |
|---|---|---|
| Refresh Token SHA-256 해싱 | `AuthService.java`, `RefreshTokenRepository.java` | 저장 시 해시, 조회 시 해시 비교 |
| DB 크레덴셜 제거 | `application.yml` | 하드코딩된 Supabase 비밀번호 제거, 환경변수만 사용 |
| JWT Secret 강화 | `application.yml` | dev 기본값을 더 강력한 키로 변경 |

### 0-3. 백엔드 헥사고날 리팩토링

현재 레이어드 구조를 설계서의 도메인별 구조로 전환:

```
현재: com.ownpic.backend/          →  전환 후: com.ownpic/
├── config/                            ├── OwnpicApplication.java
├── controller/                        ├── shared/
├── dto/                               │   ├── config/ (SecurityConfig, CorsProperties)
├── entity/                            │   ├── exception/ (GlobalExceptionHandler)
├── repository/                        │   └── dto/ (ApiPaths, ErrorResponse)
├── security/                          └── auth/
└── service/                               ├── AuthService.java
                                           ├── controller/AuthController.java
                                           ├── domain/ (User, UserRepository, RefreshToken, RefreshTokenRepository)
                                           ├── jwt/ (JwtProvider, JwtAuthFilter, JwtProperties)
                                           ├── dto/ (SignupRequest, LoginRequest, RefreshRequest, AuthResponse)
                                           └── exception/
```

### 0-4. API 버저닝

- `ApiPaths.java` 추가: `public static final String V1 = "/api/v1";`
- AuthController: `/api/auth/*` → `/api/v1/auth/*`
- SecurityConfig: 허용 경로 업데이트
- 프론트 BFF: `backendFetch` base URL 업데이트

### 0-5. 설정 보강

| 작업 | 파일 |
|---|---|
| `@EnableAsync` + `@EnableScheduling` 추가 | `OwnpicApplication.java` |
| `spring.threads.virtual.enabled: true` | `application.yml` |
| `spring.mvc.problemdetails.enabled: true` | `application.yml` |
| RFC 7807 ProblemDetail 전환 | `GlobalExceptionHandler.java` |
| `libs.versions.toml` 생성 | `gradle/libs.versions.toml` |
| springdoc 의존성 추가 | `build.gradle.kts` |

### 검증
- `./gradlew bootRun` 정상 기동
- `POST /api/v1/auth/signup` → 201
- `POST /api/v1/auth/login` → 200 + 토큰
- `POST /api/v1/auth/refresh` → 200 (해시된 토큰으로 동작)
- 프론트 `pnpm dev` → 로그인/회원가입 정상 동작

---

## Phase 1: shared 모듈 + image 모듈

서비스의 핵심 흐름 "이미지 업로드 → 워터마킹 → 임베딩" 구현.

### 1-1. shared 모듈 확장

| 파일 | 역할 |
|---|---|
| `shared/config/OpenApiConfig.java` | springdoc 설정 |
| `shared/config/JacksonConfig.java` | JSON 직렬화 설정 |
| `shared/config/RestClientConfig.java` | RestClient 빈 |
| `shared/ml/OnnxModelResolver.java` | ONNX 모델 경로 해석 + 로드 |
| `shared/ml/PgvectorUtils.java` | pgvector 유틸리티 |

### 1-2. Flyway 마이그레이션 추가

설계서의 10개 마이그레이션 중 나머지 8개:
- `V3__create_images.sql` (embedding vector(512), HNSW 인덱스 포함)
- `V4__create_scan_jobs.sql`
- `V5__create_detections.sql`
- `V6__create_detection_results.sql`
- `V7__create_evidences.sql`
- `V8__create_subscriptions.sql`
- `V9__create_notifications.sql`
- `V10__create_platforms.sql`

### 1-3. image 모듈

```
image/
├── ImageService.java              # 업로드 오케스트레이션
├── ImageIngestionService.java     # 비동기 임베딩 파이프라인
├── ImageProtectedEvent.java
├── ImageIndexedEvent.java
├── controller/ImageController.java
├── domain/ (Image, ImageRepository, ImageStatus, SourceType)
├── port/ (ImageStoragePort, WatermarkPort)
├── adapter/ (LocalFileStorageAdapter, TrustMarkWatermarkAdapter)
└── exception/
```

**핵심 흐름**:
1. `POST /api/v1/images/upload` (multipart)
2. 이미지 검증 → SHA256 중복 체크
3. TrustMark 워터마킹 (ONNX, ~399ms)
4. 저장 (로컬/GCS) → DB INSERT (status=PROTECTED)
5. `ImageProtectedEvent` → 비동기 SSCD 임베딩 → status=INDEXED

**의존성 추가** (build.gradle.kts):
- `com.microsoft.onnxruntime:onnxruntime:1.23.2`
- `com.pgvector:pgvector:0.1.6`
- `com.github.librepdf:openpdf:1.3.35`

### 1-4. image 테스트

- `ImageServiceTest.java`: 업로드 흐름 단위 테스트 (Port 목킹)
- `ImageControllerTest.java`: multipart 업로드 통합 테스트

### 검증
- `POST /api/v1/images/upload` → 201 + 이미지 ID
- DB에 status=PROTECTED → 비동기 후 INDEXED
- `GET /api/v1/images` → 목록 반환
- `./gradlew test` 통과

---

## Phase 2: detection 모듈

### 2-1. detection 모듈

```
detection/
├── DetectionService.java
├── service/AutoScanService.java
├── controller/DetectionController.java
├── domain/ (Detection, DetectionResult, ScanJob, SearchMode, SellerAction, ScanJobStatus)
├── port/ (SscdEmbeddingPort, SimilarImageSearchPort, ShoppingSearchPort)
├── adapter/ (SscdEmbeddingAdapter, PgvectorSearchAdapter, NaverShoppingSearchAdapter, ImagePreprocessor)
└── listener/ (ImageProtectedListener, ImageIndexedListener)
```

**3가지 모드**:
- A: 수동 (내 이미지 대상)
- B: 사전 확인 (전체, 블러)
- C: 자동 (네이버 쇼핑 API) — ImageIndexedEvent 트리거

**듀얼 파이프라인**: `SSCD ≥ 0.30 OR DINOv2 ≥ 0.70 → 도용 의심`

### 2-2. DINOv2 통합

- `dinov2_vits14_cls.onnx` (85MB) 추가
- `DinoEmbeddingAdapter` 구현

### 2-3. detection 테스트

### 검증
- `POST /api/v1/detections/detect` (multipart) → 결과 반환
- `PATCH /api/v1/detections/results/{id}/action` → 액션 업데이트
- pgvector 검색 결과 정확도 확인

---

## Phase 3: evidence 모듈

```
evidence/
├── EvidenceService.java
├── domain/ (Evidence, EvidenceRepository, EvidenceStatus, BizLookupStatus, EvidenceStrength)
├── controller/EvidenceController.java
├── port/ (BusinessInfoLookupPort, WatermarkDecoderPort)
├── adapter/ (FtcBusinessLookupAdapter, TrustMarkDecoderAdapter)
├── report/PdfReportGenerator.java
└── exception/
```

**비동기 파이프라인**:
1. `POST /api/v1/evidences` → 202 (GENERATING)
2. TrustMark 디코딩 → 증거 강도 (CONCLUSIVE/CIRCUMSTANTIAL)
3. 공정위 API 사업자 조회 (best-effort)
4. PDF 리포트 (OpenPDF)
5. 상태 → COMPLETED

### 검증
- 증거 생성 → PDF 다운로드 확인
- CONCLUSIVE/CIRCUMSTANTIAL 판정 로직 검증

---

## Phase 4: billing + notification 모듈

### 4-1. billing
- `BillingService`, `PortOneAdapter`, 쿼터 가드
- 포트원 V2 결제 연동
- 구독 상태 관리 (ACTIVE, EXPIRED, CANCELLED)

### 4-2. notification
- `ResendEmailAdapter` (이메일)
- `SseEmitterAdapter` (실시간)
- `SuspectFoundListener` → 자동 알림

### 검증
- 포트원 테스트 결제 → 쿼터 증가
- SSE 스트림 연결 → 알림 수신

---

## Phase 5: 프론트엔드 API 연동

### 5-1. 기반 수정
- tailwind.css: Indigo 컬러 + Pretendard 폰트 적용
- 요금제/쿼터 값 통일

### 5-2. composables 추가

| Composable | 연동 API |
|---|---|
| `api.ts` | `createUseFetch` 기반 클라이언트 |
| `useApiError.ts` | RFC 7807 ProblemDetail 파싱 |
| `useDashboard.ts` | KPI 데이터 |
| `useImages.ts` | 이미지 CRUD |
| `useImageUpload.ts` | multipart 업로드 + 진행률 |
| `useDetections.ts` | 탐지 실행/조회 |
| `useEvidence.ts` | 증거 생성/다운로드 |
| `useBilling.ts` | 구독/결제 |

### 5-3. 대시보드 기능 페이지
- `dashboard/protect.vue` → 이미지 업로드 + 상태 표시
- `dashboard/detect.vue` → 탐지 실행 + 결과 카드
- 증거 생성 페이지 (Stepper 3단계)

### 5-4. shadcn-vue 컴포넌트 추가 설치
현재 16개 → 필요한 컴포넌트 점진적 추가

### 5-5. BFF 프록시 확장
- `server/api/proxy/[...path].ts` → Spring API 범용 프록시

### 검증
- 전체 플로우: 회원가입 → 로그인 → 업로드 → INDEXED → 탐지 → 증거 → PDF 다운로드

---

## Phase 6: 인프라 + 배포

### 6-1. Dockerfile
- Backend: multi-stage build (eclipse-temurin:25), ONNX 모델 레이어
- Frontend: Node 22 + Nuxt build

### 6-2. GCP 배포
- Artifact Registry + Cloud Build 트리거
- Cloud Run 백엔드 (2Gi, 1vCPU) + 프론트엔드 (256Mi)
- Secret Manager + Cloud Scheduler keepalive

### 검증
- Cloud Run 배포 → health 200
- 전체 E2E 플로우

---

## 실행 순서 요약

```
Phase 0: 기반 정비 ──────── 보안 + 아키텍처 + 설계서 통일
  │
Phase 1: shared + image ─── 업로드 → 워터마킹 → 임베딩
  │
Phase 2: detection ──────── 탐지 3모드 + 듀얼 파이프라인
  │
Phase 3: evidence ───────── 증거 생성 + PDF
  │
Phase 4: billing + notif ── 수익화 + 알림
  │
Phase 5: 프론트 연동 ────── 전체 UI 기능 완성
  │
Phase 6: 인프라 + 배포 ──── GCP Cloud Run
```

> Phase 5는 Phase 1~4와 병행 가능: 각 백엔드 모듈 완성 즉시 해당 프론트 페이지 연동
