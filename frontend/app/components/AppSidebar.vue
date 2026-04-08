<script setup lang="ts">
import {
  LayoutDashboard,
  ShieldCheck,
  Search,
  CreditCard,
  ChevronLeft,
  ChevronRight,
} from 'lucide-vue-next'
import { Skeleton } from '@/components/ui/skeleton'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const route = useRoute()
const { user } = useAuth()

/* 메인 네비게이션 */
const mainNav = [
  { label: '대시보드', icon: LayoutDashboard, to: '/dashboard' },
  { label: '이미지 보호', icon: ShieldCheck, to: '/protect' },
  { label: '도용 탐지', icon: Search, to: '/detect' },
]

/* 현재 라우트와 매칭 */
function isActive(to: string) {
  if (to === '/dashboard') return route.path === '/dashboard'
  return route.path.startsWith(to)
}

/* 사용량 계산 */
const usagePercent = computed(() => {
  if (!user.value?.imageQuota) return 0
  return Math.min(100, Math.round(((user.value.imagesUsed ?? 0) / user.value.imageQuota) * 100))
})

const tierLabel = computed(() => {
  const role = user.value?.role ?? 'free'
  const map: Record<string, string> = { free: '무료', starter: '스타터', pro: '프로' }
  return map[role] ?? role
})
</script>

<template>
  <!-- ========== Desktop Sidebar (md+) ========== -->
  <aside
    class="fixed left-0 top-16 z-30 hidden h-[calc(100vh-4rem)] flex-col border-r border-border bg-background transition-all duration-200 md:flex"
    :class="collapsed ? 'w-16' : 'w-60'"
  >
    <!-- Nav -->
    <nav class="flex-1 space-y-1 px-3 py-4">
      <NuxtLink
        v-for="item in mainNav"
        :key="item.to"
        :to="item.to"
        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors"
        :class="[
          isActive(item.to)
            ? 'bg-primary/10 text-primary'
            : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground',
          collapsed ? 'justify-center px-0' : '',
        ]"
      >
        <component :is="item.icon" class="h-5 w-5 shrink-0" />
        <span v-if="!collapsed">{{ item.label }}</span>
      </NuxtLink>
    </nav>

    <!-- Bottom: 요금제 + 사용량 -->
    <div class="border-t border-border px-3 py-4">
      <NuxtLink
        to="/pricing"
        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent/50 hover:text-foreground"
        :class="collapsed ? 'justify-center px-0' : ''"
      >
        <CreditCard class="h-5 w-5 shrink-0" />
        <span v-if="!collapsed">요금제</span>
      </NuxtLink>

      <!-- 사용량 프로그레스바 -->
      <div v-if="!collapsed" class="mt-3 px-3">
        <template v-if="!user">
          <Skeleton class="h-3 w-full" />
          <Skeleton class="mt-2 h-1.5 w-full rounded-full" />
        </template>
        <template v-else>
          <div class="flex items-center justify-between text-xs text-muted-foreground">
            <span>{{ tierLabel }} 플랜</span>
            <span>{{ user?.imagesUsed ?? 0 }}/{{ user?.imageQuota ?? 0 }}</span>
          </div>
          <Progress :model-value="usagePercent" class="mt-1.5 h-1.5" />
        </template>
      </div>
    </div>

    <!-- Collapse toggle -->
    <button
      class="flex h-10 items-center justify-center border-t border-border text-muted-foreground transition-colors hover:bg-accent/50 hover:text-foreground"
      @click="emit('toggle')"
    >
      <ChevronLeft v-if="!collapsed" class="h-4 w-4" />
      <ChevronRight v-else class="h-4 w-4" />
    </button>
  </aside>

  <!-- ========== Mobile Bottom Bar (<md) ========== -->
  <nav class="fixed inset-x-0 bottom-0 z-30 flex h-16 items-center justify-around border-t border-border bg-background md:hidden">
    <NuxtLink
      v-for="item in mainNav"
      :key="item.to"
      :to="item.to"
      class="flex flex-1 flex-col items-center justify-center gap-1 py-2 text-xs transition-colors"
      :class="isActive(item.to) ? 'text-primary' : 'text-muted-foreground'"
    >
      <component :is="item.icon" class="h-5 w-5" />
      <span>{{ item.label }}</span>
    </NuxtLink>
  </nav>
</template>
