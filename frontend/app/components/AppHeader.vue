<script setup lang="ts">
import { Sun, Moon, Monitor, Menu, X } from 'lucide-vue-next'

const { isLoggedIn, logout } = useAuth()
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

    <!-- Right: Theme + EN + Auth — 프로덕션 동일 gap-1.5 sm:gap-2 -->
    <div class="hidden items-center gap-1.5 sm:gap-2 md:flex">
      <!-- Theme toggle — 프로덕션 동일: h-10 w-10, rounded-lg, 16x16 아이콘 -->
      <button
        class="inline-flex h-10 w-10 items-center justify-center rounded-lg text-sm font-semibold text-foreground transition-all duration-200 hover:bg-secondary hover:text-secondary-foreground"
        title="테마 전환"
        :aria-label="`현재 ${resolvedTheme === 'dark' ? '다크' : '라이트'} 모드. 클릭하여 전환`"
        @click="toggleTheme"
      >
        <Moon v-if="resolvedTheme === 'light'" class="h-4 w-4" />
        <Sun v-else class="h-4 w-4" />
      </button>

      <!-- EN 버튼 — 프로덕션 동일 -->
      <button
        class="inline-flex items-center justify-center rounded-lg px-2.5 py-1.5 text-xs font-semibold text-foreground transition-all duration-200 hover:bg-secondary hover:text-secondary-foreground"
      >
        EN
      </button>

      <template v-if="isLoggedIn">
        <NuxtLink
          to="/dashboard"
          class="rounded-lg px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-secondary hover:text-secondary-foreground"
        >
          대시보드
        </NuxtLink>
        <button
          class="h-8 rounded-lg bg-primary px-4 text-xs font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
          @click="logout"
        >
          로그아웃
        </button>
      </template>
      <template v-else>
        <NuxtLink
          to="/signup"
          class="rounded-lg px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-secondary hover:text-secondary-foreground"
        >
          무료 체험
        </NuxtLink>
        <NuxtLink
          to="/login"
          class="flex h-8 items-center rounded-lg bg-primary px-4 text-xs font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
        >
          로그인
        </NuxtLink>
      </template>
    </div>

    <!-- Mobile menu button -->
    <button
      class="inline-flex items-center justify-center rounded-lg p-2 text-muted-foreground md:hidden"
      @click="mobileOpen = !mobileOpen"
    >
      <X v-if="mobileOpen" class="h-5 w-5" />
      <Menu v-else class="h-5 w-5" />
    </button>
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

      <div class="my-2 border-t" />

      <template v-if="isLoggedIn">
        <NuxtLink to="/dashboard" class="block rounded-lg px-3 py-2 text-sm font-medium text-foreground" @click="mobileOpen = false">
          대시보드
        </NuxtLink>
        <button class="block w-full rounded-lg px-3 py-2 text-left text-sm text-muted-foreground hover:bg-muted" @click="logout(); mobileOpen = false">
          로그아웃
        </button>
      </template>
      <template v-else>
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
