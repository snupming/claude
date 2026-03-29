<script setup lang="ts">
import { Search, ScanLine } from 'lucide-vue-next'

definePageMeta({
  layout: 'dashboard',
})
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight">도용 탐지</h1>
      <p class="mt-1 text-sm text-muted-foreground">
        인터넷에서 내 상품 이미지의 도용 여부를 탐지합니다
      </p>
    </div>

    <Card class="mt-8">
      <CardContent class="flex flex-col items-center py-16">
        <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
          <ScanLine class="h-7 w-7 text-primary" />
        </div>
        <h3 class="mt-4 text-lg font-semibold">도용 탐지를 시작하세요</h3>
        <p class="mt-1.5 text-center text-sm text-muted-foreground">
          보호된 이미지를 기반으로 인터넷 전체를 스캔하여 도용을 찾아냅니다
        </p>
        <button class="mt-6 inline-flex h-10 items-center gap-2 rounded-lg bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 active:scale-[0.98]">
          <Search class="h-4 w-4" />
          탐지 시작
        </button>
      </CardContent>
    </Card>
  </div>
</template>
