<script setup lang="ts">
import { ShieldCheck, Upload } from 'lucide-vue-next'
import { Skeleton } from '@/components/ui/skeleton'

definePageMeta({
  layout: 'dashboard',
})

const isLoading = ref(true)

onMounted(() => {
  setTimeout(() => {
    isLoading.value = false
  }, 500)
})
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <!-- Header -->
    <template v-if="isLoading">
      <Skeleton class="h-7 w-36" />
      <Skeleton class="mt-2 h-4 w-72" />
    </template>
    <template v-else>
      <h1 class="text-2xl font-bold tracking-tight">이미지 보호</h1>
      <p class="mt-1 text-sm text-muted-foreground">
        상품 이미지에 비가시 워터마크를 삽입하여 보호합니다
      </p>
    </template>

    <!-- Image List / Empty State -->
    <Card class="mt-8">
      <CardContent class="flex flex-col items-center py-16">
        <template v-if="isLoading">
          <Skeleton class="h-14 w-14 rounded-2xl" />
          <Skeleton class="mt-4 h-5 w-48" />
          <Skeleton class="mt-2 h-4 w-72" />
          <Skeleton class="mt-6 h-10 w-36 rounded-lg" />
        </template>
        <template v-else>
          <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
            <ShieldCheck class="h-7 w-7 text-primary" />
          </div>
          <h3 class="mt-4 text-lg font-semibold">이미지를 업로드하세요</h3>
          <p class="mt-1.5 text-center text-sm text-muted-foreground">
            상품 이미지를 업로드하면 AI가 자동으로 비가시 워터마크를 삽입합니다
          </p>
          <Button class="mt-6 gap-2">
            <Upload class="h-4 w-4" />
            이미지 업로드
          </Button>
        </template>
      </CardContent>
    </Card>
  </div>
</template>
