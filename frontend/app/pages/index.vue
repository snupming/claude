<script setup lang="ts">
import {
  ArrowRight,
  ShieldCheck,
  ScanSearch,
  FileCheck,
  Link2,
  ScanLine,
  ShieldPlus,
  RefreshCw,
  AlertTriangle,
  Scale,
  Fingerprint,
  Check,
} from 'lucide-vue-next'

const stats = [
  { value: '59,565건', label: '월간 이미지 도용 신고' },
  { value: '222%↑', label: '전년 대비 증가율' },
  { value: '104만건', label: '연간 무단 사용 추정' },
]

const problems = [
  {
    icon: ScanSearch,
    title: '발견이 어렵다',
    description: '수십 개 플랫폼에 흩어진 내 이미지를 하나하나 검색하기 불가능합니다.',
  },
  {
    icon: Scale,
    title: '증명이 어렵다',
    description: '원본 소유자임을 입증할 객관적 증거를 확보하기 어렵습니다.',
  },
  {
    icon: AlertTriangle,
    title: '대응이 느리다',
    description: '발견해도 신고 절차가 복잡하고, 피해가 누적되는 동안 대응이 늦어집니다.',
  },
]

const solutions = [
  {
    icon: Fingerprint,
    title: '보이지 않는 각인',
    description: '비가시 워터마크로 이미지에 소유권 정보를 삽입합니다. 육안으로는 보이지 않지만 언제든 추출할 수 있습니다.',
    tag: '비가시 워터마크',
  },
  {
    icon: RefreshCw,
    title: '자동 보호',
    description: '스토어를 연동하면 신규 상품 이미지를 자동으로 보호합니다. 수작업 없이 모든 이미지가 보호됩니다.',
    tag: '플랫폼 연동',
  },
  {
    icon: FileCheck,
    title: '증거 패키지',
    description: '도용 탐지 시 워터마크 검증, 유사도 분석, 사업자 정보를 포함한 증거 PDF를 자동 생성합니다.',
    tag: '원클릭 대응',
  },
]

const steps = [
  { num: '01', title: '스토어 연동', description: 'API Key로 스토어를 연결합니다', icon: Link2 },
  { num: '02', title: '상품 자동 스캔', description: '등록된 상품 이미지를 분석합니다', icon: ScanLine },
  { num: '03', title: '원클릭 일괄 보호', description: '모든 이미지에 워터마크를 삽입합니다', icon: ShieldPlus },
  { num: '04', title: '자동 교체 완료', description: '보호된 이미지로 자동 교체됩니다', icon: ShieldCheck },
]

const plans = [
  {
    name: '무료',
    price: '₩0',
    period: '/월',
    description: '서비스를 체험해보세요',
    features: ['월 10장 보호', '도용 탐지 3회', '스토어 1개 연동'],
    cta: '무료로 시작',
    popular: false,
  },
  {
    name: '스타터',
    price: '₩29,900',
    period: '/월',
    description: '소규모 셀러에게 딱 맞는 플랜',
    features: ['월 200장 보호', '도용 탐지 30회', '스토어 2개 연동', '증거 PDF 생성', '이메일 알림'],
    cta: '스타터 시작',
    popular: true,
  },
  {
    name: '프로',
    price: '₩69,900',
    period: '/월',
    description: '대규모 셀러를 위한 플랜',
    features: ['월 1,000장 보호', '도용 탐지 200회', '스토어 5개 연동', '증거 PDF 생성', '우선 지원', '자동 스캔'],
    cta: '프로 시작',
    popular: false,
  },
]
</script>

<template>
  <div>
    <!-- Hero Section -->
    <section class="relative overflow-hidden pb-20 pt-24 sm:pb-28 sm:pt-32">
      <div class="absolute inset-0 -z-10 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/8 via-background to-background" />
      <div class="mx-auto max-w-7xl px-6 text-center">
        <h1 class="mx-auto max-w-4xl text-4xl font-bold tracking-tight sm:text-5xl lg:text-6xl">
          내 상품 이미지,<br>
          <span class="bg-gradient-to-r from-cyan-500 to-cyan-300 bg-clip-text text-transparent">
            도용당하고 있습니다
          </span>
        </h1>

        <p class="mx-auto mt-6 max-w-2xl text-lg text-muted-foreground">
          보이지 않는 워터마크와 AI 유사도 분석으로 이미지 도용을 탐지하고, 증거를 자동 생성합니다.
        </p>

        <!-- Stats -->
        <div class="mx-auto mt-10 grid max-w-2xl grid-cols-3 gap-6">
          <div v-for="stat in stats" :key="stat.label" class="space-y-1">
            <p class="text-2xl font-bold text-primary sm:text-3xl font-mono">{{ stat.value }}</p>
            <p class="text-xs text-muted-foreground sm:text-sm">{{ stat.label }}</p>
          </div>
        </div>

        <!-- CTA Buttons -->
        <div class="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <NuxtLink to="/signup">
            <Button size="lg" class="gap-2 rounded-lg px-6 py-2.5 text-sm font-medium">
              내 스토어 무료 검사
              <ArrowRight class="h-4 w-4" />
            </Button>
          </NuxtLink>
          <NuxtLink to="/signup">
            <Button variant="outline" size="lg" class="rounded-lg px-6 py-2.5 text-sm font-medium">
              무료로 체험하기
            </Button>
          </NuxtLink>
        </div>

        <p class="mt-4 text-sm text-muted-foreground">
          신용카드 불필요 · 가입 즉시 사용 가능
        </p>
      </div>
    </section>

    <!-- Problem Section -->
    <section class="border-t py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-6">
        <div class="mx-auto max-w-2xl text-center">
          <p class="text-sm font-semibold text-primary">문제</p>
          <h2 class="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
            이미지 도용, 왜 방치되고 있을까요?
          </h2>
        </div>

        <div class="mx-auto mt-14 grid max-w-5xl gap-8 sm:grid-cols-3">
          <div v-for="problem in problems" :key="problem.title" class="text-center">
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-destructive/10">
              <component :is="problem.icon" class="h-7 w-7 text-destructive" />
            </div>
            <h3 class="mt-5 text-lg font-semibold">{{ problem.title }}</h3>
            <p class="mt-2 text-sm leading-relaxed text-muted-foreground">
              {{ problem.description }}
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- Solution Section -->
    <section class="border-t bg-muted/30 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-6">
        <div class="mx-auto max-w-2xl text-center">
          <p class="text-sm font-semibold text-primary">솔루션</p>
          <h2 class="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
            Ownpic이 해결합니다
          </h2>
        </div>

        <div class="mx-auto mt-14 grid max-w-5xl gap-8 sm:grid-cols-3">
          <Card v-for="sol in solutions" :key="sol.title" class="relative overflow-hidden border-0 bg-background shadow-sm transition-shadow hover:shadow-md">
            <CardHeader class="pb-3">
              <span class="mb-3 inline-flex w-fit rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
                {{ sol.tag }}
              </span>
              <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
                <component :is="sol.icon" class="h-6 w-6 text-primary" />
              </div>
              <CardTitle class="mt-4 text-lg">{{ sol.title }}</CardTitle>
            </CardHeader>
            <CardContent>
              <p class="text-sm leading-relaxed text-muted-foreground">
                {{ sol.description }}
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>

    <!-- Process Section -->
    <section id="features" class="border-t py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-6">
        <div class="mx-auto max-w-2xl text-center">
          <p class="text-sm font-semibold text-primary">작동 방식</p>
          <h2 class="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
            스토어 연동만 하면 끝
          </h2>
          <p class="mt-4 text-muted-foreground">
            복잡한 설정 없이 4단계로 모든 이미지를 보호합니다.
          </p>
        </div>

        <div class="mx-auto mt-14 grid max-w-4xl grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="step in steps" :key="step.num" class="relative rounded-2xl border bg-background p-6 text-center">
            <span class="text-xs font-bold text-primary">STEP {{ step.num }}</span>
            <div class="mx-auto mt-4 flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
              <component :is="step.icon" class="h-6 w-6 text-primary" />
            </div>
            <h3 class="mt-4 text-sm font-semibold">{{ step.title }}</h3>
            <p class="mt-1.5 text-xs text-muted-foreground">{{ step.description }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- AI 기본법 Section -->
    <section class="border-t bg-muted/30 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-6">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full border bg-background px-4 py-1.5 text-sm font-medium">
            <Scale class="h-4 w-4 text-primary" />
            법률 대응
          </span>
          <h2 class="mt-6 text-3xl font-bold tracking-tight sm:text-4xl">
            2026 AI 기본법 시행
          </h2>
          <p class="mt-4 text-muted-foreground">
            AI 생성물 표시 의무화에 따라, 원본 소유권 증명의 중요성이 더욱 높아졌습니다.
          </p>
        </div>

        <div class="mx-auto mt-12 grid max-w-4xl gap-6 sm:grid-cols-3">
          <div class="rounded-2xl border bg-background p-6 text-center">
            <p class="text-sm font-semibold">AI 생성물 표시 의무화</p>
            <p class="mt-2 text-xs text-muted-foreground">AI로 만든 콘텐츠에 대한 표시 의무가 법제화됩니다.</p>
          </div>
          <div class="rounded-2xl border bg-background p-6 text-center">
            <p class="text-sm font-semibold">원본 소유권 증명</p>
            <p class="mt-2 text-xs text-muted-foreground">내 이미지가 원본임을 기술적으로 증명해야 합니다.</p>
          </div>
          <div class="rounded-2xl border bg-background p-6 text-center">
            <p class="text-sm font-semibold">비가시 워터마크 솔루션</p>
            <p class="mt-2 text-xs text-muted-foreground">Ownpic의 워터마크가 법적 증거로 활용됩니다.</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Pricing Section -->
    <section id="pricing" class="border-t py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-6">
        <div class="mx-auto max-w-2xl text-center">
          <p class="text-sm font-semibold text-primary">요금제</p>
          <h2 class="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
            필요한 만큼만 사용하세요
          </h2>
        </div>

        <div class="mx-auto mt-14 grid max-w-5xl gap-6 sm:grid-cols-3">
          <Card
            v-for="plan in plans"
            :key="plan.name"
            class="relative overflow-hidden transition-shadow hover:shadow-md"
            :class="plan.popular ? 'border-primary shadow-md' : ''"
          >
            <div v-if="plan.popular" class="absolute right-4 top-4">
              <span class="rounded-full bg-primary px-3 py-1 text-xs font-medium text-primary-foreground">인기</span>
            </div>
            <CardHeader>
              <CardTitle class="text-lg">{{ plan.name }}</CardTitle>
              <CardDescription>{{ plan.description }}</CardDescription>
              <div class="mt-3">
                <span class="text-3xl font-bold font-mono">{{ plan.price }}</span>
                <span class="text-sm text-muted-foreground">{{ plan.period }}</span>
              </div>
            </CardHeader>
            <CardContent class="space-y-4">
              <NuxtLink to="/signup">
                <Button
                  class="w-full rounded-lg"
                  :variant="plan.popular ? 'default' : 'outline'"
                >
                  {{ plan.cta }}
                </Button>
              </NuxtLink>
              <ul class="space-y-2.5">
                <li v-for="feature in plan.features" :key="feature" class="flex items-center gap-2 text-sm text-muted-foreground">
                  <Check class="h-4 w-4 shrink-0 text-primary" />
                  {{ feature }}
                </li>
              </ul>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>

    <!-- Final CTA Section -->
    <section class="border-t bg-muted/30 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-6 text-center">
        <h2 class="text-3xl font-bold tracking-tight sm:text-4xl">
          내 상품 이미지, 지금 보호하세요
        </h2>
        <p class="mx-auto mt-4 max-w-xl text-muted-foreground">
          무료로 시작하고, 내 상품 이미지를 보호하세요.
        </p>
        <div class="mt-8">
          <NuxtLink to="/signup">
            <Button size="lg" class="gap-2 rounded-lg px-6 py-2.5 text-sm font-medium">
              무료로 시작하기
              <ArrowRight class="h-4 w-4" />
            </Button>
          </NuxtLink>
        </div>
        <p class="mt-4 text-sm text-muted-foreground">
          신용카드 불필요 · 가입 즉시 사용 가능
        </p>
      </div>
    </section>
  </div>
</template>
