<script setup lang="ts">
import { Shield, Search, FileCheck, ArrowRight } from 'lucide-vue-next'

const features = [
  {
    icon: Shield,
    title: '이미지 보호',
    description: '보이지 않는 워터마크를 삽입하여 소유권을 증명합니다. AI 기반 TrustMark 기술로 이미지 품질 저하 없이 보호합니다.',
  },
  {
    icon: Search,
    title: '도용 탐지',
    description: '네이버 쇼핑을 자동 스캔하여 내 이미지를 무단 사용하는 셀러를 찾아냅니다. 유사도 분석으로 변형된 이미지도 탐지합니다.',
  },
  {
    icon: FileCheck,
    title: '증거 생성',
    description: '도용 증거를 자동으로 수집하고 PDF 리포트를 생성합니다. 워터마크 검증과 사업자 정보까지 한 번에 확인합니다.',
  },
]
</script>

<template>
  <div>
    <!-- Hero Section -->
    <section class="relative overflow-hidden py-24 sm:py-32">
      <div class="absolute inset-0 -z-10 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/10 via-background to-background" />
      <div class="container mx-auto px-4 text-center sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl space-y-6">
          <div class="inline-flex items-center gap-2 rounded-full border bg-muted/50 px-4 py-1.5 text-sm text-muted-foreground">
            <Shield class="h-4 w-4 text-primary" />
            AI 기반 이미지 보호 서비스
          </div>

          <h1 class="text-4xl font-bold tracking-tight sm:text-5xl lg:text-6xl">
            내 상품 이미지,<br>
            <span class="text-primary">도용으로부터 지키세요</span>
          </h1>

          <p class="mx-auto max-w-2xl text-lg text-muted-foreground sm:text-xl">
            보이지 않는 워터마크와 AI 유사도 분석으로 이미지 도용을 탐지하고,
            증거를 자동 생성합니다. 온라인 셀러를 위한 이미지 보호 솔루션.
          </p>

          <div class="flex flex-col items-center justify-center gap-3 pt-4 sm:flex-row">
            <NuxtLink to="/signup">
              <Button size="lg" class="gap-2 text-base">
                무료로 시작하기
                <ArrowRight class="h-4 w-4" />
              </Button>
            </NuxtLink>
            <NuxtLink to="/#features">
              <Button variant="outline" size="lg" class="text-base">
                기능 살펴보기
              </Button>
            </NuxtLink>
          </div>

          <p class="text-sm text-muted-foreground">
            50장까지 무료 · 카드 등록 불필요
          </p>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section id="features" class="border-t py-24 sm:py-32">
      <div class="container mx-auto px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold tracking-tight sm:text-4xl">
            보호부터 증거까지, 한 곳에서
          </h2>
          <p class="mt-4 text-lg text-muted-foreground">
            이미지 등록 한 번으로 보호, 탐지, 증거 생성이 자동으로 이루어집니다.
          </p>
        </div>

        <div class="mx-auto mt-16 grid max-w-5xl gap-8 sm:grid-cols-2 lg:grid-cols-3">
          <Card v-for="feature in features" :key="feature.title" class="relative overflow-hidden transition-shadow hover:shadow-md">
            <CardHeader>
              <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10">
                <component :is="feature.icon" class="h-6 w-6 text-primary" />
              </div>
              <CardTitle class="mt-4 text-xl">{{ feature.title }}</CardTitle>
            </CardHeader>
            <CardContent>
              <p class="text-sm leading-relaxed text-muted-foreground">
                {{ feature.description }}
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="border-t py-24 sm:py-32">
      <div class="container mx-auto px-4 sm:px-6 lg:px-8">
        <Card class="mx-auto max-w-3xl bg-gradient-to-br from-primary/5 to-primary/10 text-center">
          <CardHeader class="space-y-4 pb-4">
            <CardTitle class="text-3xl font-bold tracking-tight">
              지금 바로 이미지를 보호하세요
            </CardTitle>
            <CardDescription class="text-base">
              가입 후 바로 50장까지 무료로 이미지를 보호할 수 있습니다.
              프로 플랜으로 업그레이드하면 자동 탐지까지 사용 가능합니다.
            </CardDescription>
          </CardHeader>
          <CardContent class="pb-8">
            <NuxtLink to="/signup">
              <Button size="lg" class="gap-2 text-base">
                무료로 시작하기
                <ArrowRight class="h-4 w-4" />
              </Button>
            </NuxtLink>
          </CardContent>
        </Card>
      </div>
    </section>
  </div>
</template>
