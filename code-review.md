# Ownpic 설계서 vs 코드베이스 리뷰

> **작성일**: 2026-03-30 | **리뷰어**: Claude Code

## Context

Ownpic은 이미지 도용 탐지 SaaS로, 백엔드(Spring Boot 4 + Java 25), 프론트엔드(Nuxt 4 + shadcn-vue), ML(SSCD + DINOv2 + TrustMark ONNX)로 구성된다. 설계서 3개(backend-design.md, frontend-design.md, ml-model-design.md)를 실제 코드베이스와 대조하여 **설계-구현 간 Gap**, **구조적 문제**, **개선 제안**을 도출한다.

> **배경**: 이전 구현이 품질 문제로 리셋되어 auth 모듈부터 새로 구현 중인 상태. 설계서의 "✅ 완성" 표기는 이전 구현 기준이며, 현재 코드에는 auth만 존재한다.

---

## 1. 백엔드 리뷰: 설계 vs 구현 Gap

### 1.1 구현 완료 (설계서 기준)

| 설계서 모듈 | 설계서 상태 | 실제 코드 | 판정 |
|---|---|---|---|
| auth (signup/login/JWT/refresh/logout) | ✅ 완성 | ✅ 구현됨 (AuthController, AuthService, JwtProvider, JwtAuthFilter) | **일치** |
| health endpoint | 미언급 | ✅ HealthController 존재 | 추가 구현 |

### 1.2 설계서에는 있지만 코드에 없는 것 (Critical Gap)

| 설계서 항목 | 설계서 상태 | 실제 코드 | 심각도 |
|---|---|---|---|
| **image 모듈** (업로드/워터마킹/SSCD+DINOv2 듀얼 임베딩) | ✅ 완성 (25 tests) | **없음** | **Critical** |
| **detection 모듈** (듀얼 탐지 A/B + 자동스캔) | ✅ 완성 (20 tests) | **없음** | **Critical** |
| **evidence 모듈** (TrustMark 디코더 + 공정위 + PDF) | ✅ 코드 완성 | **없음** | **Critical** |
| **billing 모듈** (스텁 4파일) | 🔲 스텁만 | **없음** | Medium |
| **notification 모듈** (포트만 2파일) | 🔲 포트만 | **없음** | Medium |
| **shared 모듈** (SecurityConfig, JacksonConfig, OpenApiConfig) | ✅ 완성 (9 tests) | 부분 (SecurityConfig만) | **High** |
| ONNX 모델 통합 (SSCD/DINOv2/TrustMark) | ✅ 통합 완료 | **없음** | **Critical** |
| Flyway 마이그레이션 10개 | ✅ | 2개만 (users, refresh_tokens) | **High** |
| 테스트 72개 (9파일) | ✅ | **0개** | **Critical** |

### 1.3 구조적 불일치

| 항목 | 설계서 | 실제 코드 | 문제 |
|---|---|---|---|
| **패키지 구조** | 도메인별 (auth/, image/, detection/, evidence/) + 포트/어댑터 | 레이어별 (controller/, service/, entity/, dto/) | **아키텍처 불일치** -- 설계서는 헥사고날 아키텍처를 지향하나 구현은 전통적 레이어드 |
| **User ID 전략** | 내부 BIGSERIAL + 외부 public_id UUID | UUID만 사용 (BIGSERIAL 없음) | FK 조인 성능 저하 가능 |
| **User 엔티티** | 별도 UserRole enum + AuthProvider | role이 엔티티 내부 enum (FREE/PRO/ADMIN) | 설계서의 UserRole과 불일치 |
| **API 경로** | `/api/v1/auth/*` (ApiPaths.V1 접두사) | `/api/auth/*` (버전 없음) | API 버저닝 미적용 |
| **Refresh Token** | SHA-256 해시로 DB 저장 | 평문 UUID로 DB 저장 | **보안 취약** -- 토큰 탈취 시 DB 유출로 직접 악용 가능 |
| **JWT 라이브러리** | JJWT 0.12.6 | JJWT 0.13.0 | 마이너 버전 차이 (영향 낮음) |
| **에러 핸들링** | RFC 7807 ProblemDetail (Spring 네이티브) | 커스텀 ErrorResponse | 표준 미준수 |
| **libs.versions.toml** | 설계서에 상세 명시 | **파일 없음** -- build.gradle.kts에 직접 명시 | 의존성 관리 비표준 |

### 1.4 백엔드 코드 품질 이슈

1. **Refresh Token 평문 저장**: 설계서는 SHA-256 해시 후 DB 저장을 명시하나, 실제로는 UUID를 그대로 저장. DB 침해 시 모든 리프레시 토큰이 노출됨
2. **테스트 부재**: 설계서에 72개 테스트(9파일)가 있다고 하나 실제 테스트 파일 0개
3. **Dockerfile 부재**: 설계서에 상세한 Dockerfile이 있으나 코드에 없음
4. **OpenAPI/Swagger 미설정**: 설계서에 springdoc 3.0.2가 명시되어 있으나 의존성 및 설정 없음
5. **Virtual Threads 미설정**: 설계서에 `spring.threads.virtual.enabled: true` 명시, 실제 application.yml에 없음
6. **@EnableAsync/@EnableScheduling 미설정**: Application 클래스에 없음

---

## 2. 프론트엔드 리뷰: 설계 vs 구현 Gap

### 2.1 구현 완료

| 항목 | 판정 |
|---|---|
| Nuxt 4 + shadcn-vue 기본 설정 | ✅ |
| BFF 프록시 인증 (httpOnly 쿠키) | ✅ |
| 레이아웃 (default, dashboard) | ✅ |
| 랜딩 페이지 (히어로, 기능, 프라이싱) | ✅ |
| 로그인/회원가입 | ✅ |
| 대시보드 기본 셸 (사이드바 + 모바일 하단 탭) | ✅ |
| 다크모드 토글 | ✅ (설계서에는 MVP 미구현이라 했으나 구현됨) |

### 2.2 설계 vs 구현 불일치

| 항목 | 설계서 | 실제 코드 | 심각도 |
|---|---|---|---|
| **UI 프레임워크** | shadcn-vue 60개 컴포넌트 | 16개 그룹만 설치 | Medium (점진적 설치 가능) |
| **컬러 시스템** | Primary Indigo (`234 89% 64%`) | Primary Cyan (`#0891b2`) | **High** -- 브랜드 아이덴티티 불일치 |
| **폰트** | Pretendard (한글) + JetBrains Mono (숫자) | Wanted Sans Variable | **Medium** -- 설계서 폰트와 다름 |
| **요금제** | STARTER ₩0 / PRO ₩29,000 / BUSINESS ₩79,000 | Free ₩0 / Starter ₩29,900 / Pro ₩69,900 | **High** -- 가격/명칭 불일치 |
| **이미지 쿼터** | STARTER 50장 / PRO 1,000장 | Free 10장 / Starter 200장 / Pro 1,000장 | **High** -- 쿼터 불일치 |
| **하이브리드 렌더링** | `/` prerender, `/dashboard/**` CSR | ✅ 일치 | 일치 |
| **composables** | 11개 (api, useAuth, useApiError, useDashboard...) | 3개 (useAuth, useTheme, useFadeIn) | Medium (점진적 추가 가능) |
| **대시보드 메뉴** | 대시보드/이미지/탐지/알림/설정 | 대시보드/이미지보호/탐지/스토어/알림 | **Medium** |
| **openapi-typescript 타입 생성** | 설계서에 명시 | 미구현 | Medium |

### 2.3 프론트엔드 코드 품질 이슈

1. **me.get.ts JWT 디코딩**: 서버 사이드에서 JWT를 검증 없이 base64 디코딩만 수행. 서명 검증을 하지 않아 변조된 토큰도 유효하게 처리될 수 있음
2. **useAuth.ts 에러 처리**: login/signup에서 에러 시 throw만 하고 구체적 에러 타입이 없음. 설계서의 `useApiError` (RFC 7807 파싱) 미구현
3. **하드코딩된 목 데이터**: 대시보드 알림 페이지 등에 목 데이터가 하드코딩됨 (개발 중이므로 예상 범위)

---

## 3. ML 모델 설계서 리뷰

### 3.1 설계서 자체 평가 (코드 무관)

ML 설계서는 3개 설계서 중 가장 완성도가 높음:

**강점:**
- SSCD threshold 0.30 확정 근거가 R1(300원본+540neg) + R2(네이버 600장)로 이중 검증됨
- DINOv2 듀얼 파이프라인이 SSCD 약점(rotate)을 명확히 보완
- TrustMark R3 내구성 검증이 네이버 플랫폼 경유별로 세분화됨
- bg_swap 한계를 솔직히 인정하고 MVP 제외를 명시

**개선 포인트:**
- FPR 0.2% (R2 기준)가 "허용"이라 판단했으나, 대규모 서비스에서 0.2%는 절대 수치가 크므로 스케일 시 재검증 필요
- Neg max 0.5161 (R2)과 threshold 0.30 사이의 gap이 충분하나, threshold를 0.30으로 낮게 유지하는 것에 대한 근거가 "Recall 100% 우선"인데, 실서비스에서 FP 1건이 사용자 신뢰를 크게 떨어뜨릴 수 있음
- PoC 가이드(11.1~11.3)가 설계서에 포함된 것은 문서 목적이 혼합됨 (설계서 vs 실험 가이드 분리 권장)

### 3.2 코드 구현 Gap

설계서에 ONNX 모델 4개(SSCD+DINOv2+TrustMark Encoder/Decoder)가 백엔드에 통합되었다고 하나, 실제 코드에는 관련 클래스(SscdEmbeddingAdapter, TrustMarkWatermarkAdapter, PgvectorSearchAdapter 등)가 전혀 없음.

---

## 4. 설계서 간 불일치 (Cross-Document)

| 항목 | backend-design.md | frontend-design.md | 문제 |
|---|---|---|---|
| 요금제 명칭 | STARTER/PRO/BUSINESS | Free/Starter/Pro (랜딩 페이지) | **불일치** |
| 요금 | ₩0/₩29,000/₩79,000 | ₩0/₩29,900/₩69,900 | **불일치** |
| 이미지 쿼터 | 50/1,000/무제한 | 10/200/1,000 | **불일치** |
| User.role enum | FREE/PRO/ADMIN | free/starter/pro | **불일치** |
| API 경로 | `/api/v1/auth/*` | BFF → `/api/v1/*` 프록시 | 백엔드 실제 코드는 `/api/auth/*` |

---

## 5. 종합 평가

### 5.1 설계서 품질: B+

- **장점**: 매우 상세하고 체계적. 기술 스택 선정 근거, 비용 분석, 리스크 매트릭스, 감리 지적 반영까지 포함. ML 검증이 특히 탄탄함
- **단점**: 3개 설계서 간 데이터 불일치(요금제, 쿼터). 설계서가 "완성"이라 표기한 모듈의 코드가 실제로 없음 -- 설계서가 "설계"가 아니라 "목표 상태"를 기술한 것으로 보임

### 5.2 코드 완성도: D+ (전체 설계 대비)

- auth 모듈 + 프론트 기본 셸만 구현된 상태
- 설계서에서 ✅로 표시한 image/detection/evidence 모듈이 코드에 없음
- 테스트 0개

### 5.3 우선 해결 권장 사항 (Top 5)

| # | 항목 | 사유 |
|---|---|---|
| 1 | **설계서 간 요금제/쿼터 통일** | 3개 설계서와 코드가 모두 다른 값을 사용 중. 확정 후 단일 소스로 관리 |
| 2 | **Refresh Token SHA-256 해싱** | 현재 평문 저장은 보안 취약점. 설계서대로 해시 저장 적용 필요 |
| 3 | **패키지 구조 확정** | 레이어드 vs 헥사고날 중 하나로 확정. 현재 코드(레이어드)와 설계서(헥사고날)가 충돌 |
| 4 | **API 버저닝 `/api/v1/` 적용** | 설계서와 프론트 BFF 모두 v1 접두사를 전제. 백엔드 코드 미적용 |
| 5 | **컬러 시스템 확정 (Indigo vs Cyan)** | 설계서는 Indigo, 코드는 Cyan. 브랜드 아이덴티티 확정 필요 |

---

## 6. 아키텍처 권고: 헥사고날(도메인별) 채택

### 6.1 권고안

현재 코드(레이어드)를 설계서의 헥사고날(도메인별 port/adapter)로 전환할 것을 권고한다.

### 6.2 근거

| 기준 | 레이어드 (현재) | 헥사고날 (설계서) |
|---|---|---|
| 도메인 격리 | controller/에 모든 모듈 혼합 | auth/, image/, detection/ 독립 |
| 외부 의존 관리 | Service에서 직접 호출 | Port 인터페이스 + Adapter 구현 |
| 테스트 용이성 | 외부 의존 목킹 어려움 | Port 목킹으로 단위 테스트 용이 |
| 확장성 | 모듈 추가 시 패키지 혼잡 | 모듈별 독립 패키지 |
| 리팩토링 비용 | -- | auth 파일 ~19개로 **낮음** |

### 6.3 전환 시 auth 패키지 구조

```
com.ownpic/
├── OwnpicApplication.java
├── shared/
│   ├── config/ (SecurityConfig, CorsProperties)
│   ├── exception/ (GlobalExceptionHandler)
│   └── dto/ (ApiPaths, ErrorResponse)
└── auth/
    ├── AuthService.java
    ├── controller/AuthController.java
    ├── domain/ (User, UserRepository, RefreshToken, RefreshTokenRepository)
    ├── jwt/ (JwtProvider, JwtAuthFilter, JwtProperties)
    ├── dto/ (SignupRequest, LoginRequest, RefreshRequest, AuthResponse)
    └── exception/ (AuthenticationFailedException, DuplicateEmailException)
```

### 6.4 외부 의존이 많은 모듈에서 효과가 큼

- **image**: GCS(StoragePort) + TrustMark(WatermarkPort) + SSCD(EmbeddingPort)
- **detection**: SSCD(EmbeddingPort) + pgvector(SearchPort) + 네이버 API(ShoppingSearchPort)
- **evidence**: TrustMark(DecoderPort) + 공정위 API(BusinessInfoPort) + PDF(ReportPort)

이 모듈들은 3~4개의 외부 의존이 있어 Port/Adapter 패턴의 이점이 극대화된다.

---

## 7. 다음 단계 제안

### 7.1 설계서 수정 (선행)
1. 3개 설계서 간 요금제/쿼터/역할 값 통일
2. "✅ 완성" 표기를 현재 상태에 맞게 수정
3. 컬러 시스템 확정 (Indigo vs Cyan)

### 7.2 백엔드 구현 순서 (설계서 MVP 기준)
1. auth 리팩토링 (헥사고날 전환 + Refresh Token 해싱 + API 버저닝)
2. shared 모듈 (OpenApiConfig, JacksonConfig, OnnxModelResolver)
3. image 모듈 (업로드 → 워터마킹 → SSCD 임베딩)
4. detection 모듈 (수동 탐지 → 자동 탐지)
5. evidence 모듈 (TrustMark 디코딩 → 공정위 → PDF)
6. billing 모듈 (포트원 결제)
7. notification 모듈 (Resend + SSE)

### 7.3 프론트엔드 구현 순서
1. 컬러/폰트 확정 후 tailwind.css 수정
2. API 연동 composables (P0: 로그인 → 업로드 → 탐지)
3. 대시보드 기능 페이지 구현
4. 증거/결제/알림 페이지
