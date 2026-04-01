<script setup lang="ts">
import { Bell, Check, Image, AlertTriangle, Store, Trash2 } from 'lucide-vue-next'
import { Skeleton } from '~/components/ui/skeleton'

definePageMeta({
  layout: 'dashboard',
})

interface Notification {
  id: string
  type: 'detect' | 'protect' | 'store' | 'system'
  title: string
  message: string
  read: boolean
  createdAt: string
}

const isLoading = ref(true)

/* 임시 목업 데이터 (추후 API 연동) */
const notifications = ref<Notification[]>([
  {
    id: '1',
    type: 'detect',
    title: '도용 의심 발견',
    message: '등록된 이미지 "product-001.jpg"와 유사한 이미지가 외부 사이트에서 발견되었습니다. 유사도 92%로 도용 가능성이 높습니다.',
    read: false,
    createdAt: '2분 전',
  },
  {
    id: '2',
    type: 'protect',
    title: '워터마크 적용 완료',
    message: '5장의 이미지에 비가시 워터마크가 성공적으로 적용되었습니다.',
    read: false,
    createdAt: '1시간 전',
  },
  {
    id: '3',
    type: 'store',
    title: '쿠팡 스토어 연동 완료',
    message: '쿠팡 스토어가 성공적으로 연동되었습니다. 자동 보호가 시작됩니다.',
    read: true,
    createdAt: '어제',
  },
  {
    id: '4',
    type: 'detect',
    title: '도용 탐지 결과',
    message: '주간 탐지 스캔이 완료되었습니다. 새로운 도용 의심 건은 없습니다.',
    read: true,
    createdAt: '3일 전',
  },
  {
    id: '5',
    type: 'system',
    title: '서비스 업데이트',
    message: 'Ownpic v2.1 업데이트가 적용되었습니다. 새로운 AI 탐지 모델이 포함되어 정확도가 향상되었습니다.',
    read: true,
    createdAt: '1주 전',
  },
])

onMounted(() => {
  setTimeout(() => {
    isLoading.value = false
  }, 500)
})

const filter = ref<'all' | 'unread'>('all')

const filteredNotifications = computed(() => {
  if (filter.value === 'unread') return notifications.value.filter((n) => !n.read)
  return notifications.value
})

const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)

function markAllRead() {
  notifications.value.forEach((n) => (n.read = true))
}

function markRead(id: string) {
  const n = notifications.value.find((n) => n.id === id)
  if (n) n.read = true
}

function removeNotification(id: string) {
  notifications.value = notifications.value.filter((n) => n.id !== id)
}

function getIcon(type: Notification['type']) {
  switch (type) {
    case 'detect': return AlertTriangle
    case 'protect': return Image
    case 'store': return Store
    default: return Bell
  }
}

function getIconColor(type: Notification['type']) {
  switch (type) {
    case 'detect': return 'text-warning'
    case 'protect': return 'text-primary'
    case 'store': return 'text-green-500'
    default: return 'text-muted-foreground'
  }
}
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <template v-if="isLoading">
          <Skeleton class="h-7 w-28" />
          <Skeleton class="mt-2 h-4 w-40" />
        </template>
        <template v-else>
          <h1 class="text-2xl font-bold tracking-tight">알림 관리</h1>
          <p class="mt-1 text-sm text-muted-foreground">
            읽지 않은 알림 {{ unreadCount }}개
          </p>
        </template>
      </div>
      <template v-if="!isLoading">
        <button
          v-if="unreadCount > 0"
          class="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium transition-colors hover:bg-accent/50"
          @click="markAllRead"
        >
          <Check class="h-3.5 w-3.5" />
          모두 읽음 처리
        </button>
      </template>
      <Skeleton v-else class="h-8 w-28 rounded-lg" />
    </div>

    <!-- Filter tabs -->
    <template v-if="isLoading">
      <div class="mt-6 flex gap-4 border-b border-border pb-2">
        <Skeleton class="h-5 w-12" />
        <Skeleton class="h-5 w-20" />
      </div>
    </template>
    <template v-else>
      <div class="mt-6 flex gap-1 border-b border-border">
        <button
          class="border-b-2 px-4 py-2 text-sm font-medium transition-colors"
          :class="filter === 'all' ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="filter = 'all'"
        >
          전체
        </button>
        <button
          class="border-b-2 px-4 py-2 text-sm font-medium transition-colors"
          :class="filter === 'unread' ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          @click="filter = 'unread'"
        >
          읽지 않음
          <Badge v-if="unreadCount > 0" variant="secondary" class="ml-1.5">{{ unreadCount }}</Badge>
        </button>
      </div>
    </template>

    <!-- 알림 리스트 -->
    <div class="mt-4 space-y-2">
      <!-- Skeleton 로딩 -->
      <template v-if="isLoading">
        <div
          v-for="i in 4"
          :key="i"
          class="flex gap-4 rounded-xl border border-border p-4"
        >
          <Skeleton class="h-10 w-10 shrink-0 rounded-lg" />
          <div class="min-w-0 flex-1 space-y-2">
            <div class="flex items-center justify-between">
              <Skeleton class="h-4 w-32" />
              <Skeleton class="h-3 w-16" />
            </div>
            <Skeleton class="h-4 w-full" />
            <Skeleton class="h-4 w-2/3" />
          </div>
        </div>
      </template>

      <!-- 실제 알림 -->
      <template v-else>
        <div
          v-for="item in filteredNotifications"
          :key="item.id"
          class="flex gap-4 rounded-xl border border-border p-4 transition-colors"
          :class="!item.read ? 'border-primary/20 bg-primary/5' : 'hover:bg-accent/30'"
          @click="markRead(item.id)"
        >
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-muted">
            <component :is="getIcon(item.type)" class="h-5 w-5" :class="getIconColor(item.type)" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-start justify-between gap-2">
              <p class="text-sm font-semibold">{{ item.title }}</p>
              <div class="flex shrink-0 items-center gap-2">
                <span class="text-xs text-muted-foreground">{{ item.createdAt }}</span>
                <button
                  class="rounded p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-destructive"
                  title="삭제"
                  @click.stop="removeNotification(item.id)"
                >
                  <Trash2 class="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
            <p class="mt-1 text-sm text-muted-foreground">{{ item.message }}</p>
          </div>
          <!-- 읽지 않은 표시 -->
          <div v-if="!item.read" class="mt-2 h-2.5 w-2.5 shrink-0 rounded-full bg-primary" />
        </div>

        <!-- 빈 상태 -->
        <div
          v-if="filteredNotifications.length === 0"
          class="py-16 text-center"
        >
          <Bell class="mx-auto h-10 w-10 text-muted-foreground/40" />
          <p class="mt-3 text-sm text-muted-foreground">
            {{ filter === 'unread' ? '읽지 않은 알림이 없습니다' : '알림이 없습니다' }}
          </p>
        </div>
      </template>
    </div>
  </div>
</template>
