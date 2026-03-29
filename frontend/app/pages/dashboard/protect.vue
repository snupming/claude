<script setup lang="ts">
import { ShieldCheck, Upload } from 'lucide-vue-next'

definePageMeta({
  layout: 'dashboard',
})
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight">이미지 보호</h1>
      <p class="mt-1 text-sm text-muted-foreground">
        상품 이미지에 비가시 워터마크를 삽입하여 보호합니다
      </p>
    </div>

    <Card class="mt-8">
      <CardContent class="flex flex-col items-center py-16">
        <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
          <ShieldCheck class="h-7 w-7 text-primary" />
        </div>
        <h3 class="mt-4 text-lg font-semibold">이미지를 업로드하세요</h3>
        <p class="mt-1.5 text-center text-sm text-muted-foreground">
          상품 이미지를 업로드하면 AI가 자동으로 비가시 워터마크를 삽입합니다
        </p>
        <button class="mt-6 inline-flex h-10 items-center gap-2 rounded-lg bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 active:scale-[0.98]">
          <Upload class="h-4 w-4" />
          이미지 업로드
        </button>
      </CardContent>
    </Card>
  </div>
</template>
