<script setup lang="ts">
import {
  ArrowRight,
  Search,
  FileText,
  Clock,
  Upload,
  Shield,
  FileCheck,
  Link2,
  ShieldCheck,
  Scale,
  Check,
} from 'lucide-vue-next'

useFadeIn()

const stats = [
  { value: '59,565건', label: '2024년 저작권법 위반 단속 건수\n(경찰청)' },
  { value: '222%↑', label: '전년 대비 저작권 위반 사건 증가율\n(지재위)' },
  { value: '104만건', label: '2024년 온라인 불법복제물 시정권고\n(문체부)' },
]

const problems = [
  {
    icon: Search,
    title: '발견이 어렵다',
    description: '수천 개 셀러 사이에서 내 이미지가 도용되었는지 일일이 확인하는 건 불가능합니다.',
  },
  {
    icon: FileText,
    title: '증명이 어렵다',
    description: "플랫폼에 신고해도 '원본 증거'를 요구합니다. 스크린샷만으로는 부족합니다.",
  },
  {
    icon: Clock,
    title: '대응이 느리다',
    description: '신고 절차는 복잡하고, 플랫폼마다 다르고, 결과는 몇 주가 걸립니다.',
  },
]

const solutions = [
  {
    num: '01',
    title: '보이지 않는 각인',
    description: '상품 이미지에 육안으로 보이지 않는 디지털 워터마크를 삽입합니다. 이미지 품질은 그대로 유지됩니다.',
    tag: '비가시 워터마크',
  },
  {
    num: '02',
    title: '자동 보호',
    description: '쿠팡 스토어와 연동하여 상품 이미지를 자동으로 보호합니다.',
    tag: '플랫폼 연동',
  },
  {
    num: '03',
    title: '증거 패키지',
    description: '도용 발견 시 원본 증명 PDF, 유사도 분석, 신고서 자동 작성까지 한번에.',
    tag: '원클릭 대응',
  },
]

const plans = [
  {
    name: '무료',
    price: '₩0',
    period: '/월',
    description: '개인 셀러를 위한 기본 보호',
    features: ['월 10장 워터마크', '도용 탐지 월 3회', '스토어 1개 연동'],
    cta: '무료로 시작',
    popular: false,
  },
  {
    name: '스타터',
    price: '₩29,900',
    period: '/월',
    description: '성장하는 셀러를 위한 자동화',
    features: ['월 200장 워터마크', '도용 탐지 월 30회', '스토어 2개 연동', '쇼핑 검색 탐지', '자동 이미지 교체'],
    cta: '스타터 시작',
    popular: true,
  },
  {
    name: '프로',
    price: '₩69,900',
    period: '/월',
    description: '멀티 스토어 셀러를 위한 풀 패키지',
    features: ['월 1,000장 워터마크', '도용 탐지 월 200회', '스토어 5개 연동', '쇼핑 검색 탐지', '자동 이미지 교체', '우선 지원'],
    cta: '프로 시작',
    popular: false,
  },
]
</script>

<template>
  <div>
    <!-- ===================== Hero ===================== -->
    <section class="py-24 max-md:py-20 pt-20 text-center">
      <div class="mx-auto max-w-6xl px-6">
        <!-- Badge -->
        <div class="fade-in">
          <span class="mb-5 inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            이커머스 셀러를 위한 이미지 보호 서비스
          </span>
        </div>

        <h1 class="fade-in fade-in-delay-1 mb-5 text-5xl font-extrabold leading-tight max-md:text-4xl">
          내 상품 이미지,<br>
          <span class="bg-gradient-to-r from-primary to-blue-500 bg-clip-text text-transparent">
            도용당하고 있습니다
          </span>
        </h1>

        <p class="fade-in fade-in-delay-2 mx-auto mb-9 max-w-xl text-lg leading-relaxed text-muted-foreground max-md:text-base">
          온픽은 보이지 않는 디지털 각인으로 당신의 상품 이미지를 보호하고, 도용을 증명합니다.
        </p>

        <!-- CTA Buttons — 프로덕션: mb-16, justify-center, gap-3 -->
        <div class="fade-in fade-in-delay-3 mb-16 flex justify-center gap-3 max-sm:flex-col max-sm:items-center">
          <NuxtLink
            to="/signup"
            class="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-6 py-3 font-semibold text-primary-foreground transition-all duration-200 hover:bg-primary/90 active:scale-[0.98]"
          >
            내 스토어 무료 검사
            <ArrowRight class="h-4 w-4" />
          </NuxtLink>
          <NuxtLink
            to="/signup"
            class="inline-flex items-center justify-center rounded-lg border border-border bg-transparent px-6 py-3 font-semibold text-foreground transition-all duration-200 hover:bg-secondary hover:text-secondary-foreground"
          >
            무료로 체험하기
          </NuxtLink>
        </div>

        <!-- Stats — 프로덕션: max-w-xl, max-md:grid-cols-1 max-md:gap-5 -->
        <div class="fade-in mx-auto grid max-w-xl grid-cols-3 gap-8 max-md:grid-cols-1 max-md:gap-5">
          <div v-for="stat in stats" :key="stat.label" class="space-y-1.5">
            <p class="text-3xl font-extrabold text-primary">{{ stat.value }}</p>
            <p class="whitespace-pre-line text-xs text-muted-foreground">{{ stat.label }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== Demo (도용 탐지 시연) ===================== -->
    <section class="py-24 max-md:py-20 bg-gradient-to-b from-background-secondary to-background">
      <div class="mx-auto max-w-6xl px-6">
        <div class="fade-in text-center">
          <span class="mb-5 inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            <Shield class="h-4 w-4" />
            도용 탐지 시연
          </span>
        </div>
        <h2 class="fade-in fade-in-delay-1 mb-4 text-center text-3xl font-extrabold max-md:text-2xl">
          당신의 이미지, 지금 어디에서 쓰이고 있을까?
        </h2>
        <p class="mx-auto mb-10 max-w-xl text-center text-base leading-relaxed text-muted-foreground">
          상품 이미지를 업로드하면 AI가 워터마크를 삽입하고, 인터넷에서 도용된 곳을 즉시 찾아냅니다.
        </p>

        <!-- Upload Card — 프로덕션: rounded-3xl, p-10, max-md:p-6 -->
        <div class="fade-in mx-auto max-w-3xl rounded-3xl border border-border bg-card p-10 max-md:p-6">
          <div class="rounded-xl border-2 border-dashed border-border/60 px-8 py-16 text-center">
            <Upload class="mx-auto h-10 w-10 text-muted-foreground/50" />
            <p class="mt-4 text-sm text-muted-foreground">상품 이미지를 드래그하거나 클릭하세요</p>
            <p class="mt-1 text-xs text-muted-foreground/70">JPEG / PNG · 10MB 이하</p>
            <NuxtLink to="/signup" class="mt-4 inline-block text-sm font-medium text-primary hover:underline">
              가입 없이 바로 체험
            </NuxtLink>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== Problem ===================== -->
    <section class="py-24 max-md:py-20 bg-background-secondary">
      <div class="mx-auto max-w-6xl px-6">
        <div class="fade-in">
          <span class="mb-5 inline-flex items-center gap-1.5 rounded-full border border-destructive/30 bg-destructive/10 px-3.5 py-1.5 text-xs font-semibold text-destructive">
            <Scale class="h-4 w-4" />
            문제
          </span>
        </div>
        <h2 class="fade-in fade-in-delay-1 mb-12 text-3xl font-extrabold max-md:text-2xl">
          매일 도용당하는 당신의 상품 이미지
        </h2>

        <!-- 프로덕션: grid-cols-3, hover:-translate-y-1 hover:border-border/50 -->
        <div class="grid grid-cols-3 gap-6 max-md:grid-cols-1">
          <div
            v-for="(problem, idx) in problems"
            :key="problem.title"
            class="rounded-2xl border border-border bg-card p-8 transition-all hover:-translate-y-1 hover:border-border/50 fade-in"
            :class="`fade-in-delay-${idx + 1}`"
          >
            <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-destructive/10">
              <component :is="problem.icon" class="h-6 w-6 text-destructive/70" />
            </div>
            <h3 class="mt-5 text-lg font-bold">{{ problem.title }}</h3>
            <p class="mt-2 text-sm leading-relaxed text-muted-foreground">
              {{ problem.description }}
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== Solution ===================== -->
    <section class="py-24 max-md:py-20">
      <div class="mx-auto max-w-6xl px-6">
        <div class="fade-in">
          <span class="mb-5 inline-flex items-center gap-1 rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            ✦ 솔루션
          </span>
        </div>
        <h2 class="fade-in fade-in-delay-1 mb-12 text-3xl font-extrabold max-md:text-2xl">
          온픽이 해결합니다
        </h2>

        <!-- 프로덕션: grid grid-cols-[80px_1fr_auto], px-8 py-9, hover:translate-x-1 hover:border-primary/25 -->
        <div class="fade-in space-y-4">
          <div
            v-for="sol in solutions"
            :key="sol.num"
            class="grid grid-cols-[80px_1fr_auto] items-center gap-8 rounded-2xl border border-border bg-card px-8 py-9 transition-all hover:translate-x-1 hover:border-primary/25 max-md:grid-cols-[60px_1fr] max-md:gap-5"
          >
            <span class="bg-gradient-to-b from-primary to-transparent bg-clip-text text-4xl font-extrabold text-transparent">{{ sol.num }}</span>
            <div>
              <h3 class="text-lg font-bold">{{ sol.title }}</h3>
              <p class="mt-1 text-sm leading-relaxed text-muted-foreground">{{ sol.description }}</p>
            </div>
            <span class="rounded-full bg-primary/10 px-4 py-1.5 text-xs font-medium text-primary max-md:col-span-2 max-md:justify-self-start">
              {{ sol.tag }}
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== Integration (원클릭 연동) ===================== -->
    <section class="py-24 max-md:py-20 bg-background-secondary">
      <div class="mx-auto max-w-6xl px-6 text-center">
        <div class="fade-in">
          <span class="mb-5 inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            <Link2 class="h-4 w-4" />
            원클릭 연동
          </span>
        </div>
        <h2 class="fade-in fade-in-delay-1 mb-4 text-3xl font-extrabold max-md:text-2xl">
          네이버 스마트스토어 연동 수천 장도 한번에
        </h2>
        <p class="mx-auto mb-10 max-w-xl text-base text-muted-foreground">
          스토어를 연동하면 등록된 모든 상품 이미지에 자동으로 보이지 않는 워터마크를 삽입합니다.
        </p>

        <!-- Platform badges -->
        <div class="mx-auto mb-12 flex flex-wrap items-center justify-center gap-4">
          <span class="inline-flex items-center gap-2 rounded-full border bg-card px-5 py-2 text-sm font-medium">
            <span class="flex h-6 w-6 items-center justify-center rounded-md bg-[#03C75A] text-xs font-bold text-white">N</span>
            네이버 스마트스토어
          </span>
          <span class="inline-flex items-center gap-2 rounded-full border bg-card px-5 py-2 text-sm text-muted-foreground">
            <span class="flex h-6 w-6 items-center justify-center rounded-full bg-red-500 text-xs font-bold text-white">●</span>
            쿠팡 (예정)
          </span>
          <span class="inline-flex items-center gap-2 rounded-full border bg-card px-5 py-2 text-sm text-muted-foreground">
            <span class="flex h-6 w-6 items-center justify-center rounded-md bg-blue-600 text-xs font-bold text-white">C</span>
            카페24 (예정)
          </span>
        </div>

        <!-- 4-step process — 프로덕션: max-w-5xl grid-cols-4 gap-3 max-md:grid-cols-2 -->
        <div class="mx-auto grid max-w-5xl grid-cols-4 gap-3 max-md:grid-cols-2">
          <div class="rounded-2xl border bg-card p-6 text-center">
            <div class="mx-auto mb-3 text-sm font-medium text-muted-foreground">● 스토어 연동</div>
            <div class="rounded-xl bg-muted/50 p-4 text-left text-xs text-muted-foreground">
              <p>API Key <span class="ml-2 text-muted-foreground/50">••••••••</span></p>
            </div>
            <div class="mt-4">
              <span class="inline-flex h-8 items-center rounded-lg bg-primary px-4 text-xs font-semibold text-primary-foreground">연동하기</span>
            </div>
            <div class="mx-auto mt-4 flex h-7 w-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">1</div>
            <p class="mt-2 text-sm font-semibold">스토어 연동</p>
            <p class="mt-1 text-xs text-muted-foreground">네이버 커머스 API를 연동하면 끝. 30초면 연동 완료.</p>
          </div>
          <div class="rounded-2xl border bg-card p-6 text-center">
            <div class="mx-auto mb-3 flex items-center justify-between text-sm font-medium text-muted-foreground">
              <span>● 상품 목록</span>
              <span class="font-bold text-primary">248</span>
            </div>
            <div class="space-y-2">
              <div class="flex items-center gap-2"><div class="h-6 w-8 rounded bg-muted"></div><div class="h-3 flex-1 rounded bg-muted"></div></div>
              <div class="flex items-center gap-2"><div class="h-6 w-8 rounded bg-muted"></div><div class="h-3 flex-1 rounded bg-muted"></div></div>
              <div class="flex items-center gap-2"><div class="h-6 w-8 rounded bg-muted"></div><div class="h-3 flex-1 rounded bg-muted"></div></div>
            </div>
            <div class="mx-auto mt-4 flex h-7 w-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">2</div>
            <p class="mt-2 text-sm font-semibold">상품 자동 스캔</p>
            <p class="mt-1 text-xs text-muted-foreground">등록된 상품과 이미지를 자동으로 불러옵니다.</p>
          </div>
          <div class="rounded-2xl border bg-card p-6 text-center">
            <div class="mx-auto mb-3 flex items-center justify-between text-sm font-medium text-muted-foreground">
              <span>● 일괄 보호</span>
            </div>
            <div class="mb-2 text-xs text-muted-foreground">127 / 248 처리중...</div>
            <div class="h-2 w-full overflow-hidden rounded-full bg-muted">
              <div class="h-full w-1/2 rounded-full bg-primary"></div>
            </div>
            <div class="mx-auto mt-6 flex h-7 w-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">3</div>
            <p class="mt-2 text-sm font-semibold">원클릭 일괄 보호</p>
            <p class="mt-1 text-xs text-muted-foreground">전체 선택 → '보호하기' 한 번이면 모든 이미지에 소유권 정보가 삽입됩니다.</p>
          </div>
          <div class="rounded-2xl border bg-card p-6 text-center">
            <ShieldCheck class="mx-auto h-8 w-8 text-primary" />
            <p class="mt-2 text-3xl font-bold text-primary">248</p>
            <p class="text-xs text-muted-foreground">장 보호 완료</p>
            <div class="mx-auto mt-4 flex h-7 w-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">4</div>
            <p class="mt-2 text-sm font-semibold">자동 교체 완료</p>
            <p class="mt-1 text-xs text-muted-foreground">워터마크가 삽입된 이미지가 스토어에 자동 반영됩니다.</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== AI 기본법 ===================== -->
    <section class="py-24 max-md:py-20">
      <div class="mx-auto max-w-6xl px-6 text-center">
        <div class="fade-in">
          <span class="mb-5 inline-flex items-center rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            2026 AI 기본법 시행
          </span>
        </div>
        <h2 class="fade-in fade-in-delay-1 mb-4 text-3xl font-extrabold max-md:text-2xl">
          AI 시대, 이미지 소유권 증명이 더 중요해졌습니다
        </h2>
        <p class="mx-auto mb-12 max-w-xl text-base text-muted-foreground">
          2026년 1월 AI 기본법 시행으로 디지털 콘텐츠의 투명성이 법적 의무가 되었습니다.
        </p>

        <!-- 프로덕션: max-w-4xl grid-cols-3 gap-5 max-md:grid-cols-1, 카드 p-7, hover:border-warning/40 -->
        <div class="fade-in mx-auto grid max-w-4xl grid-cols-3 gap-5 max-md:grid-cols-1">
          <div class="rounded-2xl border border-border bg-card p-7 text-left transition-colors hover:border-warning/40">
            <Scale class="h-8 w-8 text-foreground/80" />
            <h3 class="mt-5 text-base font-bold">AI 생성물 표시 의무화</h3>
            <p class="mt-2 text-sm text-muted-foreground">생성형 AI로 만든 이미지에는 워터마크 표시가 법적 의무입니다.</p>
          </div>
          <div class="rounded-2xl border border-border bg-card p-7 text-left transition-colors hover:border-warning/40">
            <ShieldCheck class="h-8 w-8 text-foreground/80" />
            <h3 class="mt-5 text-base font-bold">원본 소유권 증명</h3>
            <p class="mt-2 text-sm text-muted-foreground">AI가 만든 이미지와 직접 촬영한 상품 사진을 구분할 수 있어야 합니다.</p>
          </div>
          <div class="rounded-2xl border border-border bg-card p-7 text-left transition-colors hover:border-warning/40">
            <FileCheck class="h-8 w-8 text-foreground/80" />
            <h3 class="mt-5 text-base font-bold">비가시 워터마크</h3>
            <p class="mt-2 text-sm text-muted-foreground">온픽의 비가시 워터마크는 AI 기본법 대응과 도용 방지를 동시에 해결합니다.</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== Free Tool (무료 도구) ===================== -->
    <section class="py-24 max-md:py-20 bg-background-secondary">
      <div class="mx-auto max-w-6xl px-6 text-center">
        <div class="fade-in">
          <span class="mb-5 inline-flex items-center rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            무료 도구
          </span>
        </div>
        <h2 class="fade-in fade-in-delay-1 mb-4 text-3xl font-extrabold max-md:text-2xl">
          지금 바로 체험해보세요
        </h2>
        <p class="mx-auto mb-10 max-w-xl text-base text-muted-foreground">
          이미지에 보이지 않는 메시지를 삽입하고 추출해보세요.
        </p>

        <!-- Upload Card — 프로덕션: max-w-2xl, rounded-3xl, p-9 -->
        <div class="fade-in mx-auto max-w-2xl rounded-3xl border border-border bg-card p-9">
          <div class="rounded-xl border-2 border-dashed border-border/60 px-8 py-16 text-center">
            <Upload class="mx-auto h-10 w-10 text-muted-foreground/50" />
            <p class="mt-4 text-sm text-muted-foreground">이미지를 드래그하거나 클릭</p>
            <p class="mt-1 text-xs text-muted-foreground/70">JPEG / PNG · 10MB 이하</p>
          </div>
        </div>

        <div class="mx-auto mt-6 max-w-2xl rounded-2xl border bg-card p-6 text-center">
          <h3 class="text-base font-bold">실제 서비스는 더 강력합니다</h3>
          <p class="mt-2 text-sm text-muted-foreground">AI 기반 TrustMark 워터마크는 JPEG 압축, 리사이즈, 스크린샷에도 살아남습니다.</p>
        </div>
      </div>
    </section>

    <!-- ===================== Pricing ===================== -->
    <section id="pricing" class="py-24 max-md:py-20">
      <div class="mx-auto max-w-6xl px-6 text-center">
        <div class="fade-in">
          <span class="mb-5 inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            <Check class="h-4 w-4" />
            요금제
          </span>
        </div>
        <h2 class="fade-in fade-in-delay-1 mb-4 text-3xl font-extrabold max-md:text-2xl">
          합리적인 가격으로 시작하세요
        </h2>
        <p class="mx-auto mb-14 max-w-xl text-base text-muted-foreground">
          무료로 시작하고, 비즈니스가 성장하면 업그레이드하세요.
        </p>

        <!-- 프로덕션: grid-cols-3, gap-6, max-md:grid-cols-1 -->
        <div class="grid grid-cols-3 gap-6 max-md:grid-cols-1">
          <div
            v-for="(plan, idx) in plans"
            :key="plan.name"
            class="relative flex flex-col rounded-3xl border bg-card p-9 text-left transition-all fade-in"
            :class="[
              plan.popular ? 'border-primary shadow-md' : 'border-border',
              `fade-in-delay-${idx + 1}`
            ]"
          >
            <div v-if="plan.popular" class="absolute -top-3 left-1/2 -translate-x-1/2">
              <span class="rounded-full bg-primary px-4 py-1 text-xs font-semibold text-primary-foreground">인기</span>
            </div>

            <p class="text-base font-semibold">{{ plan.name }}</p>
            <p class="mt-2">
              <span class="text-4xl font-extrabold tracking-tight">{{ plan.price }}</span>
              <span class="text-sm text-muted-foreground">{{ plan.period }}</span>
            </p>
            <p class="mt-1 text-sm text-muted-foreground">{{ plan.description }}</p>

            <ul class="mt-6 space-y-3">
              <li v-for="feature in plan.features" :key="feature" class="flex items-center gap-2 text-sm text-muted-foreground">
                <Check class="h-4 w-4 shrink-0 text-primary" />
                {{ feature }}
              </li>
            </ul>

            <!-- 프로덕션: mt-auto w-full, h-10, rounded-lg -->
            <NuxtLink
              to="/signup"
              class="mt-auto w-full inline-flex items-center justify-center rounded-lg text-sm font-semibold transition-all duration-200 h-10 px-4 py-2"
              :class="plan.popular
                ? 'bg-primary text-primary-foreground hover:bg-primary/90 active:scale-[0.98]'
                : 'border border-border bg-transparent text-foreground hover:bg-secondary hover:text-secondary-foreground'"
            >
              {{ plan.cta }}
            </NuxtLink>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== Final CTA ===================== -->
    <section class="py-24 max-md:py-20 text-center">
      <div class="mx-auto max-w-6xl px-6">
        <h2 class="fade-in text-3xl font-extrabold max-md:text-2xl">
          내 상품 이미지, 지금 보호하세요
        </h2>
        <p class="mx-auto mt-4 max-w-xl text-base text-muted-foreground">
          무료로 시작하고, 내 상품 이미지를 보호하세요.
        </p>
        <div class="mt-8">
          <NuxtLink
            to="/signup"
            class="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-8 py-3 font-semibold text-primary-foreground transition-all duration-200 hover:bg-primary/90 active:scale-[0.98]"
          >
            무료로 시작하기
            <ArrowRight class="h-4 w-4" />
          </NuxtLink>
        </div>
        <p class="mt-4 text-sm text-muted-foreground">
          신용카드 불필요 · 가입 즉시 사용 가능
        </p>
      </div>
    </section>
  </div>
</template>
