<script setup lang="ts">
import { Bell, Check, Image, AlertTriangle, Store } from 'lucide-vue-next'

/* 알림 타입 */
interface Notification {
  id: string
  type: 'detect' | 'protect' | 'store' | 'system'
  title: string
  message: string
  read: boolean
  createdAt: string
}

/* 임시 목업 데이터 (추후 API 연동) */
const notifications = ref<Notification[]>([
  {
    id: '1',
    type: 'detect',
    title: '도용 의심 발견',
    message: '등록된 이미지와 유사한 이미지가 발견되었습니다.',
    read: false,
    createdAt: '2분 전',
  },
  {
    id: '2',
    type: 'protect',
    title: '워터마크 적용 완료',
    message: '5장의 이미지에 워터마크가 적용되었습니다.',
    read: false,
    createdAt: '1시간 전',
  },
  {
    id: '3',
    type: 'store',
    title: '스토어 연동 완료',
    message: '쿠팡 스토어가 성공적으로 연동되었습니다.',
    read: true,
    createdAt: '어제',
  },
])

const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)

const open = ref(false)

function markAllRead() {
  notifications.value.forEach((n) => (n.read = true))
}

function getIcon(type: Notification['type']) {
  switch (type) {
    case 'detect':
      return AlertTriangle
    case 'protect':
      return Image
    case 'store':
      return Store
    default:
      return Bell
  }
}

function getIconColor(type: Notification['type']) {
  switch (type) {
    case 'detect':
      return 'text-warning'
    case 'protect':
      return 'text-primary'
    case 'store':
      return 'text-green-500'
    default:
      return 'text-muted-foreground'
  }
}
</script>

<template>
  <Popover v-model:open="open">
    <PopoverTrigger as-child>
      <button
        class="relative inline-flex h-9 w-9 items-center justify-center rounded-lg text-foreground transition-all duration-200 hover:bg-secondary hover:text-secondary-foreground"
      >
        <Bell class="h-4 w-4" />
        <!-- 읽지 않은 알림 뱃지 -->
        <span
          v-if="unreadCount > 0"
          class="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-destructive text-[10px] font-bold text-destructive-foreground"
        >
          {{ unreadCount > 9 ? '9+' : unreadCount }}
        </span>
      </button>
    </PopoverTrigger>

    <PopoverContent align="end" :side-offset="8" class="w-80 p-0">
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-border px-4 py-3">
        <h4 class="text-sm font-semibold">알림</h4>
        <button
          v-if="unreadCount > 0"
          class="flex items-center gap-1 text-xs text-primary hover:underline"
          @click="markAllRead"
        >
          <Check class="h-3 w-3" />
          모두 읽음
        </button>
      </div>

      <!-- 알림 목록 -->
      <ScrollArea class="max-h-72">
        <div v-if="notifications.length === 0" class="px-4 py-8 text-center text-sm text-muted-foreground">
          새로운 알림이 없습니다
        </div>
        <div v-else>
          <div
            v-for="item in notifications"
            :key="item.id"
            class="flex gap-3 border-b border-border/50 px-4 py-3 transition-colors last:border-b-0 hover:bg-accent/30"
            :class="!item.read ? 'bg-primary/5' : ''"
          >
            <div
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted"
            >
              <component :is="getIcon(item.type)" class="h-4 w-4" :class="getIconColor(item.type)" />
            </div>
            <div class="min-w-0 flex-1">
              <p class="text-sm font-medium leading-tight">{{ item.title }}</p>
              <p class="mt-0.5 text-xs text-muted-foreground line-clamp-2">{{ item.message }}</p>
              <p class="mt-1 text-[11px] text-muted-foreground/70">{{ item.createdAt }}</p>
            </div>
            <!-- 읽지 않은 표시 -->
            <div v-if="!item.read" class="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-primary" />
          </div>
        </div>
      </ScrollArea>

      <!-- Footer: 전체보기 -->
      <div class="border-t border-border">
        <NuxtLink
          to="/notifications"
          class="block py-2.5 text-center text-xs font-medium text-primary transition-colors hover:bg-accent/30"
          @click="open = false"
        >
          전체보기
        </NuxtLink>
      </div>
    </PopoverContent>
  </Popover>
</template>
