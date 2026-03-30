# Ownpic 프론트엔드 설계서

> **문서 버전**: v3.0 | **작성일**: 2026.03.28
> **통합 문서**: phase3-frontend-scaffolding v1.0, phase-c-frontend-uiux-design v2.0, r3-trustmark-ui-guide v1.0, full-mvp-execution-plan 프론트엔드 섹션 통합

---

## 1. 기술 스택

| 항목 | 버전/기술 | 근거 |
|------|----------|------|
| Nuxt | 4.4.x | createUseFetch, vue-router v5 |
| Node.js | 22 LTS | Nuxt 4 권장 |
| 패키지 매니저 | pnpm | 속도 + 디스크 효율 |
| UI 프레임워크 | **shadcn-vue** (Reka UI 기반) | 컴포넌트 로컬 소유, 완전 수정 가능 |
| TypeScript | 엄격 모드 | Nuxt 4 기본 |
| API 타입 생성 | openapi-typescript | createUseFetch 조합 |

### 1.1 shadcn-vue 선택 근거

| 기준 | @nuxt/ui v4 | shadcn-vue |
|------|-------------|------------|
| 컴포넌트 소유권 | npm 의존, 커스텀 제한 | `components/ui/` 로컬 소유, 완전 수정 |
| 컴포넌트 수 | ~40개 (모듈 번들) | 60개+ (필요한 것만) |
| 번들 크기 | 미사용 포함 | 트리쉐이킹 |
| 생태계 | Nuxt 전용 | Vue 전체 + React shadcn/ui 레퍼런스 |
| 디자인 자유도 | theme 체계 제한 | CSS 변수 직접 제어 |

### 1.2 핵심 패키지

`shadcn-nuxt`, `reka-ui`, `@tailwindcss/vite`, `tw-animate-css`, `tailwind-merge`, `clsx`, `class-variance-authority`, `lucide-vue-next`, `@vueuse/core`, `vue-sonner`

### 1.3 MVP 미도입 (100명+ 시 추가)

- 캐러셀 → CSS scroll-snap (랜딩 3장)
- Drawer → Sheet(side="bottom") 통합
- Data Table → 기본 Table + 수동 정렬

---

## 2. 디렉토리 구조

```
frontend/app/
├── assets/css/tailwind.css
├── components/
│   ├── ui/                     # shadcn-vue 60개 (로컬 소유)
│   ├── dashboard/
│   │   ├── AppSidebar.vue
│   │   ├── BottomTabBar.vue
│   │   ├── KpiCard.vue
│   │   ├── ActivityFeed.vue
│   │   ├── UpsellBanner.vue
│   │   ├── OnboardingEmpty.vue
│   │   ├── ImageGrid.vue
│   │   ├── ImageCard.vue
│   │   ├── ImageListView.vue
│   │   ├── FloatingActionBar.vue
│   │   ├── UploadSheet.vue
│   │   ├── DeleteAlertDialog.vue
│   │   ├── DetectionCard.vue
│   │   ├── DetectionFilter.vue
│   │   └── QuotaProgress.vue
│   ├── landing/ (HeroSection, FeatureSection, CtaSection)
│   ├── AppHeader.vue
│   └── AppFooter.vue
├── composables/ (api, useAuth, useApiError, useDashboard, useImages,
│                 useImageUpload, useImageFilter, useDetections,
│                 useEvidence, useBilling, useBlog)
├── constants/ (plans.ts, colors.ts)
├── layouts/ (default.vue, dashboard.vue)
├── lib/utils.ts
├── middleware/auth.global.ts
├── pages/ (index, login, pricing, blog/, dashboard/, privacy, terms)
├── plugins/ssr-width.ts
├── server/ (api/auth, api/blog, api/proxy, utils/)
├── shared/types/api.d.ts
├── types/api.ts
├── utils/ (extractPlatform, fetchError, formatTimeAgo)
├── error.vue
└── app.vue
```

---

## 3. 설정 파일

### 3.1 nuxt.config.ts

```typescript
import tailwindcss from '@tailwindcss/vite'
export default defineNuxtConfig({
  modules: ['shadcn-nuxt', '@nuxt/fonts', '@nuxtjs/mdc'],
  shadcn: { prefix: '', componentDir: './app/components/ui' },
  css: ['~/assets/css/tailwind.css'],
  vite: { plugins: [tailwindcss()] },
})
```

### 3.2 tailwind.css

shadcn 표준 CSS 변수 체계. `:root`에 HSL 값, `@theme inline`에서 Tailwind 매핑. 브랜드 gradient, 페이지 트랜지션, safe area 유틸리티 포함.

### 3.3 lib/utils.ts — `cn()` (clsx + tailwind-merge)

### 3.4 plugins/ssr-width.ts — `provideSSRWidth(1024)`

---

## 4. 디자인 시스템

### 4.1 컬러: Primary Indigo

| CSS 변수 | HSL (라이트) | 용도 |
|----------|-------------|------|
| `--primary` | `234 89% 64%` | indigo-500 — CTA, 브랜드 |
| `--destructive` | `0 84.2% 60.2%` | 삭제, 에러, 도용 |
| `--muted` | `240 4.8% 95.9%` | 비활성 배경 |
| `--ring` | `234 89% 64%` | 포커스 링 |
| `--sidebar-primary` | `234 89% 64%` | 사이드바 활성 |

서비스 상태 컬러: `constants/colors.ts`에 `STATUS_COLORS` 매핑 (UPLOADING=sky, INDEXED=emerald, FAILED=red, CONCLUSIVE=red, CIRCUMSTANTIAL=amber)

소셜 브랜드: `--color-naver-500/#03C75A`, `--color-kakao-500/#FEE500`

### 4.2 타이포그래피

| 용도 | 서체 | 크기 | 비고 |
|------|------|------|------|
| 본문 (한글+영문) | Pretendard | text-sm (14px) | Inter 기반 한글 완벽 지원 |
| 대시보드 숫자/코드 | JetBrains Mono | text-2xl~3xl | KPI, 유사도 점수 |
| UI 라벨 | Pretendard | text-xs (12px) | 뱃지, 상태 |
| 영문 강조 (선택) | Geist | — | CTA 버튼 등 영문 전용. 한글 미지원 |

CSS: `--font-sans: 'Pretendard Variable', 'Pretendard', system-ui, sans-serif;`

### 4.3 Radius: `--radius: 0.5rem`

### 4.4 다크모드

`.dark` CSS 변수 선언만. 토글 UI 미구현 (MVP). 변수만 선언해두면 나중에 토글 하나로 완성.

---

## 5. shadcn-vue 컴포넌트 활용 맵 (60개)

### 5.1 레이아웃 & 네비게이션

| # | 컴포넌트 | 사용처 |
|---|---------|--------|
| 1 | **Sidebar** | 대시보드. SidebarProvider+Sidebar(collapsible="icon")+SidebarInset+SidebarRail |
| 2 | **Navigation Menu** | 공개 페이지 헤더 |
| 3 | **Breadcrumb** | 탐지 상세 |
| 4 | **Tabs** | 이미지 필터, 탐지 모드, 알림, 설정, 프라이싱 |
| 5 | **Pagination** | 이미지·탐지·알림 목록 (50건+) |
| 6 | **Scroll Area** | 사이드바, 알림 피드 |
| 7 | **Resizable** | 탐지 상세 이미지 비교 |
| 8 | **Separator** | 사이드바 그룹, 카드, 헤더 |

### 5.2 데이터 표시

| # | 컴포넌트 | 사용처 |
|---|---------|--------|
| 9 | **Card** | KPI, 이미지, 탐지, 설정, 프라이싱, 로그인 |
| 10 | **Badge** | INDEXED/PROTECTED/FAILED/미조치/프로/판매중 |
| 11 | **Avatar** | 사이드바 유저, 활동 피드, 알림 |
| 12 | **Table** | 탐지 테이블, 알림 목록 |
| 13 | **Data Table** | 100명+ 도입. MVP: 기본 Table |
| 14 | **Chart** | 100명+ KPI 추세 |
| 15 | **Progress** | 사이드바 quota, 업로드 진행률 |
| 16 | **Skeleton** | 모든 페이지 로딩 |
| 17 | **Aspect Ratio** | 이미지 카드(1:1), 비교(4:3) |
| 18 | **Typography** | h1~h4, p, lead, muted, code |

### 5.3 입력 & 폼

| # | 컴포넌트 | 사용처 |
|---|---------|--------|
| 19 | **Button** | 모든 CTA (6 variants) |
| 20 | **Button Group** | 벌크 바 |
| 21 | **Input** | 검색, 설정 |
| 22 | **Input Group** | 검색 바 (아이콘+Input) |
| 23 | **Textarea** | 증거 메모 |
| 24 | **Select** | 모바일 필터, 정렬 |
| 25 | **Checkbox** | 이미지 선택, 필터 |
| 26 | **Switch** | 알림 on/off |
| 27 | **Radio Group** | 플랜 선택 |
| 28 | **Slider** | 유사도 임계값 0.3~1.0 |
| 29 | **Toggle** | "숨겨진 이미지 표시" |
| 30 | **Toggle Group** | Grid/List 뷰 전환 |
| 31 | **Label** | 모든 폼 필드 |
| 32 | **Field** | FieldLabel+FieldDescription+입력 |
| 33 | **Form** | 설정 폼 (vee-validate) |
| 34 | **Combobox** | 플랫폼 선택, 자동완성 |
| 35 | **Tags Input** | 100명+ 이미지 태깅 |

### 5.4 오버레이 & 모달

| # | 컴포넌트 | 사용처 |
|---|---------|--------|
| 36 | **Dialog** | 이미지 확대+메타, 설정 변경 |
| 37 | **Alert Dialog** | 이미지 삭제, 계정 삭제 (destructive) |
| 38 | **Sheet** | 업로드(right), 모바일 필터(bottom) |
| 39 | **Drawer** | 100명+ (vaul-vue). MVP: Sheet 통합 |
| 40 | **Popover** | 정렬, 날짜 범위 |
| 41 | **Hover Card** | 탐지 카드 출처 hover |
| 42 | **Tooltip** | **필수**: 모든 아이콘 Button, collapsed 사이드바 |
| 43 | **Context Menu** | 이미지 우클릭 (데스크톱) |

### 5.5 피드백 & 상태

| # | 컴포넌트 | 사용처 |
|---|---------|--------|
| 44 | **Alert** | 업셀 배너, 에러 |
| 45 | **Sonner** | 토스트 전체 |
| 46 | **Spinner** | 버튼 인라인, 카드 오버레이 |
| 47 | **Empty** | 온보딩, 0건 |
| 48 | **Stepper** | 증거 생성 3단계 |

### 5.6 검색 & 기타

| # | 컴포넌트 | 사용처 |
|---|---------|--------|
| 49 | **Command** | 100명+ ⌘K 팔레트 |
| 50 | **Calendar** | Date Picker 내부 |
| 51 | **Date Picker** | 탐지 날짜 필터 |
| 52 | **Accordion** | 프라이싱 FAQ, 설정 고급 |
| 53 | **Collapsible** | 사이드바 메뉴, 탐지 카드 확장 |
| 54 | **Dropdown Menu** | 사이드바 유저, 이미지 ⋯ |

### 5.7 설치 커맨드

```bash
npx shadcn-vue add accordion alert alert-dialog aspect-ratio avatar badge \
  breadcrumb button button-group calendar card checkbox \
  collapsible combobox command context-menu dialog dropdown-menu \
  empty field form hover-card input input-group item kbd label \
  navigation-menu pagination popover progress radio-group resizable \
  scroll-area select separator sheet sidebar skeleton slider sonner \
  spinner stepper switch table tabs textarea toggle toggle-group \
  tooltip typography
```

---

## 6. 레이아웃 설계

### 6.1 대시보드 (`layouts/dashboard.vue`)

```
SidebarProvider (default-open, cookie)
├── AppSidebar (collapsible="icon")
│   ├── SidebarHeader → 로고
│   ├── SidebarContent (ScrollArea)
│   │   ├── SidebarGroup "메뉴"
│   │   │   └── 대시보드 / 이미지 / 탐지(+Badge) / 알림(+Badge)
│   │   └── SidebarGroup "관리"
│   │       └── 설정
│   ├── QuotaProgress (Progress + Tooltip)
│   └── SidebarFooter → DropdownMenu (Avatar+이름+Badge플랜 → 계정/구독/로그아웃)
└── SidebarInset
    ├── header (sticky): SidebarTrigger + Breadcrumb + 제목 + Input검색 + Bell
    └── main (p-4) → <slot />
```

**모바일**: Sidebar → Sheet. BottomTabBar 4탭: 대시보드 / 이미지 / 탐지 / 더보기(Sheet bottom).

### 6.2 공개 페이지 (`layouts/default.vue`)

NavigationMenu(horizontal) + slot + Footer

---

## 7. 페이지별 UI 설계

### 7.1 대시보드 메인

1. **업셀 Alert** (쿼터 80%+: amber, 100%: destructive)
2. **KPI Card** ×3 (font-mono 숫자 + muted 서브)
3. **활동 피드 Card** (Avatar + Badge"미조치" + Typography시간)

### 7.2 이미지 관리

- **상단**: Checkbox전체선택 + Tabs필터 + ToggleGroup(Grid/List) + Popover정렬 + Button업로드
- **그리드 뷰**: ContextMenu + Card + AspectRatio(1:1) + Checkbox + Badge + hover:scale-[1.02] + 클릭→Dialog
- **리스트 뷰**: Table + 수동 정렬 (100명+: Data Table)
- **벌크 바**: sticky bottom. ButtonGroup(다운로드/삭제)
- **업로드 Sheet**: side="right", 드래그 영역 + 큐(Spinner+Progress)
- **삭제 AlertDialog**: destructive 확인 → Sonner toast
- **모바일**: Tabs→Select, 롱프레스→Sheet(bottom)

### 7.3 탐지 결과

- **상단**: Tabs(모드 A/B/C) + DatePicker + Popover(Slider유사도+Checkbox강도)
- **탐지 카드**: Card + AspectRatio(emerald/red border) + Badge + Collapsible상세
- **하단**: Pagination

### 7.4 탐지 상세

- Breadcrumb + Stepper(①생성→②다운로드→③다음단계)
- **Resizable**: 원본↔도용 좌우. **모바일: Tabs전환**
- 메타 Card + 메모 Textarea + CTA Button

### 7.5 알림 센터

Tabs(전체/도용/시스템) + ScrollArea + Empty(0건) + Switch설정 + Pagination

### 7.6 설정

Tabs(프로필/구독/알림). 프로필: Form+Avatar. 구독: Card+Progress. 알림: Switch×4.

### 7.7 온보딩

Empty(🛡 + Title + Button업로드 + Button outline 사전확인)

### 7.8 랜딩

Typography(h1) + Button(CTA) + CSS scroll-snap 기능소개 Card×3 + CTA Card(gradient)

### 7.9 프라이싱

ToggleGroup(월/연+Badge"20%할인") + Grid(Card스타터+Card프로 border-primary) + Accordion FAQ

### 7.10 로그인

Card(Button네이버+Button카카오+Separator("또는")+Typography약관)

---

## 8. BFF 프록시 + 인증

### 8.1 allowlist 헤더 프록시

클라이언트 헤더를 전부 포워딩하지 않고 안전한 헤더만 명시적 전달.

### 8.2 인증 흐름 (httpOnly 쿠키)

```
[클라이언트] → POST /api/auth/login → [Nitro BFF]
  → POST /api/v1/auth/login → [Spring Backend]
  ← JWT 응답
  ← Set-Cookie: ownpic-token (httpOnly, Secure, SameSite=Lax)
  ← { user: {...} } (토큰 미노출)

[클라이언트] → GET /api/proxy/images → [Nitro BFF]
  → Cookie에서 JWT 추출 → Authorization: Bearer 헤더 변환
  → GET /api/v1/images → [Spring Backend]
```

### 8.3 Server 라우트

```
server/
├── api/auth/
│   ├── login.post.ts        # JWT → httpOnly 쿠키
│   ├── logout.post.ts       # 쿠키 삭제
│   ├── refresh.post.ts      # 토큰 갱신
│   └── callback/
│       ├── naver.get.ts
│       └── kakao.get.ts
├── api/proxy/[...path].ts   # Spring API 프록시
├── middleware/log.ts
└── utils/backend.ts          # 백엔드 $fetch 헬퍼
```

### 8.4 에러 전파

BFF 프록시에서 Spring의 RFC 7807 ProblemDetail을 클라이언트에 투과. 백엔드 연결 실패 시 502 폴백.
## 9. 하이브리드 렌더링

라우트별 렌더링 전략을 분리하여 SEO와 인증 요구사항을 동시에 충족한다.

| 라우트 | 전략 | 근거 |
|---|---|---|
| `/`, `/pricing` | **prerender** | SEO 필수, 정적 콘텐츠. 빌드 시 HTML 생성 |
| `/login` | **SSR** | SEO 불필요하나 초기 로드 속도 확보 |
| `/dashboard/**` | **CSR** (`ssr: false`) | 인증 필요, SEO 불필요. 클라이언트에서만 렌더링 |

`nuxt.config.ts`의 `routeRules`에서 설정:

```typescript
// nuxt.config.ts
export default defineNuxtConfig({
  routeRules: {
    '/':          { prerender: true },
    '/pricing':   { prerender: true },
    '/login':     { ssr: true },
    '/dashboard/**': { ssr: false },
  },
})
```

**검증 방법**:
- `/` 소스 보기에서 완전한 HTML 확인 (prerender)
- `/dashboard` 소스 보기에서 빈 HTML 확인 (CSR)

---

## 10. API 연동 계획

### 10.1 createUseFetch 기반 API 클라이언트

```typescript
// composables/api.ts
export const useApiFetch = createUseFetch((currentOptions) => {
  return {
    ...currentOptions,
    baseURL: '/api/proxy',
  }
})

// 사용 예시 (타입은 shared/types/api.d.ts에서 import)
const { data } = await useApiFetch<ImageListResponse>('/images')
```

### 10.2 openapi-typescript 타입 생성

```bash
cd frontend
npx openapi-typescript http://localhost:8080/v3/api-docs -o shared/types/api.d.ts
```

Spring 백엔드의 OpenAPI 스펙에서 자동으로 TypeScript 타입을 생성한다. `createUseFetch`와 조합하여 엔드투엔드 타입 안전성을 확보한다.

### 10.3 Composables 목록

| Composable | 역할 |
|---|---|
| `api.ts` | `createUseFetch` 기반 API 클라이언트 (`useApiFetch`) |
| `useAuth.ts` | login/signup/logout, JWT 토큰 관리 |
| `useApiError.ts` | RFC 7807 ProblemDetail 파싱, 에러 토스트 |
| `useDashboard.ts` | KPI 데이터, 활동 피드 조회 |
| `useImages.ts` | 이미지 CRUD, 목록 조회/필터 |
| `useImageUpload.ts` | multipart 업로드, 진행률 추적 |
| `useImageFilter.ts` | 이미지 상태/정렬/검색 필터 상태 관리 |
| `useDetections.ts` | 탐지 실행/조회, 모드 A/B/C |
| `useEvidence.ts` | 증거 PDF 생성/다운로드 |
| `useBilling.ts` | 구독/결제, 포트원 연동 |
| `useBlog.ts` | 블로그 게시글 조회 |

### 10.4 페이지별 연동 우선순위

| 우선순위 | 페이지 | 연동 API | 비고 |
|---|---|---|---|
| **P0** | 로그인/회원가입 | `POST /auth/signup`, `POST /auth/login` | 인증 기반. 최우선 |
| **P0** | 대시보드 메인 | `GET /images`, `GET /detections` | 핵심 KPI 표시 |
| **P0** | 이미지 업로드 | `POST /images/upload` (multipart) | 서비스 핵심 플로우 |
| **P0** | 탐지 실행 | `POST /detections/detect` (multipart) | 서비스 핵심 플로우 |
| **P1** | 탐지 상세 | `GET /detections/{id}` | 결과 확인 |
| **P1** | 증거 생성 | `POST /evidences` | 증거 PDF 다운로드 |
| **P1** | 요금제/결제 | `GET /billing/plans`, 포트원 JS SDK | 수익화 |
| **P2** | 설정 (알림) | `GET/PATCH /notifications/settings` | 부가 기능 |
| **P2** | 실시간 알림 | `SSE /notifications/stream` | 부가 기능 |

---

## 11. 증거 강도 UI -- R3 반영

R3 TrustMark 내구성 PoC 실측 결과(네이버 스마트스토어 600장, 17종 변환)를 UI에 반영한다.

### 11.1 EVIDENCE_GRADE_CONFIG

```typescript
// constants/colors.ts
export const EVIDENCE_GRADE_CONFIG = {
  CONCLUSIVE: {
    label: '결정적 증거',
    variant: 'destructive',       // red -- 심각성 강조
    icon: 'ShieldCheck',
    tooltip: 'TrustMark 워터마크가 확인되었습니다. 법적 효력이 있는 증거입니다.',
  },
  CIRCUMSTANTIAL: {
    label: '정황 증거',
    variant: 'warning',           // amber
    icon: 'ShieldAlert',
    tooltip: '이미지 유사도로만 확인된 증거입니다. 워터마크 검증이 불가했습니다.',
  },
} as const
```

- **CONCLUSIVE** (결정적 증거): `destructive`(red) variant + `ShieldCheck` 아이콘. TrustMark CRC 통과 + SSCD >= 0.30일 때 부여.
- **CIRCUMSTANTIAL** (정황 증거): `warning`(amber) variant + `ShieldAlert` 아이콘. CRC 실패이거나 아래 조건 중 하나라도 해당 시 고정.

### 11.2 CIRCUMSTANTIAL 원인 4종 -- 코드 및 안내 문구

단순히 "정황 증거"만 표시하지 않는다. **원인을 사용자에게 명확히 안내**해야 한다.

| 원인 코드 | 조건 | UI 안내 문구 |
|---|---|---|
| `LOW_RESOLUTION` | 도용 이미지 단변 < 200px (160px 피드 이미지 자동 해당, CRC 통과율 16% 이하) | "도용된 이미지 해상도가 낮아(160px 이하) 워터마크를 정확히 읽을 수 없습니다." |
| `ROTATION_DETECTED` | EXIF 또는 SSCD 회전 탐지 결과 90/180도 회전 (CRC 0%) | "도용된 이미지가 회전 변형되어 워터마크 검증이 불가합니다." |
| `HIGH_COMPRESSION` | JPEG quality 추정값 < 50 (CRC 2~17%) | "도용된 이미지가 심하게 압축되어(JPEG q<50) 워터마크 신호가 손상되었습니다." |
| `DECODE_FAILED` | 위 외 기타 디코딩 실패 | "워터마크를 읽을 수 없습니다. 이미지가 크게 변형되었을 수 있습니다." |

**구현 위치**: 탐지 결과 카드 -> 증거 강도 뱃지 옆 `Tooltip` 또는 클릭 시 `Sheet`

### 11.3 증거 생성 버튼 비활성화

도용 이미지 단변이 **200px 미만**이면 결정적 증거 생성 버튼을 비활성화한다.

```
비활성 시 안내 문구:
"이미지 해상도가 너무 낮아 결정적 증거를 생성할 수 없습니다.
 정황 증거는 생성 가능합니다. [정황 증거로 생성하기]"
```

200px 기준 근거: 160px(네이버 피드)에서 CRC 16%, 300px(썸네일)에서 78%. 약 200px 이하부터 신뢰도가 급감한다.

### 11.4 탐지 결과 카드 시각화

`components/detection/DetectionResultCard.vue`에 아래 구조를 포함한다:

```
+-------------------------------------+
| [원본 이미지]    [도용 이미지]        |
|                                     |
| 유사도: 0.87  ||||||||||||..  87%   |
|                                     |
| 증거 강도:  (red) 결정적 증거   [i]  |  <-- CONCLUSIVE
| 또는       (amber) 정황 증거   [i]  |  <-- CIRCUMSTANTIAL
|             +-- "이미지 해상도가..." |  <-- Tooltip
|                                     |
| [증거 생성]  [이미지 비교]           |
+-------------------------------------+
```

- 원본 이미지: emerald border
- 도용 이미지: red border
- 유사도: Progress 바 + font-mono 숫자
- 증거 강도: `EVIDENCE_GRADE_CONFIG`에 따른 Badge + Tooltip(원인 안내)
- CTA: 증거 생성 버튼 + 이미지 비교 버튼

### 11.5 PDF 증거서 워터마크 섹션

PDF 출력 시 TrustMark 결과를 명시한다.

**CONCLUSIVE인 경우:**
```
  워터마크 검증 결과: 확인됨
  - 소유자 ID: {userId} / 이미지 ID: {imageId}
  - 등록 시각: {timestamp}
  - 검증 방법: TrustMark ONNX (variant Q, MIT License)
```

**CIRCUMSTANTIAL인 경우:**
```
  워터마크 검증 결과: 검증 불가 ({원인})
  - 이미지 유사도(SSCD): {similarity}
  - 비고: 이미지 변형으로 인해 워터마크 체크섬 검증에 실패했습니다.
          유사도 증거만으로 도용 가능성이 높습니다.
```

### 11.6 셀러 교육

#### 이미지 등록 가이드 (ImageUpload 컴포넌트 내, 3항목)

```
더 강력한 증거를 위한 팁
1. 가능하면 고해상도(800px 이상) 이미지를 등록하세요
2. 모든 촬영 각도의 이미지를 각각 등록하면 탐지율이 높아집니다
3. 회전된 이미지가 도용되면 워터마크 검증이 어려울 수 있습니다
```

#### 대시보드 안내 (CIRCUMSTANTIAL 결과 시)

```
정황 증거로만 확인된 이유
도용된 이미지가 [원인]으로 인해 워터마크를 읽을 수 없었습니다.
그러나 이미지 유사도 {score}%는 도용 가능성이 매우 높음을 나타냅니다.

-> 법적 대응이 필요하다면 [법무 가이드 보기]
```

---

## 12. 업셀 & 기능 게이팅

### 12.1 업셀 전략

과도한 업셀은 "내 저작물이 위험한데 돈을 더 내라"는 인상을 줄 수 있다. **한 세션에서 업셀 메시지는 최대 2개**로 제한한다.

| # | 위치 | 조건 | 방식 | 비고 |
|---|---|---|---|---|
| 1 | 사이드바 Progress | 항상 | Tooltip "10/50장 사용" | 소프트. 클릭 시 pricing 페이지 이동 |
| 2 | 대시보드 배너 | **쿼터 80%+ 시에만** | Alert 1회 | 유일한 인배너 업셀 |
| 3 | 탐지 결과 | -- | Badge+Tooltip | "프로: 매일 탐지" Tooltip만. 배너 제거 |
| 4 | 알림 센터 | -- | 제거 | 설정 > 구독 탭에 집중 |
| 5 | 설정 > 구독 | 항상 | Card (현재 플랜 vs 프로 비교) | 업셀 정보의 메인 허브 |

### 12.2 기능 게이팅

숨기지 않고 보여주되 잠근다. Tooltip으로 "프로 플랜에서 사용 가능" 안내.

| 기능 | STARTER | PRO | UI 표현 |
|---|---|---|---|
| 카카오 알림톡 | 비활성 | 활성 | `Switch(disabled)` + `Badge("프로")` + `Tooltip` |
| 자동 탐지 빈도 | 주 1회 | 매일 | KPI Card 서브텍스트 + `Tooltip` |
| 이미지 한도 | **50장** | **1,000장** | `Progress` + `Alert`(80%+ 시에만) |
| 증거 PDF | 기본 | 완전 | `Badge("프로")` 인라인 |

> **주의**: STARTER 이미지 한도는 **50장**이다 (10장 아님). PRO는 1,000장.

---

## 13. 반응형 설계

### 13.1 뷰포트별 레이아웃 전략

| 뷰포트 | 레이아웃 | 네비게이션 | 이미지 그리드 | 필터 | 오버레이 |
|---|---|---|---|---|---|
| **>=1024px** (데스크톱) | Sidebar + SidebarInset | NavigationMenu | 5~6열 / DataTable | Tabs | Dialog, Sheet |
| **768~1023px** (태블릿) | collapsed Sidebar | 아이콘 + Tooltip | 3~4열 | Tabs | Dialog, Sheet |
| **<768px** (모바일) | Sheet + BottomTabBar | 4탭(대시보드/이미지/탐지/더보기) | 2~3열 | Select | Dialog, Sheet(bottom) |

### 13.2 모바일 전용 규칙

- **44px 터치 타겟**: 모든 인터랙티브 요소의 최소 터치 영역
- **Checkbox 항상 표시**: 데스크톱에서는 hover 시 표시할 수 있으나, 모바일에서는 항상 표시
- **롱프레스 -> Sheet**: 이미지 카드 롱프레스 시 `Sheet(side="bottom")`으로 액션 메뉴 표시 (ContextMenu 대체)
- **Resizable -> Tabs 전환**: 탐지 상세의 원본/도용 이미지 비교에서 `Resizable` 대신 `Tabs`("원본"/"도용" 탭)로 대체. 리사이즈보다 직관적
- **BottomTabBar "더보기"**: Sheet(bottom)로 알림/설정/구독/로그아웃 메뉴 표시

---

## 14. Cloud Run 프론트엔드 배포

### 14.1 Cloud Run 설정

| 항목 | 설정 |
|---|---|
| CPU | 1 vCPU |
| Memory | 256Mi |
| Min instances | 0 |
| Max instances | 2 |
| Concurrency | 80 |

### 14.2 cloudbuild-frontend.yaml

API URL을 환경변수로 주입:

```yaml
# cloudbuild-frontend.yaml 핵심 설정
- '--set-env-vars=NUXT_PUBLIC_API_BASE=https://ownpic-backend-dev-XXXXX.run.app'
```

### 14.3 CORS 업데이트

프론트엔드 Cloud Run 도메인을 백엔드 허용 출처에 추가:

```yaml
# application-prod.yml
ownpic:
  cors:
    allowed-origins: https://ownpic-frontend-XXXXX.run.app,https://ownpic.com
```

---

## 15. 구현 순서

| Phase | 단계 | 주요 작업 | 관련 컴포넌트 |
|---|---|---|---|
| **Phase 0** | 인프라 | @nuxt/ui 제거, 전체 의존성 설치, `nuxt.config.ts`, `tailwind.css`, `lib/utils.ts`, `plugins/ssr-width.ts`, `npx shadcn-vue init` + 전체 컴포넌트 일괄 설치 | shadcn-vue 60개 |
| **Phase 1** | 레이아웃 | `app.vue`, `layouts/dashboard.vue`(Sidebar+SidebarInset), `AppSidebar.vue`(Avatar+DropdownMenu+QuotaProgress+Collapsible+Tooltip), `BottomTabBar.vue`(4탭), `layouts/default.vue`(NavigationMenu+AppHeader+AppFooter) | Sidebar, NavigationMenu, Avatar, DropdownMenu |
| **Phase 2** | 공통 | `types/`, `constants/`(plans, colors), `composables/`(api, useAuth, useApiError), `middleware/auth.global.ts`, `server/` | -- |
| **Phase 3** | 대시보드 | `dashboard/index.vue` | Card, Typography, Avatar, Badge, Item, Alert, Empty |
| **Phase 4** | 이미지 | `images.vue` | Tabs, ToggleGroup, Popover, Slider, ContextMenu, AspectRatio, Table, Sheet, AlertDialog, Pagination, ButtonGroup, Sonner, Dialog |
| **Phase 5** | 탐지 | `detections/` | Tabs, DatePicker, Collapsible, Pagination, Resizable, Stepper, Textarea, Breadcrumb |
| **Phase 6** | 알림 & 설정 | `alerts.vue`, `settings.vue` | ScrollArea, Item, Switch, Tooltip, Tabs, Accordion, Form, AlertDialog |
| **Phase 7** | 공개 페이지 | `index.vue`(랜딩), `login.vue`, `pricing.vue`, blog, privacy, terms, `error.vue` | Typography, Card, Separator, ToggleGroup, Accordion, RadioGroup |

---

## 16. 검증 체크리스트

| # | 항목 | 확인 방법 |
|---|---|---|
| 1 | `pnpm dev` 정상 실행 | `localhost:3000` 접근 확인 |
| 2 | shadcn-vue 컴포넌트 렌더링 | Button, Card 등 기본 컴포넌트 표시 확인 |
| 3 | BFF 프록시 동작 | `/api/proxy/health` -> 백엔드 응답 전달 확인 |
| 4 | 하이브리드 렌더링 (prerender) | `/` 소스 보기에서 완전한 HTML 확인 |
| 5 | 하이브리드 렌더링 (CSR) | `/dashboard` 소스 보기에서 빈 HTML 확인 |

---

## 17. 구현 주의사항

### 1. Tooltip 필수

아이콘만 있는 **모든 Button**에 Tooltip을 추가한다. collapsed Sidebar의 전체 메뉴 항목에도 필수.

### 2. ContextMenu <-> Sheet 분기

데스크톱 우클릭 -> `ContextMenu`. 모바일 롱프레스 -> `Sheet(side="bottom")`. `useMediaQuery`로 분기한다.

### 3. AlertDialog vs Dialog

- **destructive 확인** (이미지 삭제, 계정 삭제) -> `AlertDialog`
- **일반 정보/이미지 확대** -> `Dialog`

### 4. 이중 스크롤 방지

`SidebarInset`이 메인 스크롤 컨테이너이다. 내부에 `ScrollArea`를 중첩할 때 이중 스크롤이 발생하지 않도록 주의한다.

### 5. 로딩 상태 구분

- `Skeleton`: 페이지 초기 로딩
- `Spinner`: 버튼 인라인 로딩
- `Empty`: 데이터 없음 상태

### 6. 합성 패턴

shadcn-vue의 합성(composition) 패턴을 준수한다. 예: `Card` -> `Card` + `CardHeader` + `CardTitle` + `CardContent` 등.

### 7. 아이콘: lucide-vue-next

`lucide-vue-next`에서 직접 import한다.

```typescript
import { Shield, Search, Bell } from 'lucide-vue-next'
```

### 8. 코딩 규칙

- `catch (e: unknown)` -- unknown 타입 명시
- 매직넘버 금지 -- 상수로 추출
- composable 경유 -- 컴포넌트에서 직접 API 호출 금지
- `readonly()` 래핑 -- 외부에 노출하는 반응형 상태

### 9. 이미지 카드 인터랙션

`HoverCard` 사용 안 함. 세 가지 인터랙션만 허용:
- **hover** -> `scale-[1.02]` 효과만
- **클릭** -> `Dialog`로 확대 + 메타 표시
- **우클릭** -> `ContextMenu` (모바일: 롱프레스 -> Sheet)

### 10. 탐지 이미지 비교 모바일

모바일에서 `Resizable` 대신 `Tabs` 전환("원본"/"도용" 탭)으로 대체한다. 작은 화면에서 리사이즈보다 탭 전환이 직관적이다.
