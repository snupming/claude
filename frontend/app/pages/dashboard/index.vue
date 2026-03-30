<script setup lang="ts">
import { Shield, Images, ScanLine, TrendingUp } from 'lucide-vue-next'
import { Skeleton } from '@/components/ui/skeleton'

definePageMeta({
  layout: 'dashboard',
})

const { user } = useAuth()
const isLoading = ref(true)

onMounted(() => {
  // 유저 데이터 로딩 시뮬레이션 (추후 실제 API 호출로 대체)
  setTimeout(() => {
    isLoading.value = false
  }, 600)
})

const statCards = computed(() => [
  {
    label: '보호된 이미지',
    value: user.value?.imagesUsed ?? 0,
    total: user.value?.imageQuota ?? 0,
    icon: Images,
    color: 'text-primary',
    bg: 'bg-primary/10',
  },
  {
    label: '탐지 완료',
    value: 0,
    icon: ScanLine,
    color: 'text-green-500',
    bg: 'bg-green-500/10',
  },
  {
    label: '도용 의심',
    value: 0,
    icon: TrendingUp,
    color: 'text-warning',
    bg: 'bg-warning/10',
  },
])
</script>

<template>
  <div class="container mx-auto px-4 py-8 sm:px-6 lg:px-8">
    <div class="mx-auto max-w-4xl space-y-8">
      <!-- Header -->
      <div>
        <template v-if="isLoading">
          <Skeleton class="h-8 w-48" />
          <Skeleton class="mt-2 h-5 w-64" />
        </template>
        <template v-else>
          <h1 class="text-3xl font-bold tracking-tight">대시보드</h1>
          <p class="mt-1 text-muted-foreground">
            안녕하세요, {{ user?.name ?? user?.email }}님
          </p>
        </template>
      </div>

      <!-- Stat Cards -->
      <div class="grid gap-4 sm:grid-cols-3">
        <template v-if="isLoading">
          <Card v-for="i in 3" :key="i">
            <CardContent class="flex items-center gap-4 p-5">
              <Skeleton class="h-10 w-10 rounded-lg" />
              <div class="flex-1 space-y-2">
                <Skeleton class="h-4 w-20" />
                <Skeleton class="h-7 w-12" />
              </div>
            </CardContent>
          </Card>
        </template>
        <template v-else>
          <Card v-for="stat in statCards" :key="stat.label">
            <CardContent class="flex items-center gap-4 p-5">
              <div class="flex h-10 w-10 items-center justify-center rounded-lg" :class="stat.bg">
                <component :is="stat.icon" class="h-5 w-5" :class="stat.color" />
              </div>
              <div>
                <p class="text-sm text-muted-foreground">{{ stat.label }}</p>
                <p class="text-2xl font-bold">
                  {{ stat.value }}<span v-if="stat.total !== undefined" class="text-sm font-normal text-muted-foreground">/{{ stat.total }}</span>
                </p>
              </div>
            </CardContent>
          </Card>
        </template>
      </div>

      <!-- Quick Actions -->
      <Card>
        <CardHeader>
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <Shield class="h-5 w-5 text-primary" />
            </div>
            <div>
              <CardTitle>빠른 시작</CardTitle>
              <CardDescription>이미지를 보호하고 도용을 탐지하세요</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <template v-if="isLoading">
            <div class="space-y-3">
              <Skeleton class="h-4 w-full" />
              <Skeleton class="h-4 w-3/4" />
              <Skeleton class="mt-4 h-10 w-40" />
            </div>
          </template>
          <template v-else>
            <p class="text-sm text-muted-foreground">
              상품 이미지를 업로드하면 AI가 자동으로 비가시 워터마크를 삽입하고, 인터넷에서 도용 여부를 탐지합니다.
            </p>
            <div class="mt-4 flex gap-3">
              <NuxtLink to="/dashboard/protect">
                <Button size="sm">이미지 보호하기</Button>
              </NuxtLink>
              <NuxtLink to="/dashboard/detect">
                <Button variant="outline" size="sm">도용 탐지하기</Button>
              </NuxtLink>
            </div>
          </template>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
