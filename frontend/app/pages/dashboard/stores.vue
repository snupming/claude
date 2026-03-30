<script setup lang="ts">
import { Store, Plus } from 'lucide-vue-next'
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
      <Skeleton class="h-7 w-20" />
      <Skeleton class="mt-2 h-4 w-72" />
    </template>
    <template v-else>
      <h1 class="text-2xl font-bold tracking-tight">스토어</h1>
      <p class="mt-1 text-sm text-muted-foreground">
        이커머스 스토어를 연동하여 상품 이미지를 자동으로 보호합니다
      </p>
    </template>

    <Card class="mt-8">
      <CardContent class="flex flex-col items-center py-16">
        <template v-if="isLoading">
          <Skeleton class="h-14 w-14 rounded-2xl" />
          <Skeleton class="mt-4 h-5 w-44" />
          <Skeleton class="mt-2 h-4 w-72" />
          <Skeleton class="mt-6 h-10 w-28 rounded-lg" />
        </template>
        <template v-else>
          <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10">
            <Store class="h-7 w-7 text-primary" />
          </div>
          <h3 class="mt-4 text-lg font-semibold">스토어를 연동하세요</h3>
          <p class="mt-1.5 text-center text-sm text-muted-foreground">
            쿠팡, 네이버 스마트스토어 등을 연동하면 자동으로 이미지를 보호합니다
          </p>
          <Button class="mt-6 gap-2">
            <Plus class="h-4 w-4" />
            스토어 연동
          </Button>
        </template>
      </CardContent>
    </Card>
  </div>
</template>
