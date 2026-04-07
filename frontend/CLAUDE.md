# Frontend 작업 규칙 (Claude 자동 준수)

이 파일은 `frontend/` 하위 작업 시 Claude 가 반드시 따라야 할 규칙이다.
PR 단위로 위반 사항이 없는지 self-check 후 작업을 종료한다.

---

## 🚫 절대 금지

- **`any` 사용 금지** — 모르면 `unknown` + 타입 가드.
- **빈 `catch` 블록 금지** — 최소 `console.error` 또는 toast/log. 오류를 삼키지 말 것.
- **`as T` 우회 단언 금지** — 특히 `undefined as T`, `{} as Foo` 같은 패턴.
- **모듈 레벨 가변 상태 금지** — composable 내부 `let pollTimer` 같은 패턴 금지. 컴포넌트/스코프 안으로 옮겨라.
- **동적 값을 URL 에 직접 보간 금지** — `safeJoin()` (`server/utils/backend.ts`) 또는 `encodeURIComponent` 사용.
- **새 파일/유틸 무분별 생성 금지** — 아래 "재사용 우선" 규칙 참조.

## ✅ 필수

- 클라이언트→서버 라우트 호출은 `$fetch`, SSR 데이터 패칭은 `useFetch`.
- `addEventListener`, `IntersectionObserver`, `setInterval`, `setTimeout`,
  `MutationObserver` 등은 반드시 `onScopeDispose` 또는 `onUnmounted` 에서 해제.
- `server/api/*` 의 모든 body/query/params 는 zod 스키마로 검증
  (`server/utils/validate.ts` 의 `readValidatedBody` / `getValidatedQuery` / `getValidatedRouterParams`).
- JWT 등 외부 문자열 파싱은 try/catch + 필드 존재 검증.
- 비동기 작업 중복 실행 가드 (`isLoading` ref, mutex, 또는 `AbortController`).
- 폴링은 이전 요청 완료 후에만 다음 요청을 보낼 것 (in-flight 플래그 사용).

## ♻️ 재사용 우선 (파일 증식 방지)

새 파일/함수/composable 을 만들기 **전에** 반드시 다음을 수행한다.

1. `Grep` / `Glob` 으로 동일 또는 유사 구현이 있는지 검색.
   - 함수명 키워드, 시그니처, 도메인 단어 모두 검색.
2. 다음 디렉터리를 우선 확인:
   - `app/composables/` — Vue composable
   - `app/lib/` — 클라이언트 유틸
   - `server/utils/` — 서버 유틸
   - `app/components/ui/` — shadcn 컴포넌트
3. **Rule of Three**: 동일 패턴이 3회 이상 반복될 때만 헬퍼로 추출.
   1~2회 사용은 인라인으로 둔다.
4. 가능하면 **기존 파일에 함수를 추가**한다. 새 파일은 마지막 수단.
5. 리팩토링 시 **구 코드는 같은 PR 에서 삭제**한다 (옛 파일 잔재 금지).
6. 신규 파일을 만들 경우 PR 설명에 *왜 기존 파일에 추가할 수 없었는지* 한 줄 사유를 남긴다.

## 📦 작업 종료 전 체크

```bash
pnpm typecheck   # vue-tsc strict 통과
pnpm lint        # ESLint 0 error
pnpm test        # vitest 통과
pnpm knip        # 미사용 파일/export 0건
```

위 4가지가 모두 통과해야 작업 완료로 간주한다.
실패 시 우회(--no-verify, eslint-disable 남발 등) 금지 — 근본 원인 수정.

## 🧭 디렉터리 가이드

| 위치 | 용도 |
|---|---|
| `app/composables/` | Vue composable (use*) |
| `app/lib/` | 프레임워크 비의존 클라이언트 유틸 |
| `app/components/ui/` | shadcn-vue 디자인 시스템 (수동 추가 금지, CLI 사용) |
| `server/api/` | Nitro 서버 라우트 (BFF) |
| `server/utils/` | 서버 전용 유틸 (백엔드 fetch, 검증 등) |
| `server/plugins/` | Nitro 플러그인 |
