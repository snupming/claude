<script setup lang="ts">
import { Shield, Images, ScanLine, TrendingUp, Store, Plus, ExternalLink } from 'lucide-vue-next'
import { Skeleton } from '@/components/ui/skeleton'
import { extractErrorMessage } from '@/lib/utils'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

definePageMeta({
  layout: 'dashboard',
})

const { user } = useAuth()
const isLoading = ref(true)
const showStoreDialog = ref(false)

onMounted(() => {
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

interface Platform {
  id: string
  name: string
  description: string
  icon: string
  color: string
  available: boolean
}

const platforms: Platform[] = [
  {
    id: 'naver',
    name: '네이버 스마트스토어',
    description: '네이버 스마트스토어 상품 이미지를 자동 보호합니다',
    icon: 'N',
    color: 'bg-[#03C75A]',
    available: true,
  },
  {
    id: 'coupang',
    name: '쿠팡',
    description: '쿠팡 마켓플레이스 상품 이미지를 자동 보호합니다',
    icon: 'C',
    color: 'bg-[#E31937]',
    available: true,
  },
  {
    id: 'gmarket',
    name: 'G마켓',
    description: 'G마켓 셀러 이미지를 자동으로 모니터링합니다',
    icon: 'G',
    color: 'bg-[#00A650]',
    available: false,
  },
  {
    id: '11st',
    name: '11번가',
    description: '11번가 상품 이미지를 연동하여 보호합니다',
    icon: '11',
    color: 'bg-[#FF0000]',
    available: false,
  },
]

interface StoreConnection {
  id: number
  platform: string
  storeName: string | null
  status: string
  lastSyncedAt: string | null
  createdAt: string
}

const connectedStores = ref<StoreConnection[]>([])
const isConnecting = ref(false)

async function fetchStores() {
  try {
    const data = await $fetch<StoreConnection[]>('/api/stores')
    connectedStores.value = data.filter(s => s.status !== 'DISCONNECTED')
  } catch {
    // API 미연결 시 빈 상태
  }
}

async function connectStore(platformId: string) {
  isConnecting.value = true
  try {
    await $fetch('/api/stores/connect', {
      method: 'POST',
      body: { platform: platformId },
    })
    await fetchStores()
    showStoreDialog.value = false
  } catch (err: unknown) {
    // 이미 연동된 경우 등 — 백엔드가 409 등을 돌려주며, 조용히 무시한다.
    console.warn('[connectStore] failed:', extractErrorMessage(err) ?? err)
  } finally {
    isConnecting.value = false
  }
}

function isStoreConnected(platformId: string) {
  return connectedStores.value.some(s => s.platform === platformId.toUpperCase())
}

onMounted(fetchStores)
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
              <NuxtLink to="/protect">
                <Button size="sm">이미지 보호하기</Button>
              </NuxtLink>
              <NuxtLink to="/detect">
                <Button variant="outline" size="sm">도용 탐지하기</Button>
              </NuxtLink>
            </div>
          </template>
        </CardContent>
      </Card>

      <!-- Store Integration -->
      <Card>
        <CardHeader>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-500/10">
                <Store class="h-5 w-5 text-green-500" />
              </div>
              <div>
                <CardTitle>스토어 연동</CardTitle>
                <CardDescription>이커머스 스토어를 연동하여 자동으로 보호합니다</CardDescription>
              </div>
            </div>
            <Button
              v-if="!isLoading"
              size="sm"
              variant="outline"
              class="gap-1.5"
              @click="showStoreDialog = true"
            >
              <Plus class="h-4 w-4" />
              연동하기
            </Button>
            <Skeleton v-else class="h-9 w-24 rounded-md" />
          </div>
        </CardHeader>
        <CardContent>
          <template v-if="isLoading">
            <div class="space-y-3">
              <div v-for="i in 2" :key="i" class="flex items-center gap-4 rounded-lg border border-border p-4">
                <Skeleton class="h-10 w-10 rounded-lg" />
                <div class="flex-1 space-y-2">
                  <Skeleton class="h-4 w-32" />
                  <Skeleton class="h-3 w-48" />
                </div>
                <Skeleton class="h-6 w-16 rounded-full" />
              </div>
            </div>
          </template>
          <template v-else>
            <!-- 연동된 스토어 목록 -->
            <div v-if="connectedStores.length > 0" class="space-y-3">
              <div
                v-for="store in connectedStores"
                :key="store.id"
                class="flex items-center gap-4 rounded-lg border border-border p-4"
              >
                <div
                  class="flex h-10 w-10 items-center justify-center rounded-lg text-sm font-bold text-white"
                  :class="platforms.find(p => p.id === store.platform.toLowerCase())?.color ?? 'bg-muted'"
                >
                  {{ platforms.find(p => p.id === store.platform.toLowerCase())?.icon ?? '?' }}
                </div>
                <div class="flex-1">
                  <p class="text-sm font-medium">{{ platforms.find(p => p.id === store.platform.toLowerCase())?.name ?? store.platform }}</p>
                  <p class="text-xs text-muted-foreground">
                    {{ store.lastSyncedAt ? `마지막 동기화: ${new Date(store.lastSyncedAt).toLocaleDateString('ko-KR')}` : '연동됨' }}
                  </p>
                </div>
                <Badge
                  variant="secondary"
                  :class="store.status === 'CONNECTED' ? 'text-green-600' : store.status === 'SYNCING' ? 'text-primary' : 'text-muted-foreground'"
                >
                  {{ store.status === 'CONNECTED' ? '활성' : store.status === 'SYNCING' ? '동기화 중' : store.status }}
                </Badge>
              </div>
            </div>

            <!-- 빈 상태 -->
            <div v-else class="flex flex-col items-center py-8 text-center">
              <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-muted">
                <Store class="h-6 w-6 text-muted-foreground/50" />
              </div>
              <p class="mt-3 text-sm font-medium">연동된 스토어가 없습니다</p>
              <p class="mt-1 text-xs text-muted-foreground">
                스토어를 연동하면 상품 이미지가 자동으로 보호됩니다
              </p>
              <Button
                variant="outline"
                size="sm"
                class="mt-4 gap-1.5"
                @click="showStoreDialog = true"
              >
                <Plus class="h-4 w-4" />
                스토어 연동하기
              </Button>
            </div>
          </template>
        </CardContent>
      </Card>
    </div>
  </div>

  <!-- Platform Selection Dialog -->
  <Dialog v-model:open="showStoreDialog">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>스토어 연동</DialogTitle>
        <DialogDescription>연동할 이커머스 플랫폼을 선택하세요</DialogDescription>
      </DialogHeader>

      <div class="mt-2 space-y-2">
        <button
          v-for="platform in platforms"
          :key="platform.id"
          class="flex w-full items-center gap-4 rounded-lg border border-border p-4 text-left transition-colors"
          :class="[
            platform.available && !isStoreConnected(platform.id) && !isConnecting
              ? 'hover:border-primary/30 hover:bg-accent/30 cursor-pointer'
              : 'opacity-50 cursor-not-allowed',
          ]"
          :disabled="!platform.available || isStoreConnected(platform.id) || isConnecting"
          @click="platform.available && !isStoreConnected(platform.id) && connectStore(platform.id)"
        >
          <div
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-sm font-bold text-white"
            :class="platform.color"
          >
            {{ platform.icon }}
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <p class="text-sm font-medium">{{ platform.name }}</p>
              <Badge v-if="isStoreConnected(platform.id)" variant="secondary" class="text-xs">연동됨</Badge>
              <Badge v-else-if="!platform.available" variant="outline" class="text-xs">준비 중</Badge>
            </div>
            <p class="mt-0.5 text-xs text-muted-foreground">{{ platform.description }}</p>
          </div>
          <ExternalLink v-if="platform.available && !isStoreConnected(platform.id)" class="h-4 w-4 shrink-0 text-muted-foreground" />
        </button>
      </div>
    </DialogContent>
  </Dialog>
</template>
