<script setup lang="ts">
import { Sun, Moon, Menu, X, User, LayoutDashboard, LogOut } from 'lucide-vue-next'

const { user, isLoggedIn, logout } = useAuth()
const { theme, toggleTheme } = useTheme()
const mobileOpen = ref(false)

/* 현재 적용된 테마(system일 경우 OS 감지 결과) */
const resolvedTheme = computed(() => {
  if (theme.value === 'system') {
    if (import.meta.client) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    }
    return 'light'
  }
  return theme.value
})

</script>

<template>
  <!-- 프로덕션 동일: nav, sticky, h-16, bg-background/85, backdrop-blur-xl, px-8 -->
  <nav class="sticky top-0 z-50 flex h-16 items-center justify-between border-b border-border bg-background/85 px-8 backdrop-blur-xl max-md:px-4">
    <!-- Left: Logo + Nav Links -->
    <div class="flex items-center gap-6">
      <NuxtLink to="/" class="shrink-0">
        <span class="bg-gradient-to-r from-primary to-cyan-300 bg-clip-text text-xl font-extrabold text-transparent">
          Ownpic
        </span>
      </NuxtLink>

      <!-- Desktop Nav Links — 프로덕션 동일 순서: 가이드, 블로그, 요금제, 문의하기 -->
      <div class="hidden items-center gap-1 md:flex">
        <NuxtLink
          to="/blog?category=guide"
          class="rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent/50 hover:text-foreground"
        >
          가이드
        </NuxtLink>
        <NuxtLink
          to="/blog"
          class="rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent/50 hover:text-foreground"
        >
          블로그
        </NuxtLink>
        <NuxtLink
          to="/pricing"
          class="rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent/50 hover:text-foreground"
        >
          요금제
        </NuxtLink>
        <a
          href="https://open.kakao.com/o/swb05uki"
          target="_blank"
          rel="noopener noreferrer"
          class="rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent/50 hover:text-foreground"
        >
          문의하기
        </a>
      </div>
    </div>

    <!-- Right: Theme + EN + Auth — 프로덕션 동일 -->
    <div class="flex items-center gap-1.5 sm:gap-2">
      <!-- Theme toggle — 프로덕션 동일: h-10 w-10, rounded-lg -->
      <button
        class="inline-flex h-10 w-10 items-center justify-center rounded-lg text-sm font-semibold text-foreground transition-all duration-200 hover:bg-secondary hover:text-secondary-foreground"
        title="테마 전환"
        :aria-label="`현재 ${resolvedTheme === 'dark' ? '다크' : '라이트'} 모드. 클릭하여 전환`"
        @click="toggleTheme"
      >
        <Moon v-if="resolvedTheme === 'light'" class="h-4 w-4" />
        <Sun v-else class="h-4 w-4" />
      </button>

      <!-- EN 버튼 — 프로덕션 동일: bg-secondary, h-8, px-2 sm:px-3 -->
      <button
        class="inline-flex h-8 items-center justify-center rounded-lg bg-secondary px-2 text-xs font-semibold text-secondary-foreground transition-all duration-200 hover:bg-secondary/80 sm:px-3"
      >
        EN
      </button>

      <!-- 로그인 상태: 알림 + 사용자 아바타 드롭다운 -->
      <template v-if="isLoggedIn">
        <!-- 알림 버튼 + 팝업 -->
        <NotificationPopover />

        <!-- 사용자 아바타 드롭다운 -->
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <button
              class="inline-flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary transition-colors hover:bg-primary/20"
            >
              <User class="h-4 w-4" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-48">
            <DropdownMenuLabel class="font-normal">
              <p class="text-sm font-medium">{{ user?.name ?? '사용자' }}</p>
              <p class="text-xs text-muted-foreground">{{ user?.email }}</p>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem as-child>
              <NuxtLink to="/dashboard" class="flex cursor-pointer items-center gap-2">
                <LayoutDashboard class="h-4 w-4" />
                대시보드
              </NuxtLink>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem class="cursor-pointer text-destructive focus:text-destructive" @click="logout">
              <LogOut class="mr-2 h-4 w-4" />
              로그아웃
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </template>

      <!-- 비로그인 상태 — 프로덕션 동일: 무료 체험 + 로그인 -->
      <template v-else>
        <NuxtLink
          to="/signup"
          class="hidden h-8 items-center justify-center rounded-lg px-3 text-xs font-semibold text-foreground transition-all duration-200 hover:bg-secondary hover:text-secondary-foreground sm:inline-flex"
        >
          무료 체험
        </NuxtLink>
        <NuxtLink
          to="/login"
          class="inline-flex h-8 items-center justify-center rounded-lg bg-primary px-2.5 text-xs font-semibold text-primary-foreground transition-all duration-200 hover:bg-primary/90 active:scale-[0.98] sm:px-4"
        >
          로그인
        </NuxtLink>
      </template>

      <!-- Mobile menu button -->
      <button
        class="inline-flex items-center justify-center rounded-lg p-2 text-muted-foreground md:hidden"
        @click="mobileOpen = !mobileOpen"
      >
        <X v-if="mobileOpen" class="h-5 w-5" />
        <Menu v-else class="h-5 w-5" />
      </button>
    </div>
  </nav>

  <!-- Mobile Nav Overlay -->
  <div v-if="mobileOpen" class="fixed inset-x-0 top-16 z-40 border-b bg-background md:hidden">
    <div class="space-y-1 px-4 py-4">
      <NuxtLink to="/blog?category=guide" class="block rounded-lg px-3 py-2 text-sm text-muted-foreground hover:bg-accent/50 hover:text-foreground" @click="mobileOpen = false">
        가이드
      </NuxtLink>
      <NuxtLink to="/blog" class="block rounded-lg px-3 py-2 text-sm text-muted-foreground hover:bg-accent/50 hover:text-foreground" @click="mobileOpen = false">
        블로그
      </NuxtLink>
      <NuxtLink to="/pricing" class="block rounded-lg px-3 py-2 text-sm text-muted-foreground hover:bg-accent/50 hover:text-foreground" @click="mobileOpen = false">
        요금제
      </NuxtLink>
      <a href="https://open.kakao.com/o/swb05uki" target="_blank" rel="noopener noreferrer" class="block rounded-lg px-3 py-2 text-sm text-muted-foreground hover:bg-accent/50 hover:text-foreground" @click="mobileOpen = false">
        문의하기
      </a>

      <template v-if="isLoggedIn">
        <div class="my-2 border-t" />
        <NuxtLink to="/dashboard" class="block rounded-lg px-3 py-2 text-sm font-medium text-foreground" @click="mobileOpen = false">
          대시보드
        </NuxtLink>
        <button class="block w-full rounded-lg px-3 py-2 text-left text-sm text-destructive hover:bg-muted" @click="logout(); mobileOpen = false">
          로그아웃
        </button>
      </template>
      <template v-else>
        <div class="my-2 border-t" />
        <NuxtLink to="/signup" class="block rounded-lg px-3 py-2 text-sm font-medium text-foreground" @click="mobileOpen = false">
          무료 체험
        </NuxtLink>
        <NuxtLink to="/login" class="block rounded-lg bg-primary px-3 py-2 text-center text-sm font-medium text-primary-foreground" @click="mobileOpen = false">
          로그인
        </NuxtLink>
      </template>
    </div>
  </div>
</template>
