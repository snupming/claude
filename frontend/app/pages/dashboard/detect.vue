<script setup lang="ts">
import { Search, ScanLine } from 'lucide-vue-next'
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
      <Skeleton class="h-7 w-28" />
      <Skeleton class="mt-2 h-4 w-72" />
    </template>
    <template v-else>
      <h1 class="text-2xl font-bold tracking-tight">도용 탐지</h1>
      <p class="mt-1 text-sm text-muted-foreground">
        인터넷에서 내 상품 이미지의 도용 여부를 탐지합니다
      </p>
    </template>

    <Card class="mt-8">
      <CardContent class="flex flex-col items-center py-16">
        <template v-if="isLoading">
          <Skeleton class="h-14 w-14 rounded-2xl" />
          <Skeleton class="mt-4 h-5 w-48" />
          <Skeleton class="mt-2 h-4 w-72" />
          <Skeleton class="mt-6 h-10 w-28 rounded-lg" />
        </template>
        <template v-else>
          <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
            <ScanLine class="h-7 w-7 text-primary" />
          </div>
          <h3 class="mt-4 text-lg font-semibold">도용 탐지를 시작하세요</h3>
          <p class="mt-1.5 text-center text-sm text-muted-foreground">
            보호된 이미지를 기반으로 인터넷 전체를 스캔하여 도용을 찾아냅니다
          </p>
          <Button class="mt-6 gap-2">
            <Search class="h-4 w-4" />
            탐지 시작
          </Button>
        </template>
      </CardContent>
    </Card>
  </div>
</template>
