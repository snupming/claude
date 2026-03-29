<script setup lang="ts">
import { Sun, Moon, Monitor, Menu, X } from 'lucide-vue-next'

const { isLoggedIn, logout } = useAuth()
const { theme, toggleTheme } = useTheme()
const mobileOpen = ref(false)
</script>

<template>
  <!-- 프로덕션 동일: nav, sticky, h-16, bg-background/85, backdrop-blur-xl, px-8 -->
  <nav class="sticky top-0 z-50 flex h-16 items-center justify-between border-b border-border bg-background/85 px-8 backdrop-blur-xl max-md:px-4">
    <!-- Left: Logo + Nav Links -->
    <div class="flex items-center gap-1">
      <NuxtLink to="/" class="mr-4 flex items-center">
        <span class="text-xl font-extrabold tracking-tight text-primary">
          Ownpic
        </span>
      </NuxtLink>

      <!-- Desktop Nav Links -->
      <div class="hidden items-center gap-0.5 md:flex">
        <NuxtLink
          to="/#features"
          class="rounded-xl px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          가이드
        </NuxtLink>
        <NuxtLink
          to="/pricing"
          class="rounded-xl px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          요금제
        </NuxtLink>
        <a
          href="https://open.kakao.com/o/swb05uki"
          target="_blank"
          rel="noopener noreferrer"
          class="rounded-xl px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          문의하기
        </a>
      </div>
    </div>

    <!-- Right: Theme toggle + Auth -->
    <div class="hidden items-center gap-2 md:flex">
      <!-- Theme toggle -->
      <button
        class="inline-flex h-9 w-9 items-center justify-center rounded-xl text-foreground transition-colors hover:bg-muted"
        @click="toggleTheme"
        :title="`테마: ${theme === 'system' ? '시스템' : theme === 'dark' ? '다크' : '라이트'}`"
      >
        <Sun v-if="theme === 'light'" class="h-[18px] w-[18px]" />
        <Moon v-else-if="theme === 'dark'" class="h-[18px] w-[18px]" />
        <Monitor v-else class="h-[18px] w-[18px]" />
      </button>

      <template v-if="isLoggedIn">
        <NuxtLink
          to="/dashboard"
          class="rounded-xl px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-muted"
        >
          대시보드
        </NuxtLink>
        <button
          class="h-8 rounded-xl bg-primary px-4 text-xs font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
          @click="logout"
        >
          로그아웃
        </button>
      </template>
      <template v-else>
        <NuxtLink
          to="/signup"
          class="rounded-xl px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-muted"
        >
          무료 체험
        </NuxtLink>
        <NuxtLink
          to="/login"
          class="flex h-8 items-center rounded-xl bg-primary px-4 text-xs font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
        >
          로그인
        </NuxtLink>
      </template>
    </div>

    <!-- Mobile menu button -->
    <button
      class="inline-flex items-center justify-center rounded-xl p-2 text-muted-foreground md:hidden"
      @click="mobileOpen = !mobileOpen"
    >
      <X v-if="mobileOpen" class="h-5 w-5" />
      <Menu v-else class="h-5 w-5" />
    </button>
  </nav>

  <!-- Mobile Nav Overlay -->
  <div v-if="mobileOpen" class="fixed inset-x-0 top-16 z-40 border-b bg-background md:hidden">
    <div class="space-y-1 px-4 py-4">
      <NuxtLink to="/#features" class="block rounded-xl px-3 py-2 text-sm text-muted-foreground hover:bg-muted hover:text-foreground" @click="mobileOpen = false">
        가이드
      </NuxtLink>
      <NuxtLink to="/pricing" class="block rounded-xl px-3 py-2 text-sm text-muted-foreground hover:bg-muted hover:text-foreground" @click="mobileOpen = false">
        요금제
      </NuxtLink>
      <a href="https://open.kakao.com/o/swb05uki" target="_blank" rel="noopener noreferrer" class="block rounded-xl px-3 py-2 text-sm text-muted-foreground hover:bg-muted hover:text-foreground" @click="mobileOpen = false">
        문의하기
      </a>

      <div class="my-2 border-t" />

      <template v-if="isLoggedIn">
        <NuxtLink to="/dashboard" class="block rounded-xl px-3 py-2 text-sm font-medium text-foreground" @click="mobileOpen = false">
          대시보드
        </NuxtLink>
        <button class="block w-full rounded-xl px-3 py-2 text-left text-sm text-muted-foreground hover:bg-muted" @click="logout(); mobileOpen = false">
          로그아웃
        </button>
      </template>
      <template v-else>
        <NuxtLink to="/signup" class="block rounded-xl px-3 py-2 text-sm font-medium text-foreground" @click="mobileOpen = false">
          무료 체험
        </NuxtLink>
        <NuxtLink to="/login" class="block rounded-xl bg-primary px-3 py-2 text-center text-sm font-medium text-primary-foreground" @click="mobileOpen = false">
          로그인
        </NuxtLink>
      </template>
    </div>
  </div>
</template>
