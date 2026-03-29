<script setup lang="ts">
import { Store, Plus } from 'lucide-vue-next'

definePageMeta({
  layout: 'dashboard',
})
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight">스토어</h1>
      <p class="mt-1 text-sm text-muted-foreground">
        이커머스 스토어를 연동하여 상품 이미지를 자동으로 보호합니다
      </p>
    </div>

    <Card class="mt-8">
      <CardContent class="flex flex-col items-center py-16">
        <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
          <Store class="h-7 w-7 text-primary" />
        </div>
        <h3 class="mt-4 text-lg font-semibold">스토어를 연동하세요</h3>
        <p class="mt-1.5 text-center text-sm text-muted-foreground">
          쿠팡, 네이버 스마트스토어 등을 연동하면 자동으로 이미지를 보호합니다
        </p>
        <button class="mt-6 inline-flex h-10 items-center gap-2 rounded-lg bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 active:scale-[0.98]">
          <Plus class="h-4 w-4" />
          스토어 연동
        </button>
      </CardContent>
    </Card>
  </div>
</template>
